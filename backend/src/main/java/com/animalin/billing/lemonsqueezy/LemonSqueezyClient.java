package com.animalin.billing.lemonsqueezy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class LemonSqueezyClient {

    private static final Logger log = LoggerFactory.getLogger(LemonSqueezyClient.class);
    private static final String API = "https://api.lemonsqueezy.com/v1";
    private static final String JSON_API = "application/vnd.api+json";

    private final LemonSqueezyProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LemonSqueezyClient(LemonSqueezyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(API).build();
    }

    public String createCheckout(String variantId, String email, String name, Map<String, String> custom,
                                 String redirectUrl, boolean skipTrial) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode data = body.putObject("data");
        data.put("type", "checkouts");
        ObjectNode attributes = data.putObject("attributes");
        attributes.put("test_mode", properties.testMode());
        ObjectNode checkoutData = attributes.putObject("checkout_data");
        if (StringUtils.hasText(email)) {
            checkoutData.put("email", email);
        }
        if (StringUtils.hasText(name)) {
            checkoutData.put("name", name);
        }
        ObjectNode customNode = checkoutData.putObject("custom");
        if (custom != null) {
            custom.forEach(customNode::put);
        }
        ObjectNode productOptions = attributes.putObject("product_options");
        if (StringUtils.hasText(redirectUrl)) {
            productOptions.put("redirect_url", redirectUrl);
        }
        ObjectNode checkoutOptions = attributes.putObject("checkout_options");
        checkoutOptions.put("embed", false);
        checkoutOptions.put("skip_trial", skipTrial);
        ObjectNode relationships = data.putObject("relationships");
        relationships.putObject("store").putObject("data").put("type", "stores").put("id", properties.storeId().trim());
        relationships.putObject("variant").putObject("data").put("type", "variants").put("id", variantId.trim());

        JsonNode response = post("/checkouts", body);
        String url = response.path("data").path("attributes").path("url").asText(null);
        if (!StringUtils.hasText(url)) {
            throw new LemonSqueezyApiException(502, "Lemon Squeezy no devolvió la URL de checkout");
        }
        return url;
    }

    public JsonNode getSubscription(String subscriptionId) {
        requireConfigured();
        return get("/subscriptions/" + subscriptionId);
    }

    public JsonNode updateSubscription(String subscriptionId, ObjectNode attributes) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode data = body.putObject("data");
        data.put("type", "subscriptions");
        data.put("id", subscriptionId);
        data.set("attributes", attributes);
        return patch("/subscriptions/" + subscriptionId, body);
    }

    public JsonNode cancelSubscription(String subscriptionId) {
        requireConfigured();
        return delete("/subscriptions/" + subscriptionId);
    }

    public String customerPortalUrl(String subscriptionId) {
        JsonNode attributes = getSubscription(subscriptionId).path("data").path("attributes");
        String portal = attributes.path("urls").path("customer_portal").asText(null);
        if (!StringUtils.hasText(portal)) {
            throw new LemonSqueezyApiException(502, "Lemon Squeezy no devolvió el portal del cliente");
        }
        return portal;
    }

    public String updatePaymentMethodUrl(String subscriptionId) {
        return getSubscription(subscriptionId).path("data").path("attributes").path("urls").path("update_payment_method").asText(null);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new LemonSqueezyApiException(0, "Lemon Squeezy no está configurado");
        }
    }

    private JsonNode get(String path) {
        return exchange("GET", path, null);
    }

    private JsonNode post(String path, JsonNode body) {
        return exchange("POST", path, body);
    }

    private JsonNode patch(String path, JsonNode body) {
        return exchange("PATCH", path, body);
    }

    private JsonNode delete(String path) {
        return exchange("DELETE", path, null);
    }

    private JsonNode exchange(String method, String path, JsonNode body) {
        try {
            RestClient.RequestBodySpec request = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .header("Authorization", "Bearer " + properties.apiKey().trim())
                    .header("Accept", JSON_API)
                    .header("Content-Type", JSON_API);
            String raw = body == null
                    ? request.retrieve().body(String.class)
                    : request.contentType(MediaType.parseMediaType(JSON_API)).body(body.toString()).retrieve().body(String.class);
            if (!StringUtils.hasText(raw)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (RestClientResponseException ex) {
            String detail = errorDetail(ex.getResponseBodyAsString());
            log.warn("Lemon Squeezy API failed method={} path={} status={}", method, path, ex.getStatusCode().value());
            throw new LemonSqueezyApiException(ex.getStatusCode().value(), detail);
        } catch (LemonSqueezyApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Lemon Squeezy API failed method={} path={} error={}", method, path, ex.getClass().getSimpleName());
            throw new LemonSqueezyApiException(502, "No se pudo contactar a Lemon Squeezy");
        }
    }

    private String errorDetail(String body) {
        if (!StringUtils.hasText(body)) {
            return "Lemon Squeezy rechazó la operación";
        }
        try {
            JsonNode errors = objectMapper.readTree(body).path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                String detail = errors.get(0).path("detail").asText(null);
                if (StringUtils.hasText(detail) && !containsSecret(detail)) {
                    return detail;
                }
            }
        } catch (Exception ignored) {
            return "Lemon Squeezy rechazó la operación";
        }
        return "Lemon Squeezy rechazó la operación";
    }

    private static boolean containsSecret(String detail) {
        String lower = detail.toLowerCase();
        return lower.contains("api key") || lower.contains("token") || lower.contains("secret") || lower.contains("bearer");
    }
}
