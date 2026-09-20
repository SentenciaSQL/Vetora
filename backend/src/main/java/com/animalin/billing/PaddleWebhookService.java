package com.animalin.billing;

import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.billing.paddle.PaddleSignatureException;
import com.animalin.billing.paddle.PaddleSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class PaddleWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaddleWebhookService.class);

    private final PaddleProperties properties;
    private final PaddleSignatureVerifier signatureVerifier;
    private final BillingEventRepository billingEventRepository;
    private final SubscriptionSyncService syncService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate independentTransaction;

    public PaddleWebhookService(PaddleProperties properties,
                                PaddleSignatureVerifier signatureVerifier,
                                BillingEventRepository billingEventRepository,
                                SubscriptionSyncService syncService,
                                ObjectMapper objectMapper,
                                Clock clock,
                                PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.signatureVerifier = signatureVerifier;
        this.billingEventRepository = billingEventRepository;
        this.syncService = syncService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public void ingest(String signature, String rawBody) {
        signatureVerifier.verify(
                signature,
                rawBody,
                properties.webhookSecret(),
                Duration.ofSeconds(properties.webhookToleranceSeconds()),
                clock.instant()
        );
        try {
            transactionTemplate.executeWithoutResult(status -> processVerified(rawBody));
        } catch (RuntimeException ex) {
            independentTransaction.executeWithoutResult(status -> markFailed(rawBody, ex));
            throw ex;
        }
    }

    public void processVerified(String rawBody) {
        PaddleDtos.Notification notification = parse(rawBody);
        if (!StringUtils.hasText(notification.eventId())) {
            throw new PaddleSignatureException("Webhook payload is missing event_id");
        }
        if (billingEventRepository.existsByPaddleEventIdAndProcessingStatus(
                notification.eventId(), SubscriptionStatuses.EVENT_PROCESSED)) {
            log.info("Duplicate Paddle event {}", notification.eventId());
            return;
        }
        BillingEvent event = billingEventRepository.findByPaddleEventId(notification.eventId())
                .orElseGet(() -> persistNew(notification, rawBody));
        if (event.isProcessed()) {
            return;
        }
        dispatch(notification);
        event.setProcessingStatus(SubscriptionStatuses.EVENT_PROCESSED);
        event.setProcessedAt(clock.instant());
        event.setErrorMessage(null);
        billingEventRepository.save(event);
        log.info("Processed Paddle event type={} id={}", notification.eventType(), notification.eventId());
    }

    private BillingEvent persistNew(PaddleDtos.Notification notification, String rawBody) {
        BillingEvent event = new BillingEvent();
        event.setPaddleEventId(notification.eventId());
        event.setEventType(notification.eventType());
        event.setOccurredAt(notification.occurredAt());
        event.setReceivedAt(clock.instant());
        event.setProcessingStatus(SubscriptionStatuses.EVENT_RECEIVED);
        event.setPayload(rawBody);
        try {
            return billingEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            return billingEventRepository.findByPaddleEventId(notification.eventId())
                    .orElseThrow(() -> ex);
        }
    }

    private void dispatch(PaddleDtos.Notification notification) {
        String type = notification.eventType() == null ? "" : notification.eventType().toLowerCase(Locale.ROOT);
        JsonNode data = notification.data();
        switch (type) {
            case "customer.created", "customer.updated" ->
                    syncService.applyCustomer(objectMapper.convertValue(data, PaddleDtos.Customer.class));
            case "subscription.created", "subscription.updated", "subscription.activated",
                 "subscription.canceled", "subscription.past_due", "subscription.trialing",
                 "subscription.paused", "subscription.resumed" ->
                    syncService.applySubscription(objectMapper.convertValue(data, PaddleDtos.Subscription.class));
            case "transaction.completed", "transaction.payment_failed", "transaction.past_due",
                 "transaction.updated", "transaction.paid", "transaction.billed" ->
                    syncService.applyTransaction(objectMapper.convertValue(data, PaddleDtos.Transaction.class));
            default -> log.info("Ignoring unhandled Paddle event type {}", type);
        }
    }

    private PaddleDtos.Notification parse(String rawBody) {
        try {
            PaddleDtos.Notification notification = objectMapper.readValue(rawBody, PaddleDtos.Notification.class);
            if (notification == null) {
                throw new PaddleSignatureException("Empty webhook payload");
            }
            return notification;
        } catch (PaddleSignatureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaddleSignatureException("Malformed Paddle webhook payload");
        }
    }

    public void markFailed(String rawBody, Exception error) {
        try {
            PaddleDtos.Notification notification = objectMapper.readValue(rawBody, PaddleDtos.Notification.class);
            if (notification == null || !StringUtils.hasText(notification.eventId())) {
                return;
            }
            BillingEvent event = billingEventRepository.findByPaddleEventId(notification.eventId()).orElseGet(() -> {
                BillingEvent created = new BillingEvent();
                created.setPaddleEventId(notification.eventId());
                created.setEventType(notification.eventType());
                created.setOccurredAt(notification.occurredAt());
                created.setReceivedAt(clock.instant());
                created.setPayload(rawBody);
                return created;
            });
            if (event.isProcessed()) {
                return;
            }
            event.setProcessingStatus(SubscriptionStatuses.EVENT_FAILED);
            event.setErrorMessage(sanitize(error.getMessage()));
            event.setPayload(rawBody);
            billingEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("Unable to persist webhook failure audit: {}", ex.getClass().getSimpleName());
        }
    }

    private String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "Processing failed";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("apikey") || lower.contains("api-key") || lower.contains("secret") || lower.contains("bearer")) {
            return "Processing failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
