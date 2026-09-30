package com.animalin.billing.lemonsqueezy;

import com.animalin.billing.BillingEvent;
import com.animalin.billing.BillingEventRepository;
import com.animalin.billing.SubscriptionStatuses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class LemonSqueezyWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LemonSqueezyWebhookService.class);

    static final Set<String> SUPPORTED_EVENTS = Set.of(
            "order_created",
            "order_refunded",
            "subscription_created",
            "subscription_updated",
            "subscription_cancelled",
            "subscription_resumed",
            "subscription_expired",
            "subscription_paused",
            "subscription_unpaused",
            "subscription_payment_failed",
            "subscription_payment_success",
            "subscription_payment_recovered",
            "subscription_payment_refunded",
            "subscription_plan_changed"
    );

    private final LemonSqueezyProperties properties;
    private final LemonSqueezySignatureVerifier signatureVerifier;
    private final LemonSqueezySubscriptionService subscriptionService;
    private final BillingEventRepository billingEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate independentTransaction;

    public LemonSqueezyWebhookService(LemonSqueezyProperties properties,
                                      LemonSqueezySignatureVerifier signatureVerifier,
                                      LemonSqueezySubscriptionService subscriptionService,
                                      BillingEventRepository billingEventRepository,
                                      ObjectMapper objectMapper,
                                      Clock clock,
                                      PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.signatureVerifier = signatureVerifier;
        this.subscriptionService = subscriptionService;
        this.billingEventRepository = billingEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public void ingest(String signature, String headerEventName, String rawBody) {
        signatureVerifier.verify(rawBody, signature, properties.webhookSecret());
        warnOnEventNameMismatch(headerEventName, rawBody);
        try {
            transactionTemplate.executeWithoutResult(status -> processVerified(headerEventName, rawBody));
        } catch (LemonSqueezyPayloadException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            independentTransaction.executeWithoutResult(status -> markFailed(headerEventName, rawBody, ex));
            throw ex;
        }
    }

    private void processVerified(String headerEventName, String rawBody) {
        LemonSqueezyWebhookPayload payload = LemonSqueezyWebhookPayload.parse(rawBody, headerEventName, objectMapper);
        String eventKey = eventKey(payload);
        if (billingEventRepository.existsByPaddleEventIdAndProcessingStatus(eventKey, SubscriptionStatuses.EVENT_PROCESSED)) {
            log.info("Duplicate Lemon Squeezy webhook event={} resourceType={} resourceId={} userId={} result=duplicate",
                    payload.eventName(), payload.resourceType(), payload.resourceId(), payload.userId());
            return;
        }
        BillingEvent event = billingEventRepository.findByPaddleEventId(eventKey)
                .orElseGet(() -> persistNew(eventKey, payload, rawBody));
        if (event.isProcessed()) {
            log.info("Duplicate Lemon Squeezy webhook event={} resourceType={} resourceId={} userId={} result=duplicate",
                    payload.eventName(), payload.resourceType(), payload.resourceId(), payload.userId());
            return;
        }
        LemonSqueezySubscriptionService.Outcome outcome;
        if (!SUPPORTED_EVENTS.contains(payload.eventName())) {
            outcome = new LemonSqueezySubscriptionService.Outcome("ignored_event", null, payload.userId());
        } else {
            outcome = subscriptionService.apply(payload);
        }
        event.setProcessingStatus(SubscriptionStatuses.EVENT_PROCESSED);
        event.setProcessedAt(clock.instant());
        event.setErrorMessage(null);
        billingEventRepository.save(event);
        log.info("Lemon Squeezy webhook event={} resourceType={} resourceId={} userId={} tenantId={} result={}",
                payload.eventName(), payload.resourceType(), payload.resourceId(),
                outcome.userId() != null ? outcome.userId() : payload.userId(),
                outcome.tenantId(), outcome.result());
    }

    private BillingEvent persistNew(String eventKey, LemonSqueezyWebhookPayload payload, String rawBody) {
        BillingEvent event = new BillingEvent();
        event.setPaddleEventId(eventKey);
        event.setEventType(payload.eventName());
        event.setOccurredAt(payload.updatedAt() != null ? payload.updatedAt() : payload.createdAt());
        event.setReceivedAt(clock.instant());
        event.setProcessingStatus(SubscriptionStatuses.EVENT_RECEIVED);
        event.setPayload(rawBody);
        try {
            return billingEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            return billingEventRepository.findByPaddleEventId(eventKey).orElseThrow(() -> ex);
        }
    }

    private void markFailed(String headerEventName, String rawBody, Exception error) {
        try {
            LemonSqueezyWebhookPayload payload = LemonSqueezyWebhookPayload.parse(rawBody, headerEventName, objectMapper);
            String eventKey = eventKey(payload);
            BillingEvent event = billingEventRepository.findByPaddleEventId(eventKey).orElseGet(() -> {
                BillingEvent created = new BillingEvent();
                created.setPaddleEventId(eventKey);
                created.setEventType(payload.eventName());
                created.setOccurredAt(payload.updatedAt());
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
            log.error("Lemon Squeezy webhook failed event={} resourceType={} resourceId={} userId={} result=failed",
                    payload.eventName(), payload.resourceType(), payload.resourceId(), payload.userId());
        } catch (Exception ex) {
            log.warn("Unable to persist Lemon Squeezy webhook failure: {}", ex.getClass().getSimpleName());
        }
    }

    private void warnOnEventNameMismatch(String headerEventName, String rawBody) {
        if (!StringUtils.hasText(headerEventName) || rawBody == null) {
            return;
        }
        try {
            LemonSqueezyWebhookPayload payload = LemonSqueezyWebhookPayload.parse(rawBody, null, objectMapper);
            if (!headerEventName.trim().equalsIgnoreCase(payload.eventName())) {
                log.warn("Lemon Squeezy X-Event-Name {} differs from meta.event_name {}",
                        headerEventName.trim(), payload.eventName());
            }
        } catch (RuntimeException ex) {
            // Signature already passed. Parsing errors are reported by processVerified.
        }
    }

    static String eventKey(LemonSqueezyWebhookPayload payload) {
        String marker = payload.updatedAt() != null
                ? payload.updatedAt().toString()
                : (payload.createdAt() != null ? payload.createdAt().toString() : "na");
        String key = "ls:" + payload.eventName() + ":" + payload.resourceType() + ":" + payload.resourceId() + ":" + marker;
        if (key.length() <= 180) {
            return key;
        }
        return "ls:" + sha256(key);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "Processing failed";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("secret") || lower.contains("signature") || lower.contains("bearer") || lower.contains("password")) {
            return "Processing failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
