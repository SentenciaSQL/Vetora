package com.animalin.billing.lemonsqueezy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * View over a Lemon Squeezy webhook. Attributes differ by event, so callers
 * read fields defensively instead of binding a single fixed schema.
 */
public final class LemonSqueezyWebhookPayload {

    private final String eventName;
    private final String resourceType;
    private final String resourceId;
    private final boolean testMode;
    private final Long userId;
    private final Long tenantId;
    private final Instant updatedAt;
    private final Instant createdAt;
    private final JsonNode attributes;

    private LemonSqueezyWebhookPayload(String eventName, String resourceType, String resourceId, boolean testMode,
                                       Long userId, Long tenantId, Instant updatedAt, Instant createdAt,
                                       JsonNode attributes) {
        this.eventName = eventName;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.testMode = testMode;
        this.userId = userId;
        this.tenantId = tenantId;
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
        this.attributes = attributes == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : attributes;
    }

    public static LemonSqueezyWebhookPayload parse(String rawBody, String headerEventName, ObjectMapper objectMapper) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new LemonSqueezyPayloadException("Malformed webhook payload");
        }
        if (root == null || !root.isObject()) {
            throw new LemonSqueezyPayloadException("Malformed webhook payload");
        }
        JsonNode meta = root.get("meta");
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new LemonSqueezyPayloadException("Webhook payload is missing data");
        }
        String eventName = text(meta, "event_name");
        if (!StringUtils.hasText(eventName) && StringUtils.hasText(headerEventName)) {
            eventName = headerEventName.trim();
        }
        if (!StringUtils.hasText(eventName)) {
            throw new LemonSqueezyPayloadException("Webhook payload is missing event_name");
        }
        String resourceType = text(data, "type");
        String resourceId = text(data, "id");
        if (!StringUtils.hasText(resourceType) || !StringUtils.hasText(resourceId)) {
            throw new LemonSqueezyPayloadException("Webhook payload is missing the resource");
        }
        JsonNode attributes = data.get("attributes");
        if (attributes == null || !attributes.isObject()) {
            attributes = com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        JsonNode customData = meta == null ? null : meta.get("custom_data");
        boolean testMode = readTestMode(meta, attributes);
        return new LemonSqueezyWebhookPayload(
                eventName.trim().toLowerCase(),
                resourceType.trim(),
                resourceId.trim(),
                testMode,
                readId(customData, "user_id", "userId"),
                readId(customData, "tenant_id", "tenantId"),
                instant(attributes, "updated_at"),
                instant(attributes, "created_at"),
                attributes
        );
    }

    public String eventName() {
        return eventName;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public boolean testMode() {
        return testMode;
    }

    public Long userId() {
        return userId;
    }

    public Long tenantId() {
        return tenantId;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String subscriptionId() {
        if ("subscriptions".equals(resourceType)) {
            return resourceId;
        }
        return attr("subscription_id");
    }

    public String attr(String field) {
        return text(attributes, field);
    }

    public String childAttr(String objectField, String field) {
        JsonNode child = attributes.get(objectField);
        if (child == null || !child.isObject()) {
            return null;
        }
        return text(child, field);
    }

    public Instant attrInstant(String field) {
        return instant(attributes, field);
    }

    public boolean attrBoolean(String field) {
        JsonNode value = attributes.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return "true".equalsIgnoreCase(value.asText());
    }

    public boolean pausePresent() {
        JsonNode pause = attributes.get("pause");
        return pause != null && !pause.isNull() && !pause.isMissingNode();
    }

    private static boolean readTestMode(JsonNode meta, JsonNode attributes) {
        if (attributes != null && attributes.has("test_mode") && !attributes.get("test_mode").isNull()) {
            JsonNode value = attributes.get("test_mode");
            if (value.isBoolean()) {
                return value.booleanValue();
            }
            return "true".equalsIgnoreCase(value.asText());
        }
        if (meta != null && meta.has("test_mode") && !meta.get("test_mode").isNull()) {
            JsonNode value = meta.get("test_mode");
            if (value.isBoolean()) {
                return value.booleanValue();
            }
            return "true".equalsIgnoreCase(value.asText());
        }
        return false;
    }

    private static Long readId(JsonNode customData, String... keys) {
        if (customData == null || customData.isNull() || !customData.isObject()) {
            return null;
        }
        for (String key : keys) {
            if (!customData.has(key) || customData.get(key).isNull()) {
                continue;
            }
            Long value = asLong(customData.get(key));
            if (value == null) {
                throw new LemonSqueezyPayloadException("Invalid custom_data." + key);
            }
            return value;
        }
        return null;
    }

    private static Long asLong(JsonNode value) {
        try {
            if (value.isIntegralNumber()) {
                return value.longValue();
            }
            if (value.isTextual()) {
                String text = value.asText().trim();
                if (!StringUtils.hasText(text)) {
                    return null;
                }
                return Long.parseLong(text);
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Instant instant(JsonNode node, String field) {
        String raw = text(node, field);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.bigIntegerValue().toString();
        }
        if (value.isNumber() || value.isTextual()) {
            String text = value.asText();
            if (text == null) {
                return null;
            }
            text = text.trim();
            return text.isEmpty() ? null : text;
        }
        return null;
    }
}
