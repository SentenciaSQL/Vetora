package com.animalin.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record DashboardResponse(
            long tenants,
            long activeTenants,
            long trialTenants,
            long suspendedTenants,
            long users,
            long owners,
            long pets,
            long appointments,
            Instant from,
            Instant to,
            String range,
            String granularity,
            List<String> countries,
            List<NamedCount> plans,
            List<MetricCard> cards,
            GrowthChart growth,
            List<PlanSlice> subscriptionsByPlan,
            List<StatusSlice> tenantStatuses,
            BillingBlock billing,
            ActivityBlock activity,
            List<AlertItem> alerts,
            List<ActivityItem> recentActivity,
            boolean revenueDataAvailable,
            String revenueNote
    ) {
    }

    public record MetricCard(
            String key,
            String kind,
            long value,
            Double changePercent,
            String trend,
            String unit
    ) {
    }

    public record NamedCount(String code, String name, long count) {
    }

    public record SeriesPoint(String label, Instant start, long value) {
    }

    public record NamedSeries(String key, List<SeriesPoint> points) {
    }

    public record GrowthChart(String granularity, List<String> labels, List<NamedSeries> series) {
    }

    public record PlanSlice(
            String planCode,
            String planName,
            String billingCycle,
            String statusGroup,
            long count,
            double percent,
            BigDecimal estimatedMonthlyRevenue,
            boolean estimated
    ) {
    }

    public record StatusSlice(String status, long count, double percent) {
    }

    public record MoneyMetric(String key, BigDecimal amount, String currency, boolean estimated, boolean available) {
    }

    public record BillingBlock(
            MoneyMetric confirmedRevenue,
            MoneyMetric mrr,
            MoneyMetric arr,
            MoneyMetric averageSubscription,
            long newSubscriptions,
            long renewals,
            long cancellations,
            long failedPayments,
            long recoveredPayments,
            long refunds,
            Double churnRate,
            Double trialConversionRate,
            GrowthChart revenueChart,
            boolean revenueDataAvailable,
            String note
    ) {
    }

    public record ActivityBlock(
            long appointmentsCreated,
            long appointmentsCompleted,
            long appointmentsCancelled,
            long consultations,
            long newPets,
            long newOwners,
            long messages,
            long activeUsers,
            long activeTenants,
            long inactiveTenants,
            GrowthChart chart
    ) {
    }

    public record AlertItem(String key, long count, String href, String severity) {
    }

    public record ActivityItem(
            Instant at,
            String type,
            String tenantName,
            String description,
            String status,
            String href
    ) {
    }
}
