package com.animalin.billing.paddle;

import com.animalin.billing.PaddleProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class PaddleApiClient implements PaddleClient {

    private static final Logger log = LoggerFactory.getLogger(PaddleApiClient.class);

    private final PaddleProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public PaddleApiClient(PaddleProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.builder().baseUrl(properties.apiBaseUrl()).build());
    }

    public PaddleApiClient(PaddleProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public PaddleDtos.Customer getCustomer(String customerId) {
        return get("/customers/" + customerId, PaddleDtos.Customer.class);
    }

    @Override
    public List<PaddleDtos.Customer> listCustomers() {
        return list("/customers", PaddleDtos.Customer.class);
    }

    @Override
    public PaddleDtos.Subscription getSubscription(String subscriptionId) {
        return get("/subscriptions/" + subscriptionId, PaddleDtos.Subscription.class);
    }

    @Override
    public List<PaddleDtos.Subscription> listSubscriptions() {
        return list("/subscriptions", PaddleDtos.Subscription.class);
    }

    @Override
    public PaddleDtos.Subscription updateSubscription(String subscriptionId, PaddleDtos.UpdateSubscriptionRequest request) {
        return patch("/subscriptions/" + subscriptionId, request, PaddleDtos.Subscription.class);
    }

    @Override
    public PaddleDtos.SubscriptionPreview previewSubscriptionUpdate(String subscriptionId, PaddleDtos.UpdateSubscriptionRequest request) {
        return post("/subscriptions/" + subscriptionId + "/preview", request, PaddleDtos.SubscriptionPreview.class);
    }

    @Override
    public PaddleDtos.Subscription cancelSubscription(String subscriptionId, PaddleDtos.CancelSubscriptionRequest request) {
        return post("/subscriptions/" + subscriptionId + "/cancel", request, PaddleDtos.Subscription.class);
    }

    @Override
    public PaddleDtos.Transaction getTransaction(String transactionId) {
        return get("/transactions/" + transactionId, PaddleDtos.Transaction.class);
    }

    @Override
    public List<PaddleDtos.Transaction> listTransactions() {
        return list("/transactions", PaddleDtos.Transaction.class);
    }

    @Override
    public PaddleDtos.Product getProduct(String productId) {
        return get("/products/" + productId, PaddleDtos.Product.class);
    }

    @Override
    public List<PaddleDtos.Product> listProducts() {
        return list("/products", PaddleDtos.Product.class);
    }

    @Override
    public PaddleDtos.Product createProduct(PaddleDtos.CreateProductRequest request) {
        return post("/products", request, PaddleDtos.Product.class);
    }

    @Override
    public PaddleDtos.Product updateProduct(String productId, PaddleDtos.UpdateProductRequest request) {
        return patch("/products/" + productId, request, PaddleDtos.Product.class);
    }

    @Override
    public PaddleDtos.Price getPrice(String priceId) {
        return get("/prices/" + priceId, PaddleDtos.Price.class);
    }

    @Override
    public List<PaddleDtos.Price> listPrices() {
        return list("/prices", PaddleDtos.Price.class);
    }

    @Override
    public PaddleDtos.Price createPrice(PaddleDtos.CreatePriceRequest request) {
        return post("/prices", request, PaddleDtos.Price.class);
    }

    @Override
    public PaddleDtos.Price updatePrice(String priceId, PaddleDtos.UpdatePriceRequest request) {
        return patch("/prices/" + priceId, request, PaddleDtos.Price.class);
    }

    @Override
    public PaddleDtos.PortalSession createCustomerPortalSession(String customerId, PaddleDtos.CreatePortalSessionRequest request) {
        return post("/customers/" + customerId + "/portal-sessions", request, PaddleDtos.PortalSession.class);
    }

    private <T> T get(String path, Class<T> type) {
        return exchange(HttpMethod.GET, path, null, type);
    }

    private <T> List<T> list(String path, Class<T> type) {
        ensureConfigured();
        try {
            String json = restClient.get()
                    .uri(path)
                    .headers(this::secureHeaders)
                    .retrieve()
                    .onStatus(status -> status.isError(), this::onError)
                    .body(String.class);
            JavaType listType = objectMapper.getTypeFactory().constructParametricType(PaddleDtos.ListEnvelope.class, type);
            PaddleDtos.ListEnvelope<T> envelope = objectMapper.readValue(json, listType);
            return envelope == null || envelope.data() == null ? List.of() : envelope.data();
        } catch (PaddleApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw toApiException(ex);
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.POST, path, body, type);
    }

    private <T> T patch(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.PATCH, path, body, type);
    }

    private <T> T exchange(HttpMethod method, String path, Object body, Class<T> type) {
        ensureConfigured();
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(path)
                    .headers(this::secureHeaders);
            if (body != null) {
                spec = spec.body(body);
            }
            String json = spec.retrieve()
                    .onStatus(status -> status.isError(), this::onError)
                    .body(String.class);
            JavaType envelopeType = objectMapper.getTypeFactory().constructParametricType(PaddleDtos.Envelope.class, type);
            PaddleDtos.Envelope<T> envelope = objectMapper.readValue(json, envelopeType);
            return envelope == null ? null : envelope.data();
        } catch (PaddleApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw toApiException(ex);
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    private void secureHeaders(HttpHeaders headers) {
        headers.setBearerAuth(properties.apiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.add("Paddle-Version", "1");
    }

    private void onError(org.springframework.http.HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        log.warn("Paddle API error method={} path={} status={}", request.getMethod(), request.getURI().getPath(), status);
        PaddleErrorInfo sanitized = sanitizeError(body);
        throw new PaddleApiException(status, sanitized.detail(), sanitized.type(), sanitized.code());
    }

    private PaddleApiException toApiException(RestClientResponseException ex) {
        log.warn("Paddle API error status={}", ex.getStatusCode().value());
        PaddleErrorInfo sanitized = sanitizeError(ex.getResponseBodyAsString());
        return new PaddleApiException(ex.getStatusCode().value(), sanitized.detail(), sanitized.type(), sanitized.code());
    }

    private PaddleApiException wrap(Exception ex) {
        if (ex instanceof RestClientException rest && rest.getCause() instanceof PaddleApiException paddle) {
            return paddle;
        }
        if (ex instanceof PaddleApiException paddle) {
            return paddle;
        }
        log.warn("Paddle API request failed: {}", ex.getClass().getSimpleName());
        return new PaddleApiException(0, "Paddle API request failed");
    }

    private PaddleErrorInfo sanitizeError(String body) {
        if (!StringUtils.hasText(body)) {
            return new PaddleErrorInfo("Paddle API request failed", null, null);
        }
        try {
            PaddleDtos.ErrorEnvelope envelope = objectMapper.readValue(body, new TypeReference<>() {
            });
            if (envelope != null && envelope.error() != null) {
                String detail = StringUtils.hasText(envelope.error().detail())
                        ? envelope.error().detail()
                        : "Paddle API request failed";
                return new PaddleErrorInfo(detail, envelope.error().type(), envelope.error().code());
            }
        } catch (Exception ignored) {
            // Fall through to a generic message so payment payloads are never logged or returned.
        }
        return new PaddleErrorInfo("Paddle API request failed", null, null);
    }

    private record PaddleErrorInfo(String detail, String type, String code) {
    }

    private void ensureConfigured() {
        if (!properties.configured()) {
            throw new PaddleApiException(0, "Paddle API key is not configured");
        }
    }
}
