package com.animalin.billing;

import com.animalin.audit.AuditService;
import com.animalin.billing.paddle.PaddleApiException;
import com.animalin.billing.paddle.PaddleClient;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.common.exception.ApiException;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PendingPlanChangeService {

    private static final Logger log = LoggerFactory.getLogger(PendingPlanChangeService.class);
    static final Duration PADDLE_APPLY_LEAD = Duration.ofHours(2);
    static final Duration RETRY_AFTER = Duration.ofMinutes(10);
    static final String PRORATION_NO_CHARGE = "do_not_bill";

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PaddleClient paddleClient;
    private final AuditService auditService;
    private final Clock clock;

    public PendingPlanChangeService(SubscriptionRepository subscriptionRepository,
                                    PlanRepository planRepository,
                                    PaddleClient paddleClient,
                                    AuditService auditService,
                                    Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.paddleClient = paddleClient;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> dueSubscriptionIds() {
        return subscriptionRepository.findDuePendingChangeIds(clock.instant().plus(PADDLE_APPLY_LEAD));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyDueById(Long subscriptionId) {
        if (subscriptionId == null) {
            return;
        }
        Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (subscription == null) {
            return;
        }
        applyDue(subscription);
    }

    @Transactional
    public void reconcileAfterPaddleSync(Subscription subscription) {
        if (subscription == null || !subscription.hasPendingPlanChange()) {
            return;
        }
        completeLocalIfDue(subscription, currentPaddlePrice(subscription));
        if (subscription.hasPendingPlanChange()) {
            applyDue(subscription);
        }
    }

    public List<PaddleDtos.UpdateSubscriptionItem> itemsForPlanChange(Subscription subscription, String newPriceId) {
        if (!StringUtils.hasText(newPriceId)) {
            throw ApiException.badRequest("Debe indicar el Price ID de destino");
        }
        if (subscription == null || !StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
            return List.of(new PaddleDtos.UpdateSubscriptionItem(newPriceId, 1));
        }
        try {
            PaddleDtos.Subscription paddle = paddleClient.getSubscription(subscription.getPaddleSubscriptionId());
            return replacePlanItem(paddle, subscription.getPaddlePriceId(), newPriceId);
        } catch (PaddleApiException ex) {
            log.warn("Unable to load Paddle items method={} path={} status={} paddleCode={} paddleDetail={} paddleSub={} targetPrice={}",
                    ex.getHttpMethod(), ex.getPath(), ex.getStatus(), ex.getPaddleErrorCode(), ex.getMessage(),
                    TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                    TrialPolicy.maskPaddleId(newPriceId));
            return List.of(new PaddleDtos.UpdateSubscriptionItem(newPriceId, 1));
        }
    }

    private void applyDue(Subscription subscription) {
        if (!subscription.hasPendingPlanChange()) {
            return;
        }
        Instant now = clock.instant();
        Instant effective = subscription.getPendingChangeEffectiveAt();
        if (effective == null) {
            return;
        }
        String status = subscription.getStatus();
        if (!SubscriptionStatuses.ACTIVE.equals(status)
                && !SubscriptionStatuses.TRIALING.equals(status)
                && !SubscriptionStatuses.TRIAL.equals(status)) {
            log.info("Skipping pending plan change subscriptionId={} status={}", subscription.getId(), status);
            return;
        }
        if (!StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
            log.warn("Pending plan change has no Paddle subscription id={}", subscription.getId());
            return;
        }

        boolean paddleWindow = !now.isBefore(effective.minus(PADDLE_APPLY_LEAD));
        String paddlePrice = currentPaddlePrice(subscription);
        boolean paddleAlreadyNew = subscription.getPendingPriceId().equals(paddlePrice);

        if (paddleAlreadyNew) {
            markPaddleUpdated(subscription, now);
            completeLocalIfDue(subscription, paddlePrice);
            return;
        }
        if (!paddleWindow) {
            return;
        }
        if (PlanChangeType.PENDING_APPLYING.equals(subscription.getPendingChangeStatus())
                && subscription.getPendingChangePaddleUpdatedAt() != null
                && subscription.getPendingChangePaddleUpdatedAt().isAfter(now.minus(RETRY_AFTER))) {
            completeLocalIfDue(subscription, paddlePrice);
            return;
        }
        patchPaddle(subscription, now);
        completeLocalIfDue(subscription, currentPaddlePrice(subscription));
    }

    private void patchPaddle(Subscription subscription, Instant now) {
        String targetPrice = subscription.getPendingPriceId();
        List<PaddleDtos.UpdateSubscriptionItem> items = itemsForPlanChange(subscription, targetPrice);
        Long tenantId = subscription.getTenant() == null ? null : subscription.getTenant().getId();
        PaddleDtos.UpdateSubscriptionRequest request = new PaddleDtos.UpdateSubscriptionRequest(
                items,
                PRORATION_NO_CHARGE,
                tenantId == null ? null : Map.of("tenant_id", String.valueOf(tenantId))
        );
        try {
            PaddleDtos.Subscription updated = paddleClient.updateSubscription(
                    subscription.getPaddleSubscriptionId(), request);
            markPaddleUpdated(subscription, now);
            String updatedPrice = updated == null ? null : updated.firstPriceId();
            log.info("Applied pending plan change in Paddle tenantId={} paddleSub={} targetPrice={} resultPrice={} proration={}",
                    tenantId,
                    TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                    TrialPolicy.maskPaddleId(targetPrice),
                    TrialPolicy.maskPaddleId(updatedPrice),
                    PRORATION_NO_CHARGE);
            completeLocalIfDue(subscription, updatedPrice);
        } catch (PaddleApiException ex) {
            log.warn("Paddle pending-change failed method={} path={} status={} paddleType={} paddleCode={} paddleDetail={} tenantId={} paddleSub={} targetPrice={} proration={} subscriptionStatus={}",
                    ex.getHttpMethod(),
                    ex.getPath(),
                    ex.getStatus(),
                    ex.getPaddleErrorType(),
                    ex.getPaddleErrorCode(),
                    ex.getMessage(),
                    tenantId,
                    TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                    TrialPolicy.maskPaddleId(targetPrice),
                    PRORATION_NO_CHARGE,
                    subscription.getStatus());
            throw ex;
        }
    }

    private void completeLocalIfDue(Subscription subscription, String paddlePriceId) {
        if (!subscription.hasPendingPlanChange()) {
            return;
        }
        Instant now = clock.instant();
        Instant effective = subscription.getPendingChangeEffectiveAt();
        if (effective != null && now.isBefore(effective)) {
            return;
        }
        if (!StringUtils.hasText(paddlePriceId) || !paddlePriceId.equals(subscription.getPendingPriceId())) {
            return;
        }
        Plan pending = subscription.getPendingPlan();
        if (pending == null) {
            return;
        }
        subscription.setPlan(pending);
        if (subscription.getTenant() != null) {
            subscription.getTenant().setPlan(pending);
        }
        subscription.setPaddlePriceId(subscription.getPendingPriceId());
        if (StringUtils.hasText(subscription.getPendingBillingInterval())) {
            subscription.setBillingCycle(subscription.getPendingBillingInterval());
        }
        if (StringUtils.hasText(pending.getPaddleProductId())) {
            subscription.setPaddleProductId(pending.getPaddleProductId());
        }
        Long tenantId = subscription.getTenant() == null ? null : subscription.getTenant().getId();
        auditService.record(tenantId, null, "billing", "APPLY_PENDING_PLAN", "SUBSCRIPTION",
                subscription.getId(),
                "Pending plan " + pending.getCode() + " applied at next billing cycle",
                PlanChangeType.PENDING_APPLYING, pending.getCode());
        subscription.clearPendingPlanChange();
    }

    private void markPaddleUpdated(Subscription subscription, Instant now) {
        subscription.setPendingChangeStatus(PlanChangeType.PENDING_APPLYING);
        if (subscription.getPendingChangePaddleUpdatedAt() == null) {
            subscription.setPendingChangePaddleUpdatedAt(now);
        }
    }

    private String currentPaddlePrice(Subscription subscription) {
        if (!StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
            return subscription.getPaddlePriceId();
        }
        try {
            PaddleDtos.Subscription paddle = paddleClient.getSubscription(subscription.getPaddleSubscriptionId());
            if (paddle == null || paddle.items() == null) {
                return paddle == null ? subscription.getPaddlePriceId() : paddle.firstPriceId();
            }
            for (PaddleDtos.SubscriptionItem item : paddle.items()) {
                String itemPrice = item.price() == null ? null : item.price().id();
                if (itemPrice != null && itemPrice.equals(subscription.getPendingPriceId())) {
                    return itemPrice;
                }
            }
            for (PaddleDtos.SubscriptionItem item : paddle.items()) {
                String itemPrice = item.price() == null ? null : item.price().id();
                if (itemPrice != null && itemPrice.equals(subscription.getPaddlePriceId())) {
                    return itemPrice;
                }
            }
            return paddle.firstPriceId();
        } catch (PaddleApiException ex) {
            log.warn("Unable to read Paddle subscription method={} path={} status={} paddleCode={} paddleSub={}",
                    ex.getHttpMethod(), ex.getPath(), ex.getStatus(), ex.getPaddleErrorCode(),
                    TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()));
        }
        return subscription.getPaddlePriceId();
    }

    List<PaddleDtos.UpdateSubscriptionItem> replacePlanItem(PaddleDtos.Subscription paddle,
                                                            String currentPriceId,
                                                            String newPriceId) {
        List<PaddleDtos.UpdateSubscriptionItem> items = new ArrayList<>();
        boolean replaced = false;
        Set<String> planPriceIds = planPriceIds();
        if (paddle != null && paddle.items() != null) {
            for (PaddleDtos.SubscriptionItem item : paddle.items()) {
                String itemPrice = item.price() == null ? null : item.price().id();
                if (!StringUtils.hasText(itemPrice)) {
                    continue;
                }
                boolean planItem = itemPrice.equals(currentPriceId) || planPriceIds.contains(itemPrice);
                if (planItem && !replaced) {
                    items.add(new PaddleDtos.UpdateSubscriptionItem(newPriceId, item.quantity() == null ? 1 : item.quantity()));
                    replaced = true;
                } else if (!planItem) {
                    items.add(new PaddleDtos.UpdateSubscriptionItem(itemPrice, item.quantity()));
                }
            }
        }
        if (!replaced) {
            items.add(new PaddleDtos.UpdateSubscriptionItem(newPriceId, 1));
        }
        return items;
    }

    private Set<String> planPriceIds() {
        Set<String> ids = new HashSet<>();
        for (Plan plan : planRepository.findAll()) {
            if (StringUtils.hasText(plan.getPaddleMonthlyPriceId())) {
                ids.add(plan.getPaddleMonthlyPriceId());
            }
            if (StringUtils.hasText(plan.getPaddleAnnualPriceId())) {
                ids.add(plan.getPaddleAnnualPriceId());
            }
        }
        return ids;
    }
}
