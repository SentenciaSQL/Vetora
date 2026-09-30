package com.animalin.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {
    }

    public record PlanLimits(
            int maxUsers,
            int maxVeterinarians,
            int maxBranches,
            int maxStorageMb,
            int maxMessagesMonth,
            boolean reportsEnabled,
            boolean messagingEnabled,
            boolean laboratoryEnabled
    ) {
    }

    public record PlanResponse(
            Long id,
            String code,
            String name,
            String nameEs,
            String nameEn,
            String description,
            String descriptionEs,
            String descriptionEn,
            String currency,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            BigDecimal monthlyEquivalent,
            BigDecimal savingsPercent,
            boolean monthlyAvailable,
            boolean annualAvailable,
            boolean enabled,
            PlanLimits limits,
            int monthlyTrialDays
    ) {
    }

    public record BillingConfigResponse(
            String environment,
            int trialDays,
            List<PlanResponse> plans
    ) {
    }

    public record SubscriptionResponse(
            Long id,
            Long tenantId,
            Long planId,
            String planCode,
            String planName,
            String status,
            String billingCycle,
            String currency,
            String productId,
            String variantId,
            boolean trial,
            Instant startedAt,
            Instant currentPeriodStartsAt,
            Instant currentPeriodEndsAt,
            Instant nextBillingAt,
            Instant canceledAt,
            Instant lastPaymentSucceededAt,
            Instant firstPaymentFailedAt,
            Instant gracePeriodEndsAt,
            Instant suspendedAt,
            String scheduledChangeAction,
            Instant scheduledChangeEffectiveAt,
            boolean accessGranted,
            boolean gracePeriod,
            boolean suspended,
            boolean hasCustomer,
            boolean hasSubscription,
            boolean trialAccess,
            PlanLimits limits,
            PlanUsage usage,
            Long pendingPlanId,
            String pendingPlanCode,
            String pendingPlanName,
            String pendingPriceId,
            String pendingBillingInterval,
            Instant pendingChangeEffectiveAt,
            Instant pendingChangeCreatedAt,
            String pendingChangeStatus,
            String pendingChangeMessage,
            boolean cancelled,
            boolean paused,
            Boolean testMode,
            Instant endsAt,
            Instant renewsAt,
            Instant trialEndsAt
    ) {
    }

    public record CheckoutRequest(Long planId, String billingCycle) {
    }

    public record CheckoutResponse(String url, String billingCycle) {
    }

    public record PortalResponse(String url) {
    }

    public record CancelSubscriptionRequest(String effectiveFrom) {
    }

    public record ChangePlanRequest(Long planId, String billingCycle) {
    }

    public record ChangePreviewResponse(
            Long currentPlanId,
            String currentPlanCode,
            String currentPlanName,
            String currentCycle,
            Long newPlanId,
            String newPlanCode,
            String newPlanName,
            String newCycle,
            BigDecimal estimatedAmount,
            String currency,
            Instant nextBillingAt,
            String prorationMode,
            String currentPlan,
            String newPlan,
            String changeType,
            Instant effectiveAt,
            BigDecimal immediateCharge,
            BigDecimal credit,
            String billingInterval,
            String message
    ) {
    }

    public record AdminPlanResponse(
            Long id,
            String code,
            String nameEs,
            String nameEn,
            String descriptionEs,
            String descriptionEn,
            String currency,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            boolean active,
            PlanLimits limits,
            long subscriberCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record UsageMetric(long current, int limit) {
    }

    public record PlanUsage(
            UsageMetric users,
            UsageMetric veterinarians,
            UsageMetric branches,
            UsageMetric storageMb,
            UsageMetric messagesMonth
    ) {
    }

    public record CreatePlanRequest(
            String code,
            String nameEs,
            String nameEn,
            String descriptionEs,
            String descriptionEn,
            String currency,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            Integer maxUsers,
            Integer maxVeterinarians,
            Integer maxBranches,
            Integer maxStorageMb,
            Integer maxMessagesMonth,
            Boolean reportsEnabled,
            Boolean messagingEnabled,
            Boolean laboratoryEnabled,
            Boolean active
    ) {
    }

    public record UpdatePlanRequest(
            String nameEs,
            String nameEn,
            String descriptionEs,
            String descriptionEn,
            String currency,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            Integer maxUsers,
            Integer maxVeterinarians,
            Integer maxBranches,
            Integer maxStorageMb,
            Integer maxMessagesMonth,
            Boolean reportsEnabled,
            Boolean messagingEnabled,
            Boolean laboratoryEnabled,
            Boolean active
    ) {
    }
}
