package com.animalin.billing.paddle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PaddleDtos {

    private PaddleDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope<T>(T data, Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListEnvelope<T>(List<T> data, Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Pagination pagination, @JsonProperty("request_id") String requestId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(
            @JsonProperty("per_page") Integer perPage,
            @JsonProperty("next") String next,
            @JsonProperty("has_more") Boolean hasMore
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorEnvelope(PaddleError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaddleError(String type, String code, String detail, @JsonProperty("documentation_url") String documentationUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notification(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("occurred_at") Instant occurredAt,
            @JsonProperty("notification_id") String notificationId,
            JsonNode data
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(
            String id,
            String name,
            String email,
            String status,
            @JsonProperty("custom_data") Map<String, Object> customData,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(
            String id,
            String status,
            @JsonProperty("customer_id") String customerId,
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("first_billed_at") Instant firstBilledAt,
            @JsonProperty("next_billed_at") Instant nextBilledAt,
            @JsonProperty("paused_at") Instant pausedAt,
            @JsonProperty("canceled_at") Instant canceledAt,
            @JsonProperty("billing_cycle") BillingCycle billingCycle,
            @JsonProperty("current_billing_period") BillingPeriod currentBillingPeriod,
            @JsonProperty("scheduled_change") ScheduledChange scheduledChange,
            List<SubscriptionItem> items,
            @JsonProperty("custom_data") Map<String, Object> customData,
            @JsonProperty("transaction_id") String transactionId
    ) {
        public String firstPriceId() {
            if (items == null || items.isEmpty() || items.getFirst().price() == null) {
                return null;
            }
            return items.getFirst().price().id();
        }

        public String firstProductId() {
            if (items == null || items.isEmpty() || items.getFirst().price() == null) {
                return null;
            }
            return items.getFirst().price().productId();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionPreview(
            String id,
            String status,
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("next_billed_at") Instant nextBilledAt,
            @JsonProperty("billing_cycle") BillingCycle billingCycle,
            @JsonProperty("current_billing_period") BillingPeriod currentBillingPeriod,
            @JsonProperty("immediate_transaction") PreviewTransaction immediateTransaction,
            @JsonProperty("next_transaction") PreviewTransaction nextTransaction
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreviewTransaction(
            @JsonProperty("billing_period") BillingPeriod billingPeriod,
            PreviewDetails details
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreviewDetails(PreviewTotals totals) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreviewTotals(
            @JsonProperty("grand_total") String grandTotal,
            @JsonProperty("subtotal") String subtotal,
            @JsonProperty("currency_code") String currencyCode
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionItem(Price price, Integer quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BillingCycle(String interval, Integer frequency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BillingPeriod(
            @JsonProperty("starts_at") Instant startsAt,
            @JsonProperty("ends_at") Instant endsAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduledChange(
            String action,
            @JsonProperty("effective_at") Instant effectiveAt,
            @JsonProperty("resume_at") Instant resumeAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(
            String id,
            String status,
            @JsonProperty("customer_id") String customerId,
            @JsonProperty("subscription_id") String subscriptionId,
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("custom_data") Map<String, Object> customData,
            List<TransactionItem> items,
            @JsonProperty("billed_at") Instant billedAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
        public String firstPriceId() {
            if (items == null || items.isEmpty() || items.getFirst().price() == null) {
                return null;
            }
            return items.getFirst().price().id();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionItem(Price price, Integer quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            String id,
            String name,
            String description,
            String status,
            @JsonProperty("tax_category") String taxCategory,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(
            String id,
            String description,
            String status,
            @JsonProperty("product_id") String productId,
            @JsonProperty("unit_price") UnitPrice unitPrice,
            @JsonProperty("billing_cycle") BillingCycle billingCycle,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UnitPrice(String amount, @JsonProperty("currency_code") String currencyCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortalSession(
            String id,
            @JsonProperty("customer_id") String customerId,
            PortalUrls urls
    ) {
        public String overviewUrl() {
            return urls == null || urls.general() == null ? null : urls.general().overview();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortalUrls(PortalGeneral general) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortalGeneral(String overview) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateProductRequest(String name, String description, @JsonProperty("tax_category") String taxCategory) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateProductRequest(String name, String description, String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreatePriceRequest(
            String description,
            @JsonProperty("product_id") String productId,
            @JsonProperty("unit_price") UnitPrice unitPrice,
            @JsonProperty("billing_cycle") BillingCycle billingCycle
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdatePriceRequest(String status, String description) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CancelSubscriptionRequest(@JsonProperty("effective_from") String effectiveFrom) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateSubscriptionRequest(
            List<UpdateSubscriptionItem> items,
            @JsonProperty("proration_billing_mode") String prorationBillingMode,
            @JsonProperty("custom_data") Map<String, Object> customData
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateSubscriptionItem(@JsonProperty("price_id") String priceId, Integer quantity) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreatePortalSessionRequest(@JsonProperty("subscription_ids") List<String> subscriptionIds) {
    }
}
