package com.animalin.tenant;

import com.animalin.plan.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(nullable = false, length = 20)
    private String status = "TRIAL";

    @Column(nullable = false)
    private boolean trial = true;

    @Column(name = "paddle_customer_id", length = 64)
    private String paddleCustomerId;

    @Column(name = "paddle_subscription_id", length = 64)
    private String paddleSubscriptionId;

    @Column(name = "paddle_transaction_id", length = 64)
    private String paddleTransactionId;

    @Column(name = "paddle_product_id", length = 64)
    private String paddleProductId;

    @Column(name = "paddle_price_id", length = 64)
    private String paddlePriceId;

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle;

    @Column(nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "current_period_starts_at")
    private Instant currentPeriodStartsAt;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "next_billing_at")
    private Instant nextBillingAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "last_payment_succeeded_at")
    private Instant lastPaymentSucceededAt;

    @Column(name = "first_payment_failed_at")
    private Instant firstPaymentFailedAt;

    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "scheduled_change_action", length = 40)
    private String scheduledChangeAction;

    @Column(name = "scheduled_change_effective_at")
    private Instant scheduledChangeEffectiveAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_plan_id")
    private Plan pendingPlan;

    @Column(name = "pending_price_id", length = 64)
    private String pendingPriceId;

    @Column(name = "pending_billing_interval", length = 20)
    private String pendingBillingInterval;

    @Column(name = "pending_change_effective_at")
    private Instant pendingChangeEffectiveAt;

    @Column(name = "pending_change_created_at")
    private Instant pendingChangeCreatedAt;

    @Column(name = "pending_change_status", length = 20)
    private String pendingChangeStatus;

    @Column(name = "pending_change_paddle_updated_at")
    private Instant pendingChangePaddleUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (startedAt == null) {
            startedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Tenant getTenant() {
        return tenant;
    }
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }
    public Plan getPlan() {
        return plan;
    }
    public void setPlan(Plan plan) {
        this.plan = plan;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public boolean isTrial() {
        return trial;
    }
    public void setTrial(boolean trial) {
        this.trial = trial;
    }
    public String getPaddleCustomerId() {
        return paddleCustomerId;
    }
    public void setPaddleCustomerId(String paddleCustomerId) {
        this.paddleCustomerId = paddleCustomerId;
    }
    public String getPaddleSubscriptionId() {
        return paddleSubscriptionId;
    }
    public void setPaddleSubscriptionId(String paddleSubscriptionId) {
        this.paddleSubscriptionId = paddleSubscriptionId;
    }
    public String getPaddleTransactionId() {
        return paddleTransactionId;
    }
    public void setPaddleTransactionId(String paddleTransactionId) {
        this.paddleTransactionId = paddleTransactionId;
    }
    public String getPaddleProductId() {
        return paddleProductId;
    }
    public void setPaddleProductId(String paddleProductId) {
        this.paddleProductId = paddleProductId;
    }
    public String getPaddlePriceId() {
        return paddlePriceId;
    }
    public void setPaddlePriceId(String paddlePriceId) {
        this.paddlePriceId = paddlePriceId;
    }
    public String getBillingCycle() {
        return billingCycle;
    }
    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }
    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    public Instant getStartedAt() {
        return startedAt;
    }
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }
    public Instant getCurrentPeriodStartsAt() {
        return currentPeriodStartsAt;
    }
    public void setCurrentPeriodStartsAt(Instant currentPeriodStartsAt) {
        this.currentPeriodStartsAt = currentPeriodStartsAt;
    }
    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }
    public Instant getCurrentPeriodEndsAt() {
        return currentPeriodEnd;
    }
    public void setCurrentPeriodEndsAt(Instant currentPeriodEndsAt) {
        this.currentPeriodEnd = currentPeriodEndsAt;
    }
    public Instant getNextBillingAt() {
        return nextBillingAt;
    }
    public void setNextBillingAt(Instant nextBillingAt) {
        this.nextBillingAt = nextBillingAt;
    }
    public Instant getCancelledAt() {
        return cancelledAt;
    }
    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
    public Instant getCanceledAt() {
        return cancelledAt;
    }
    public void setCanceledAt(Instant canceledAt) {
        this.cancelledAt = canceledAt;
    }
    public Instant getLastPaymentSucceededAt() {
        return lastPaymentSucceededAt;
    }
    public void setLastPaymentSucceededAt(Instant lastPaymentSucceededAt) {
        this.lastPaymentSucceededAt = lastPaymentSucceededAt;
    }
    public Instant getFirstPaymentFailedAt() {
        return firstPaymentFailedAt;
    }
    public void setFirstPaymentFailedAt(Instant firstPaymentFailedAt) {
        this.firstPaymentFailedAt = firstPaymentFailedAt;
    }
    public Instant getGracePeriodEndsAt() {
        return gracePeriodEndsAt;
    }
    public void setGracePeriodEndsAt(Instant gracePeriodEndsAt) {
        this.gracePeriodEndsAt = gracePeriodEndsAt;
    }
    public Instant getSuspendedAt() {
        return suspendedAt;
    }
    public void setSuspendedAt(Instant suspendedAt) {
        this.suspendedAt = suspendedAt;
    }
    public String getScheduledChangeAction() {
        return scheduledChangeAction;
    }
    public void setScheduledChangeAction(String scheduledChangeAction) {
        this.scheduledChangeAction = scheduledChangeAction;
    }
    public Instant getScheduledChangeEffectiveAt() {
        return scheduledChangeEffectiveAt;
    }
    public void setScheduledChangeEffectiveAt(Instant scheduledChangeEffectiveAt) {
        this.scheduledChangeEffectiveAt = scheduledChangeEffectiveAt;
    }
    public Plan getPendingPlan() {
        return pendingPlan;
    }
    public void setPendingPlan(Plan pendingPlan) {
        this.pendingPlan = pendingPlan;
    }
    public String getPendingPriceId() {
        return pendingPriceId;
    }
    public void setPendingPriceId(String pendingPriceId) {
        this.pendingPriceId = pendingPriceId;
    }
    public String getPendingBillingInterval() {
        return pendingBillingInterval;
    }
    public void setPendingBillingInterval(String pendingBillingInterval) {
        this.pendingBillingInterval = pendingBillingInterval;
    }
    public Instant getPendingChangeEffectiveAt() {
        return pendingChangeEffectiveAt;
    }
    public void setPendingChangeEffectiveAt(Instant pendingChangeEffectiveAt) {
        this.pendingChangeEffectiveAt = pendingChangeEffectiveAt;
    }
    public Instant getPendingChangeCreatedAt() {
        return pendingChangeCreatedAt;
    }
    public void setPendingChangeCreatedAt(Instant pendingChangeCreatedAt) {
        this.pendingChangeCreatedAt = pendingChangeCreatedAt;
    }
    public String getPendingChangeStatus() {
        return pendingChangeStatus;
    }
    public void setPendingChangeStatus(String pendingChangeStatus) {
        this.pendingChangeStatus = pendingChangeStatus;
    }
    public Instant getPendingChangePaddleUpdatedAt() {
        return pendingChangePaddleUpdatedAt;
    }
    public void setPendingChangePaddleUpdatedAt(Instant pendingChangePaddleUpdatedAt) {
        this.pendingChangePaddleUpdatedAt = pendingChangePaddleUpdatedAt;
    }
    public boolean hasPendingPlanChange() {
        return pendingPlan != null || (pendingPriceId != null && !pendingPriceId.isBlank());
    }
    public void clearPendingPlanChange() {
        this.pendingPlan = null;
        this.pendingPriceId = null;
        this.pendingBillingInterval = null;
        this.pendingChangeEffectiveAt = null;
        this.pendingChangeCreatedAt = null;
        this.pendingChangeStatus = null;
        this.pendingChangePaddleUpdatedAt = null;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
