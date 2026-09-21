package com.animalin.billing;

import com.animalin.audit.AuditService;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.signup.ClinicSignup;
import com.animalin.signup.ClinicSignupRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class SubscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSyncService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final ClinicSignupRepository signupRepository;
    private final PaddleProperties paddleProperties;
    private final AuditService auditService;
    private final Clock clock;

    public SubscriptionSyncService(SubscriptionRepository subscriptionRepository,
                                   TenantRepository tenantRepository,
                                   PlanRepository planRepository,
                                   ClinicSignupRepository signupRepository,
                                   PaddleProperties paddleProperties,
                                   AuditService auditService,
                                   Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.signupRepository = signupRepository;
        this.paddleProperties = paddleProperties;
        this.auditService = auditService;
        this.clock = clock;
    }

    public void applyCustomer(PaddleDtos.Customer customer) {
        if (customer == null || !StringUtils.hasText(customer.id())) {
            return;
        }
        Optional<Subscription> existing = subscriptionRepository.findFirstByPaddleCustomerIdOrderByStartedAtDesc(customer.id());
        if (existing.isPresent()) {
            return;
        }
        Long tenantId = tenantIdFromCustomData(customer.customData());
        Tenant tenant = tenantId != null
                ? tenantRepository.findById(tenantId).orElse(null)
                : tenantRepository.findFirstByEmailIgnoreCase(customer.email()).orElse(null);
        if (tenant == null) {
            log.info("Ignoring Paddle customer {} without a matching tenant", customer.id());
            return;
        }
        Subscription subscription = requireTenantSubscription(tenant);
        if (!StringUtils.hasText(subscription.getPaddleCustomerId())) {
            subscription.setPaddleCustomerId(customer.id());
        }
    }

    public void applySubscription(PaddleDtos.Subscription paddleSub) {
        if (paddleSub == null || !StringUtils.hasText(paddleSub.id())) {
            return;
        }
        Subscription subscription = resolveFromSubscription(paddleSub);
        if (subscription == null) {
            log.info("Ignoring Paddle subscription {} without a matching tenant", paddleSub.id());
            return;
        }
        copyPaddleIdentity(subscription, paddleSub);
        applyPlanFromPrice(subscription, paddleSub.firstPriceId(), paddleSub.firstProductId());
        applyBillingPeriod(subscription, paddleSub);

        String paddleStatus = paddleSub.status() == null ? "" : paddleSub.status().toLowerCase(Locale.ROOT);
        switch (paddleStatus) {
            case "active" -> applyActiveOrScheduledCancel(subscription, paddleSub);
            case "trialing" -> applyTrialing(subscription, paddleSub);
            case "past_due" -> enterGracePeriod(subscription);
            case "canceled", "cancelled" -> applyCanceled(subscription, paddleSub);
            case "paused" -> {
                subscription.setStatus(SubscriptionStatuses.PAUSED);
                subscription.getTenant().setStatus(SubscriptionStatuses.SUSPENDED);
                auditService.record(subscription.getTenant().getId(), null, "paddle", "PAUSE", "SUBSCRIPTION",
                        subscription.getId(), "Paddle subscription paused", paddleStatus, SubscriptionStatuses.PAUSED);
            }
            default -> log.info("Unhandled Paddle subscription status {}", paddleStatus);
        }
    }

    public void applyTransaction(PaddleDtos.Transaction transaction) {
        if (transaction == null) {
            return;
        }
        Subscription subscription = resolveFromTransaction(transaction);
        if (subscription == null) {
            log.info("Ignoring Paddle transaction {} without a matching tenant", transaction.id());
            return;
        }
        if (StringUtils.hasText(transaction.customerId())) {
            subscription.setPaddleCustomerId(transaction.customerId());
        }
        if (StringUtils.hasText(transaction.subscriptionId())) {
            subscription.setPaddleSubscriptionId(transaction.subscriptionId());
        }
        if (StringUtils.hasText(transaction.id())) {
            subscription.setPaddleTransactionId(transaction.id());
        }
        applyPlanFromPrice(subscription, transaction.firstPriceId(), null);
        if (StringUtils.hasText(transaction.currencyCode())) {
            subscription.setCurrency(transaction.currencyCode());
        }

        String status = transaction.status() == null ? "" : transaction.status().toLowerCase(Locale.ROOT);
        if ("completed".equals(status) || "paid".equals(status) || "billed".equals(status)) {
            activate(subscription, transaction.id());
        } else if ("past_due".equals(status) || "payment_failed".equals(status)) {
            enterGracePeriod(subscription);
        }
    }

    public void activate(Subscription subscription, String transactionId) {
        Instant now = clock.instant();
        String previous = subscription.getStatus();
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscription.setTrial(false);
        subscription.setLastPaymentSucceededAt(now);
        subscription.setFirstPaymentFailedAt(null);
        subscription.setGracePeriodEndsAt(null);
        subscription.setSuspendedAt(null);
        subscription.setCancelledAt(null);
        subscription.setScheduledChangeAction(null);
        subscription.setScheduledChangeEffectiveAt(null);
        if (StringUtils.hasText(transactionId)) {
            subscription.setPaddleTransactionId(transactionId);
        }
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(SubscriptionStatuses.ACTIVE);
        tenant.setPlan(subscription.getPlan());
        completeSignup(tenant);
        auditService.record(tenant.getId(), null, "paddle", "ACTIVATE", "SUBSCRIPTION",
                subscription.getId(), "Payment succeeded; access restored", previous, SubscriptionStatuses.ACTIVE);
    }

    public void enterGracePeriod(Subscription subscription) {
        if (SubscriptionStatuses.SUSPENDED.equals(subscription.getStatus())
                || SubscriptionStatuses.CANCELED.equals(subscription.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        String previous = subscription.getStatus();
        if (subscription.getFirstPaymentFailedAt() == null) {
            subscription.setFirstPaymentFailedAt(now);
            subscription.setGracePeriodEndsAt(now.plus(paddleProperties.gracePeriodDays(), ChronoUnit.DAYS));
        }
        subscription.setStatus(SubscriptionStatuses.GRACE_PERIOD);
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(SubscriptionStatuses.PAST_DUE);
        auditService.record(tenant.getId(), null, "paddle", "GRACE_PERIOD", "SUBSCRIPTION",
                subscription.getId(), "Payment failed; grace period until " + subscription.getGracePeriodEndsAt(),
                previous, SubscriptionStatuses.GRACE_PERIOD);
    }

    public void suspendExpired(Subscription subscription) {
        if (!SubscriptionStatuses.GRACE_PERIOD.equals(subscription.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        if (subscription.getGracePeriodEndsAt() == null || !subscription.getGracePeriodEndsAt().isBefore(now)) {
            return;
        }
        suspend(subscription, "Grace period expired");
    }

    public void suspend(Subscription subscription, String reason) {
        Instant now = clock.instant();
        String previous = subscription.getStatus();
        subscription.setStatus(SubscriptionStatuses.SUSPENDED);
        if (subscription.getSuspendedAt() == null) {
            subscription.setSuspendedAt(now);
        }
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(SubscriptionStatuses.SUSPENDED);
        auditService.record(tenant.getId(), null, "paddle", "SUSPEND", "SUBSCRIPTION",
                subscription.getId(), reason, previous, SubscriptionStatuses.SUSPENDED);
    }

    private void applyActiveOrScheduledCancel(Subscription subscription, PaddleDtos.Subscription paddleSub) {
        if (paddleSub.scheduledChange() != null && "cancel".equalsIgnoreCase(paddleSub.scheduledChange().action())) {
            subscription.setScheduledChangeAction("cancel");
            subscription.setScheduledChangeEffectiveAt(paddleSub.scheduledChange().effectiveAt());
            if (!SubscriptionStatuses.GRACE_PERIOD.equals(subscription.getStatus())
                    && !SubscriptionStatuses.SUSPENDED.equals(subscription.getStatus())) {
                subscription.setStatus(SubscriptionStatuses.ACTIVE);
                subscription.setTrial(false);
                Tenant tenant = subscription.getTenant();
                tenant.setStatus(SubscriptionStatuses.ACTIVE);
            }
            auditService.record(subscription.getTenant().getId(), null, "paddle", "SCHEDULE_CANCEL", "SUBSCRIPTION",
                    subscription.getId(),
                    "Cancellation scheduled at " + paddleSub.scheduledChange().effectiveAt(),
                    null, "cancel");
            return;
        }
        activate(subscription, paddleSub.transactionId());
        if (paddleSub.startedAt() != null && subscription.getStartedAt() == null) {
            subscription.setStartedAt(paddleSub.startedAt());
        }
    }

    private void applyTrialing(Subscription subscription, PaddleDtos.Subscription paddleSub) {
        subscription.setStatus(SubscriptionStatuses.TRIALING);
        subscription.setTrial(true);
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(SubscriptionStatuses.TRIAL);
        if (paddleSub.currentBillingPeriod() != null && paddleSub.currentBillingPeriod().endsAt() != null) {
            tenant.setTrialEndsAt(paddleSub.currentBillingPeriod().endsAt());
        }
        if (paddleSub.startedAt() != null) {
            subscription.setStartedAt(paddleSub.startedAt());
        }
        completeSignup(tenant);
    }

    private void completeSignup(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            return;
        }
        signupRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenant.getId()).ifPresent(signup -> {
            signup.setStatus(ClinicSignup.COMPLETED);
            if (signup.getCompletedAt() == null) {
                signup.setCompletedAt(clock.instant());
            }
        });
    }

    private void applyCanceled(Subscription subscription, PaddleDtos.Subscription paddleSub) {
        Instant now = clock.instant();
        Instant effective = paddleSub.canceledAt() != null ? paddleSub.canceledAt() : now;
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        boolean futureCancel = periodEnd != null && periodEnd.isAfter(now) && effective.isAfter(now);
        if (futureCancel) {
            subscription.setScheduledChangeAction("cancel");
            subscription.setScheduledChangeEffectiveAt(effective);
            subscription.setCancelledAt(effective);
            return;
        }
        String previous = subscription.getStatus();
        subscription.setStatus(SubscriptionStatuses.CANCELED);
        subscription.setCancelledAt(effective);
        subscription.setScheduledChangeAction(null);
        subscription.setScheduledChangeEffectiveAt(null);
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(SubscriptionStatuses.SUSPENDED);
        auditService.record(tenant.getId(), null, "paddle", "CANCEL", "SUBSCRIPTION",
                subscription.getId(), "Paddle subscription canceled", previous, SubscriptionStatuses.CANCELED);
    }

    private void copyPaddleIdentity(Subscription subscription, PaddleDtos.Subscription paddleSub) {
        subscription.setPaddleSubscriptionId(paddleSub.id());
        if (StringUtils.hasText(paddleSub.customerId())) {
            subscription.setPaddleCustomerId(paddleSub.customerId());
        }
        if (StringUtils.hasText(paddleSub.transactionId())) {
            subscription.setPaddleTransactionId(paddleSub.transactionId());
        }
        if (StringUtils.hasText(paddleSub.currencyCode())) {
            subscription.setCurrency(paddleSub.currencyCode());
        }
        if (paddleSub.billingCycle() != null && "year".equalsIgnoreCase(paddleSub.billingCycle().interval())) {
            subscription.setBillingCycle(SubscriptionStatuses.CYCLE_ANNUAL);
        } else if (paddleSub.billingCycle() != null) {
            subscription.setBillingCycle(SubscriptionStatuses.CYCLE_MONTHLY);
        }
        if (paddleSub.startedAt() != null) {
            subscription.setStartedAt(paddleSub.startedAt());
        }
    }

    private void applyBillingPeriod(Subscription subscription, PaddleDtos.Subscription paddleSub) {
        if (paddleSub.currentBillingPeriod() != null) {
            subscription.setCurrentPeriodStartsAt(paddleSub.currentBillingPeriod().startsAt());
            subscription.setCurrentPeriodEndsAt(paddleSub.currentBillingPeriod().endsAt());
        }
        subscription.setNextBillingAt(paddleSub.nextBilledAt());
        if (paddleSub.canceledAt() != null) {
            subscription.setCancelledAt(paddleSub.canceledAt());
        }
    }

    private void applyPlanFromPrice(Subscription subscription, String priceId, String productId) {
        if (StringUtils.hasText(priceId)) {
            subscription.setPaddlePriceId(priceId);
        }
        if (StringUtils.hasText(productId)) {
            subscription.setPaddleProductId(productId);
        }
        Plan plan = findPlan(priceId, productId);
        if (plan != null) {
            subscription.setPlan(plan);
            subscription.getTenant().setPlan(plan);
            if (!StringUtils.hasText(subscription.getPaddleProductId()) && StringUtils.hasText(plan.getPaddleProductId())) {
                subscription.setPaddleProductId(plan.getPaddleProductId());
            }
            if (StringUtils.hasText(priceId)) {
                if (priceId.equals(plan.getPaddleAnnualPriceId())) {
                    subscription.setBillingCycle(SubscriptionStatuses.CYCLE_ANNUAL);
                } else if (priceId.equals(plan.getPaddleMonthlyPriceId())) {
                    subscription.setBillingCycle(SubscriptionStatuses.CYCLE_MONTHLY);
                }
            }
        }
    }

    private Plan findPlan(String priceId, String productId) {
        if (StringUtils.hasText(priceId)) {
            Optional<Plan> byPrice = planRepository.findByPaddleMonthlyPriceIdOrPaddleAnnualPriceId(priceId, priceId);
            if (byPrice.isPresent()) {
                return byPrice.get();
            }
        }
        if (StringUtils.hasText(productId)) {
            return planRepository.findByPaddleProductId(productId).orElse(null);
        }
        return null;
    }

    private Subscription resolveFromSubscription(PaddleDtos.Subscription paddleSub) {
        Optional<Subscription> byPaddleId = subscriptionRepository.findByPaddleSubscriptionId(paddleSub.id());
        if (byPaddleId.isPresent()) {
            return byPaddleId.get();
        }
        Long tenantId = tenantIdFromCustomData(paddleSub.customData());
        if (tenantId != null) {
            return tenantRepository.findById(tenantId).map(this::requireTenantSubscription).orElse(null);
        }
        if (StringUtils.hasText(paddleSub.customerId())) {
            return subscriptionRepository.findFirstByPaddleCustomerIdOrderByStartedAtDesc(paddleSub.customerId()).orElse(null);
        }
        return null;
    }

    private Subscription resolveFromTransaction(PaddleDtos.Transaction transaction) {
        if (StringUtils.hasText(transaction.subscriptionId())) {
            Optional<Subscription> bySub = subscriptionRepository.findByPaddleSubscriptionId(transaction.subscriptionId());
            if (bySub.isPresent()) {
                return bySub.get();
            }
        }
        Long tenantId = tenantIdFromCustomData(transaction.customData());
        if (tenantId != null) {
            return tenantRepository.findById(tenantId).map(this::requireTenantSubscription).orElse(null);
        }
        if (StringUtils.hasText(transaction.customerId())) {
            return subscriptionRepository.findFirstByPaddleCustomerIdOrderByStartedAtDesc(transaction.customerId()).orElse(null);
        }
        return null;
    }

    private Subscription requireTenantSubscription(Tenant tenant) {
        return subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenant.getId()).orElseGet(() -> {
            Subscription created = new Subscription();
            created.setTenant(tenant);
            created.setPlan(tenant.getPlan());
            created.setStatus(StringUtils.hasText(tenant.getStatus()) ? tenant.getStatus() : SubscriptionStatuses.TRIAL);
            created.setTrial(true);
            created.setCurrency(paddleProperties.sandbox() ? "USD" : Optional.ofNullable(tenant.getCurrency()).orElse("USD"));
            created.setStartedAt(clock.instant());
            return subscriptionRepository.save(created);
        });
    }

    static Long tenantIdFromCustomData(Map<String, Object> customData) {
        if (customData == null) {
            return null;
        }
        Object raw = customData.get("tenant_id");
        if (raw == null) {
            raw = customData.get("tenantId");
        }
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = raw.toString().trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
