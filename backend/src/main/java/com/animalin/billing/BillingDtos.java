package com.animalin.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
            String paddleMonthlyPriceId,
            String paddleAnnualPriceId,
            boolean enabled,
            PlanLimits limits
    ) {
    }

    public record BillingConfigResponse(
            String environment,
            String clientToken,
            int gracePeriodDays,
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
            boolean hasPaddleCustomer,
            boolean trialAccess,
            PlanLimits limits,
            PlanUsage usage
    ) {
    }

    public record CheckoutRequest(String priceId, String billingCycle) {
    }

    public record CheckoutResponse(
            String environment,
            String clientToken,
            String priceId,
            String billingCycle,
            Map<String, String> customData,
            String customerEmail,
            String locale
    ) {
    }

    public record PortalResponse(String url) {
    }

    public record CancelSubscriptionRequest(String effectiveFrom) {
    }

    public record ChangePlanRequest(String priceId) {
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
            String paddleProductId,
            String paddleMonthlyPriceId,
            String paddleAnnualPriceId,
            boolean active,
            PlanLimits limits,
            long subscriberCount,
            Instant createdAt,
            Instant updatedAt,
            String paddleMonthlyPriceStatus,
            String paddleAnnualPriceStatus,
            Instant paddleLastSyncedAt,
            String paddleSyncStatus
    ) {
    }

    public record PaddlePriceDiff(
            String cycle,
            String priceId,
            BigDecimal localAmount,
            BigDecimal paddleAmount,
            String currency,
            String interval,
            String status,
            boolean matches
    ) {
    }

    public record PaddleSyncResult(
            AdminPlanResponse plan,
            List<PaddlePriceDiff> differences,
            boolean inSync,
            String environment
    ) {
    }

    public record RotatePriceRequest(String cycle, BigDecimal amount, Boolean confirm) {
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
            Boolean syncToPaddle,
            Integer maxUsers,
            Integer maxVeterinarians,
            Integer maxBranches,
            Integer maxStorageMb,
            Integer maxMessagesMonth,
            Boolean reportsEnabled,
            Boolean messagingEnabled,
            Boolean laboratoryEnabled,
            Boolean active,
            String paddleProductId,
            String paddleMonthlyPriceId,
            String paddleAnnualPriceId
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
            Boolean migratePrice,
            String paddleProductId,
            String paddleMonthlyPriceId,
            String paddleAnnualPriceId,
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
