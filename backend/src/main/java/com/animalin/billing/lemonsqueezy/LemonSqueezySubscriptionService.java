package com.animalin.billing.lemonsqueezy;

import com.animalin.audit.AuditService;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.signup.ClinicSignup;
import com.animalin.signup.ClinicSignupRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.RoleCodes;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class LemonSqueezySubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(LemonSqueezySubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final ClinicSignupRepository signupRepository;
    private final LemonSqueezyProperties properties;
    private final AuditService auditService;
    private final Clock clock;

    public LemonSqueezySubscriptionService(SubscriptionRepository subscriptionRepository,
                                           TenantRepository tenantRepository,
                                           TenantMembershipRepository membershipRepository,
                                           UserRepository userRepository,
                                           PlanRepository planRepository,
                                           ClinicSignupRepository signupRepository,
                                           LemonSqueezyProperties properties,
                                           AuditService auditService,
                                           Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.signupRepository = signupRepository;
        this.properties = properties;
        this.auditService = auditService;
        this.clock = clock;
    }

    public Outcome apply(LemonSqueezyWebhookPayload payload) {
        return switch (payload.eventName()) {
            case "order_created" -> applyOrder(payload, false);
            case "order_refunded" -> applyOrder(payload, true);
            case "subscription_payment_success", "subscription_payment_recovered" ->
                    applyPayment(payload, payload.eventName().endsWith("recovered") ? PaymentKind.RECOVERED : PaymentKind.SUCCESS);
            case "subscription_payment_failed" -> applyPayment(payload, PaymentKind.FAILED);
            case "subscription_payment_refunded" -> applyPayment(payload, PaymentKind.REFUNDED);
            default -> applySubscription(payload);
        };
    }

    private Outcome applySubscription(LemonSqueezyWebhookPayload payload) {
        Resolved resolved = resolveSubscription(payload.subscriptionId(), payload, true);
        if (resolved.ignored() != null) {
            return outcome(resolved.ignored(), resolved.subscription(), payload);
        }
        Subscription subscription = resolved.subscription();
        if (isStale(subscription, payload.updatedAt())) {
            return outcome("ignored_stale", subscription, payload);
        }
        boolean creating = subscription.getId() == null;
        String previousVariantId = subscription.getLsVariantId();
        copySubscriptionIdentity(subscription, payload);
        boolean requirePlan = creating
                || "subscription_created".equals(payload.eventName())
                || "subscription_plan_changed".equals(payload.eventName());
        applyPlan(subscription, payload, requirePlan, previousVariantId);
        applySubscriptionStatus(subscription, payload);
        touchProviderTimestamps(subscription, payload);
        subscriptionRepository.save(subscription);
        return outcome(creating ? "created" : "updated", subscription, payload);
    }

    private Outcome applyPayment(LemonSqueezyWebhookPayload payload, PaymentKind kind) {
        Resolved resolved = resolveSubscription(payload.subscriptionId(), payload, true);
        if (resolved.ignored() != null) {
            return outcome(resolved.ignored(), resolved.subscription(), payload);
        }
        Subscription subscription = resolved.subscription();
        copyPaymentIdentity(subscription, payload);
        if (subscription.getPlan() == null) {
            throw new LemonSqueezyProcessingException("LunaVeta subscription has no plan");
        }
        Instant occurredAt = payload.updatedAt() != null ? payload.updatedAt() : clock.instant();
        switch (kind) {
            case SUCCESS, RECOVERED -> recordPaymentSuccess(subscription, payload, occurredAt, kind == PaymentKind.RECOVERED);
            case FAILED -> recordPaymentFailure(subscription, occurredAt);
            case REFUNDED -> recordRefund(subscription, payload, occurredAt);
        }
        subscriptionRepository.save(subscription);
        return outcome(kind.name().toLowerCase(Locale.ROOT), subscription, payload);
    }

    private Outcome applyOrder(LemonSqueezyWebhookPayload payload, boolean refund) {
        Subscription subscription = findOrderSubscription(payload);
        if (subscription == null) {
            Resolved resolved = resolveSubscription(null, payload, false);
            if (resolved.ignored() != null) {
                return outcome(resolved.ignored(), null, payload);
            }
            subscription = resolved.subscription();
        }
        if (subscription == null || conflicts(subscription, payload.testMode())) {
            log.info("Lemon Squeezy order stored without a subscription event={} resourceId={} userId={} testMode={}",
                    payload.eventName(), payload.resourceId(), payload.userId(), payload.testMode());
            return new Outcome(refund ? "refund_unlinked" : "order_unlinked", payload.tenantId(), payload.userId());
        }
        if (refund) {
            recordRefund(subscription, payload, payload.updatedAt() != null ? payload.updatedAt() : clock.instant());
            subscriptionRepository.save(subscription);
            return outcome("refunded", subscription, payload);
        }
        if (payload.resourceId().equals(subscription.getLsOrderId())) {
            return outcome("order_already_linked", subscription, payload);
        }
        if (!StringUtils.hasText(subscription.getLsOrderId())) {
            subscription.setLsOrderId(payload.resourceId());
            String orderItemId = payload.childAttr("first_order_item", "id");
            if (StringUtils.hasText(orderItemId)) {
                subscription.setLsOrderItemId(orderItemId);
            }
            copyIfBlank(subscription::getLsCustomerId, subscription::setLsCustomerId, payload.attr("customer_id"));
            copyIfBlank(subscription::getLsProductId, subscription::setLsProductId, payload.childAttr("first_order_item", "product_id"));
            copyIfBlank(subscription::getLsVariantId, subscription::setLsVariantId, payload.childAttr("first_order_item", "variant_id"));
            if (subscription.getLsTestMode() == null) {
                subscription.setLsTestMode(payload.testMode());
            }
            subscriptionRepository.save(subscription);
            return outcome("order_linked", subscription, payload);
        }
        return outcome("order_already_linked", subscription, payload);
    }

    private Resolved resolveSubscription(String lemonSubscriptionId, LemonSqueezyWebhookPayload payload, boolean required) {
        if (StringUtils.hasText(lemonSubscriptionId)) {
            Optional<Subscription> byId = subscriptionRepository.findByLsSubscriptionIdAndLsTestMode(
                    lemonSubscriptionId, payload.testMode());
            if (byId.isPresent()) {
                Subscription subscription = byId.get();
                attachUserIfMissing(subscription, payload);
                return Resolved.found(subscription);
            }
        }
        TenantLink link = resolveTenant(payload, required);
        if (link == null) {
            return Resolved.ignored("unlinked");
        }
        Subscription latest = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(link.tenant().getId()).orElse(null);
        if (latest != null && conflicts(latest, payload.testMode())) {
            log.info("Ignored Lemon Squeezy webhook because test mode does not match the tenant subscription event={} resourceId={} tenantId={} testMode={}",
                    payload.eventName(), payload.resourceId(), link.tenant().getId(), payload.testMode());
            return Resolved.ignored("ignored_test_mode");
        }
        if (latest == null) {
            if (!required) {
                return Resolved.ignored("unlinked");
            }
            latest = newSubscription(link.tenant(), payload);
        }
        if (link.user() != null && latest.getUser() == null) {
            latest.setUser(link.user());
        }
        return Resolved.found(latest);
    }

    private Subscription findOrderSubscription(LemonSqueezyWebhookPayload payload) {
        Optional<Subscription> byOrder = subscriptionRepository
                .findFirstByLsOrderIdAndLsTestModeOrderByStartedAtDesc(payload.resourceId(), payload.testMode());
        if (byOrder.isPresent()) {
            return byOrder.get();
        }
        String customerId = payload.attr("customer_id");
        if (!StringUtils.hasText(customerId)) {
            return null;
        }
        return subscriptionRepository
                .findFirstByLsCustomerIdAndLsTestModeOrderByStartedAtDesc(customerId, payload.testMode())
                .orElse(null);
    }

    private boolean conflicts(Subscription subscription, boolean testMode) {
        if (subscription.getLsTestMode() != null && subscription.getLsTestMode() != testMode) {
            return true;
        }
        if (testMode && subscription.getLsTestMode() == null && StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
            return true;
        }
        return testMode && subscription.getLsTestMode() == null && StringUtils.hasText(subscription.getLsSubscriptionId());
    }

    private TenantLink resolveTenant(LemonSqueezyWebhookPayload payload, boolean required) {
        User user = null;
        if (payload.userId() != null) {
            user = userRepository.findById(payload.userId())
                    .orElseThrow(() -> new LemonSqueezyProcessingException("LunaVeta user was not found"));
        }
        if (payload.tenantId() != null) {
            Tenant tenant = tenantRepository.findById(payload.tenantId())
                    .orElseThrow(() -> new LemonSqueezyProcessingException("LunaVeta clinic was not found"));
            if (user != null && !membershipRepository.existsByTenantIdAndUserId(tenant.getId(), user.getId())) {
                throw new LemonSqueezyProcessingException("LunaVeta user does not belong to the clinic");
            }
            return new TenantLink(tenant, user);
        }
        if (user == null) {
            if (required) {
                throw new LemonSqueezyProcessingException("Webhook is missing meta.custom_data.user_id");
            }
            return null;
        }
        List<TenantMembership> active = membershipRepository.findActiveByUserId(user.getId());
        List<TenantMembership> owners = active.stream()
                .filter(membership -> RoleCodes.TENANT_OWNER.equals(membership.getRole().getCode()))
                .toList();
        List<TenantMembership> candidates = owners.isEmpty() ? active : owners;
        if (candidates.size() == 1) {
            return new TenantLink(candidates.getFirst().getTenant(), user);
        }
        if (candidates.isEmpty()) {
            throw new LemonSqueezyProcessingException("LunaVeta user has no clinic");
        }
        throw new LemonSqueezyProcessingException("LunaVeta user owns multiple clinics; meta.custom_data.tenant_id is required");
    }

    private void attachUserIfMissing(Subscription subscription, LemonSqueezyWebhookPayload payload) {
        if (subscription.getUser() != null || payload.userId() == null) {
            return;
        }
        userRepository.findById(payload.userId()).ifPresent(subscription::setUser);
    }

    private Subscription newSubscription(Tenant tenant, LemonSqueezyWebhookPayload payload) {
        Subscription created = new Subscription();
        created.setTenant(tenant);
        created.setPlan(tenant.getPlan());
        created.setStatus(SubscriptionStatuses.PENDING);
        created.setTrial(false);
        created.setCurrency("USD");
        created.setBillingCycle(SubscriptionStatuses.CYCLE_MONTHLY);
        created.setStartedAt(payload.createdAt() != null ? payload.createdAt() : clock.instant());
        created.setLsTestMode(payload.testMode());
        return created;
    }

    private void copySubscriptionIdentity(Subscription subscription, LemonSqueezyWebhookPayload payload) {
        subscription.setLsSubscriptionId(payload.resourceId());
        subscription.setLsTestMode(payload.testMode());
        setText(payload.attr("customer_id"), subscription::setLsCustomerId);
        setText(payload.attr("order_id"), subscription::setLsOrderId);
        setText(payload.attr("order_item_id"), subscription::setLsOrderItemId);
        setText(payload.attr("product_id"), subscription::setLsProductId);
        setText(payload.attr("variant_id"), subscription::setLsVariantId);
        setText(payload.attr("variant_name"), subscription::setLsVariantName);
        setText(payload.attr("product_name"), subscription::setLsProductName);
        setText(payload.attr("product_sku"), subscription::setLsProductSku);
        if (!StringUtils.hasText(subscription.getBillingCycle())) {
            subscription.setBillingCycle(SubscriptionStatuses.CYCLE_MONTHLY);
        }
        if (!StringUtils.hasText(subscription.getCurrency())) {
            subscription.setCurrency("USD");
        }
        if (payload.createdAt() != null && subscription.getLsCreatedAt() == null) {
            subscription.setLsCreatedAt(payload.createdAt());
        }
        if (subscription.getStartedAt() == null && payload.createdAt() != null) {
            subscription.setStartedAt(payload.createdAt());
        }
    }

    private void copyPaymentIdentity(Subscription subscription, LemonSqueezyWebhookPayload payload) {
        if (subscription.getLsTestMode() == null) {
            subscription.setLsTestMode(payload.testMode());
        }
        setText(payload.attr("customer_id"), subscription::setLsCustomerId);
        String subscriptionId = payload.subscriptionId();
        if (StringUtils.hasText(subscriptionId) && !StringUtils.hasText(subscription.getLsSubscriptionId())) {
            subscription.setLsSubscriptionId(subscriptionId);
        }
    }

    private void applyPlan(Subscription subscription, LemonSqueezyWebhookPayload payload, boolean requireMapped,
                           String previousVariantId) {
        String variantId = payload.attr("variant_id");
        String productId = payload.attr("product_id");
        String variantCode = properties.planCodeForVariant(variantId);
        final String code = variantCode != null ? variantCode : properties.planCodeForProduct(productId);
        boolean variantChanged = StringUtils.hasText(variantId) && !variantId.equals(previousVariantId);
        if (code == null) {
            Plan current = subscription.getPlan() != null ? subscription.getPlan() : subscription.getTenant().getPlan();
            if (requireMapped || variantChanged || current == null) {
                log.error("No LunaVeta plan mapped for Lemon Squeezy variant event={} resourceId={} testMode={}",
                        payload.eventName(), payload.resourceId(), payload.testMode());
                throw new LemonSqueezyProcessingException(
                        "No LunaVeta plan is mapped for this Lemon Squeezy variant. Set LEMONSQUEEZY_VARIANT_BASIC, LEMONSQUEEZY_VARIANT_PROFESSIONAL and LEMONSQUEEZY_VARIANT_PREMIUM.");
            }
            subscription.setPlan(current);
            return;
        }
        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new LemonSqueezyProcessingException("LunaVeta plan " + code + " was not found"));
        String previous = subscription.getPlan() == null ? null : subscription.getPlan().getCode();
        subscription.setPlan(plan);
        subscription.getTenant().setPlan(plan);
        if (previous != null && !previous.equals(plan.getCode())) {
            audit(subscription, "PLAN_CHANGED", "Lemon Squeezy plan changed", previous, plan.getCode());
        }
    }

    private void applySubscriptionStatus(Subscription subscription, LemonSqueezyWebhookPayload payload) {
        String event = payload.eventName();
        String lsStatus = payload.attr("status");
        lsStatus = lsStatus == null ? "" : lsStatus.toLowerCase(Locale.ROOT);
        subscription.setLsStatus(lsStatus);
        Instant endsAt = payload.attrInstant("ends_at");
        Instant renewsAt = payload.attrInstant("renews_at");
        Instant trialEndsAt = payload.attrInstant("trial_ends_at");
        subscription.setEndsAt(endsAt);
        subscription.setRenewsAt(renewsAt);
        subscription.setTrialEndsAt(trialEndsAt);
        subscription.setNextBillingAt(renewsAt);
        if (renewsAt != null && endsAt == null) {
            subscription.setCurrentPeriodEnd(renewsAt);
        }

        if ("subscription_expired".equals(event) || "expired".equals(lsStatus)) {
            expire(subscription, endsAt);
            return;
        }
        if ("subscription_resumed".equals(event)) {
            clearCancellation(subscription);
            subscription.setLsPaused(false);
            if ("on_trial".equals(lsStatus)) {
                startTrial(subscription, trialEndsAt);
            } else if ("past_due".equals(lsStatus) || "unpaid".equals(lsStatus)) {
                markPastDue(subscription, clock.instant());
            } else {
                activate(subscription);
            }
            return;
        }
        if ("subscription_unpaused".equals(event) || ("active".equals(lsStatus) && !payload.pausePresent() && subscription.isLsPaused())) {
            subscription.setLsPaused(false);
        }
        boolean cancelled = payload.attrBoolean("cancelled")
                || "subscription_cancelled".equals(event)
                || "cancelled".equals(lsStatus);
        boolean paused = payload.pausePresent() || "paused".equals(lsStatus) || "subscription_paused".equals(event);
        if ("subscription_unpaused".equals(event)) {
            paused = payload.pausePresent() || "paused".equals(lsStatus);
        }
        subscription.setLsCancelled(cancelled);
        subscription.setLsPaused(paused);

        if (paused) {
            pause(subscription);
            return;
        }
        if (cancelled) {
            cancel(subscription, endsAt);
            return;
        }
        if ("on_trial".equals(lsStatus)) {
            startTrial(subscription, trialEndsAt);
            return;
        }
        if ("past_due".equals(lsStatus) || "unpaid".equals(lsStatus)) {
            markPastDue(subscription, payload.updatedAt() != null ? payload.updatedAt() : clock.instant());
            return;
        }
        if ("active".equals(lsStatus) || "subscription_unpaused".equals(event)
                || ("subscription_created".equals(event) && lsStatus.isEmpty())) {
            activate(subscription);
            return;
        }
        log.info("Unhandled Lemon Squeezy subscription status event={} resourceId={} status={}",
                event, payload.resourceId(), lsStatus);
    }

    private void recordPaymentSuccess(Subscription subscription, LemonSqueezyWebhookPayload payload,
                                       Instant occurredAt, boolean recovered) {
        subscription.setLastPaymentSucceededAt(occurredAt);
        subscription.setFirstPaymentFailedAt(null);
        subscription.setGracePeriodEndsAt(null);
        subscription.setSuspendedAt(null);
        Instant renewsAt = payload.attrInstant("renews_at");
        if (renewsAt != null) {
            subscription.setRenewsAt(renewsAt);
            subscription.setNextBillingAt(renewsAt);
            if (!subscription.isLsCancelled()) {
                subscription.setCurrentPeriodEnd(renewsAt);
            }
        }
        String status = subscription.getStatus();
        if (SubscriptionStatuses.PAST_DUE.equals(status)
                || SubscriptionStatuses.GRACE_PERIOD.equals(status)
                || SubscriptionStatuses.PENDING.equals(status)
                || SubscriptionStatuses.PENDING_PAYMENT.equals(status)
                || recovered) {
            if (!subscription.isLsCancelled() && !subscription.isLsPaused()
                    && !SubscriptionStatuses.EXPIRED.equals(status)) {
                activate(subscription);
            }
        }
        audit(subscription, recovered ? "PAYMENT_RECOVERED" : "PAYMENT_SUCCESS",
                recovered ? "Lemon Squeezy payment recovered" : "Lemon Squeezy payment succeeded",
                null, SubscriptionStatuses.ACTIVE);
    }

    private void recordPaymentFailure(Subscription subscription, Instant occurredAt) {
        if (SubscriptionStatuses.EXPIRED.equals(subscription.getStatus())
                || SubscriptionStatuses.SUSPENDED.equals(subscription.getStatus())) {
            audit(subscription, "PAYMENT_FAILED", "Lemon Squeezy payment failed", subscription.getStatus(), subscription.getStatus());
            return;
        }
        markPastDue(subscription, occurredAt);
    }

    private void recordRefund(Subscription subscription, LemonSqueezyWebhookPayload payload, Instant occurredAt) {
        Instant refundedAt = payload.attrInstant("refunded_at");
        subscription.setLsRefundedAt(refundedAt != null ? refundedAt : occurredAt);
        audit(subscription, "PAYMENT_REFUNDED", "Lemon Squeezy payment refunded", null, "refunded");
    }

    private void activate(Subscription subscription) {
        String previous = subscription.getStatus();
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscription.setTrial(false);
        subscription.setLsPaused(false);
        subscription.setLsCancelled(false);
        clearCancellation(subscription);
        subscription.setSuspendedAt(null);
        subscription.setGracePeriodEndsAt(null);
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(SubscriptionStatuses.ACTIVE);
        if (subscription.getPlan() != null) {
            tenant.setPlan(subscription.getPlan());
        }
        completeSignup(tenant);
        audit(subscription, "ACTIVATE", "Lemon Squeezy subscription active", previous, SubscriptionStatuses.ACTIVE);
    }

    private void startTrial(Subscription subscription, Instant trialEndsAt) {
        String previous = subscription.getStatus();
        subscription.setStatus(SubscriptionStatuses.TRIALING);
        subscription.setTrial(true);
        subscription.setLsPaused(false);
        if (!subscription.isLsCancelled()) {
            clearCancellation(subscription);
        }
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(SubscriptionStatuses.TRIAL);
        if (trialEndsAt != null) {
            tenant.setTrialEndsAt(trialEndsAt);
            subscription.setCurrentPeriodEnd(trialEndsAt);
        }
        markTrialUsed(tenant, subscription);
        completeSignup(tenant);
        audit(subscription, "TRIAL", "Lemon Squeezy subscription trialing", previous, SubscriptionStatuses.TRIALING);
    }

    private void cancel(Subscription subscription, Instant endsAt) {
        Instant now = clock.instant();
        String previous = subscription.getStatus();
        subscription.setLsCancelled(true);
        subscription.setStatus(SubscriptionStatuses.CANCELED);
        if (subscription.getCancelledAt() == null) {
            subscription.setCancelledAt(now);
        }
        subscription.setScheduledChangeAction("cancel");
        subscription.setScheduledChangeEffectiveAt(endsAt);
        if (endsAt != null) {
            subscription.setEndsAt(endsAt);
            subscription.setCurrentPeriodEnd(endsAt);
        }
        boolean stillActive = endsAt != null && endsAt.isAfter(now);
        Tenant tenant = subscription.getTenant();
        if (stillActive) {
            boolean trial = subscription.isTrial() || SubscriptionStatuses.isTrial(previous);
            tenant.setStatus(trial ? SubscriptionStatuses.TRIAL : SubscriptionStatuses.ACTIVE);
        } else {
            tenant.setStatus(SubscriptionStatuses.SUSPENDED);
            subscription.clearPendingPlanChange();
        }
        audit(subscription, "CANCEL",
                stillActive ? "Lemon Squeezy subscription cancelled with access until ends_at" : "Lemon Squeezy subscription cancelled",
                previous, SubscriptionStatuses.CANCELED);
    }

    private void expire(Subscription subscription, Instant endsAt) {
        String previous = subscription.getStatus();
        Instant now = clock.instant();
        subscription.setStatus(SubscriptionStatuses.EXPIRED);
        subscription.setTrial(false);
        if (endsAt != null) {
            subscription.setEndsAt(endsAt);
            subscription.setCurrentPeriodEnd(endsAt);
        } else if (subscription.getCurrentPeriodEnd() == null || subscription.getCurrentPeriodEnd().isAfter(now)) {
            subscription.setCurrentPeriodEnd(now);
        }
        subscription.getTenant().setStatus(SubscriptionStatuses.SUSPENDED);
        subscription.clearPendingPlanChange();
        audit(subscription, "EXPIRE", "Lemon Squeezy subscription expired", previous, SubscriptionStatuses.EXPIRED);
    }

    private void pause(Subscription subscription) {
        String previous = subscription.getStatus();
        subscription.setLsPaused(true);
        subscription.setStatus(SubscriptionStatuses.PAUSED);
        subscription.getTenant().setStatus(SubscriptionStatuses.SUSPENDED);
        audit(subscription, "PAUSE", "Lemon Squeezy subscription paused", previous, SubscriptionStatuses.PAUSED);
    }

    private void markPastDue(Subscription subscription, Instant occurredAt) {
        if (subscription.isLsCancelled() || subscription.isLsPaused()
                || SubscriptionStatuses.CANCELED.equals(subscription.getStatus())
                || SubscriptionStatuses.EXPIRED.equals(subscription.getStatus())
                || SubscriptionStatuses.PAUSED.equals(subscription.getStatus())) {
            audit(subscription, "PAYMENT_FAILED", "Lemon Squeezy payment failed without cancelling the subscription",
                    subscription.getStatus(), subscription.getStatus());
            return;
        }
        String previous = subscription.getStatus();
        if (subscription.getFirstPaymentFailedAt() == null) {
            subscription.setFirstPaymentFailedAt(occurredAt);
        }
        subscription.setStatus(SubscriptionStatuses.PAST_DUE);
        subscription.getTenant().setStatus(SubscriptionStatuses.PAST_DUE);
        audit(subscription, "PAYMENT_FAILED", "Lemon Squeezy payment failed", previous, SubscriptionStatuses.PAST_DUE);
    }

    private void clearCancellation(Subscription subscription) {
        subscription.setLsCancelled(false);
        subscription.setCancelledAt(null);
        subscription.setScheduledChangeAction(null);
        subscription.setScheduledChangeEffectiveAt(null);
    }

    private void touchProviderTimestamps(Subscription subscription, LemonSqueezyWebhookPayload payload) {
        if (payload.createdAt() != null && subscription.getLsCreatedAt() == null) {
            subscription.setLsCreatedAt(payload.createdAt());
        }
        if (payload.updatedAt() != null) {
            subscription.setLsUpdatedAt(payload.updatedAt());
        }
    }

    private boolean isStale(Subscription subscription, Instant incomingUpdatedAt) {
        return subscription.getId() != null
                && subscription.getLsUpdatedAt() != null
                && incomingUpdatedAt != null
                && incomingUpdatedAt.isBefore(subscription.getLsUpdatedAt());
    }

    private void markTrialUsed(Tenant tenant, Subscription subscription) {
        if (tenant != null) {
            tenant.setTrialUsed(true);
        }
        if (subscription.getUser() != null) {
            subscription.getUser().setTrialUsed(true);
        }
        if (tenant != null && tenant.getId() != null) {
            signupRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenant.getId()).ifPresent(signup -> {
                if (signup.getUser() != null) {
                    signup.getUser().setTrialUsed(true);
                }
            });
        }
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

    private void audit(Subscription subscription, String action, String details, String oldValue, String newValue) {
        Tenant tenant = subscription.getTenant();
        Long tenantId = tenant == null ? null : tenant.getId();
        Long userId = subscription.getUser() == null ? null : subscription.getUser().getId();
        auditService.record(tenantId, userId, "lemonsqueezy", action, "SUBSCRIPTION",
                subscription.getId(), details, oldValue, newValue);
    }

    private Outcome outcome(String result, Subscription subscription, LemonSqueezyWebhookPayload payload) {
        Long tenantId = subscription != null && subscription.getTenant() != null ? subscription.getTenant().getId() : payload.tenantId();
        Long userId = subscription != null && subscription.getUser() != null ? subscription.getUser().getId() : payload.userId();
        return new Outcome(result, tenantId, userId);
    }

    private static void setText(String value, java.util.function.Consumer<String> setter) {
        if (StringUtils.hasText(value)) {
            setter.accept(value);
        }
    }

    private static void copyIfBlank(java.util.function.Supplier<String> current, java.util.function.Consumer<String> setter, String value) {
        if (!StringUtils.hasText(current.get()) && StringUtils.hasText(value)) {
            setter.accept(value);
        }
    }

    public record Outcome(String result, Long tenantId, Long userId) {
    }

    private enum PaymentKind {
        SUCCESS, FAILED, RECOVERED, REFUNDED
    }

    private record TenantLink(Tenant tenant, User user) {
    }

    private record Resolved(Subscription subscription, String ignored) {
        static Resolved found(Subscription subscription) {
            return new Resolved(subscription, null);
        }

        static Resolved ignored(String reason) {
            return new Resolved(null, reason);
        }
    }
}
