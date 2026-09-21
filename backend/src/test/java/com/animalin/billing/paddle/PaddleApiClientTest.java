package com.animalin.billing.paddle;

import com.animalin.billing.PaddleProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

class PaddleApiClientTest {

    private MockRestServiceServer server;
    private PaddleApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sandbox-api.paddle.com");
        server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PaddleProperties properties = new PaddleProperties(
                "pdl_sdbx_apikey_test", "sandbox", "secret", "token", 10, 5, false,
                new PaddleProperties.Jobs(true, "0 5 0 * * *"));
        client = new PaddleApiClient(properties, mapper, builder.build());
    }

    @Test
    void createsPortalSessionAndReturnsOnlyUrlPayload() {
        server.expect(requestTo("https://sandbox-api.paddle.com/customers/ctm_1/portal-sessions"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer pdl_sdbx_apikey_test"))
                .andRespond(withSuccess("""
                        {"data":{"id":"cpls_1","customer_id":"ctm_1","urls":{"general":{"overview":"https://portal.example/tmp"}}}}
                        """, MediaType.APPLICATION_JSON));
        PaddleDtos.PortalSession session = client.createCustomerPortalSession(
                "ctm_1", new PaddleDtos.CreatePortalSessionRequest(java.util.List.of("sub_1")));
        assertThat(session.overviewUrl()).isEqualTo("https://portal.example/tmp");
        server.verify();
    }

    @Test
    void mapsApiErrorsWithoutExposingSecrets() {
        server.expect(requestTo("https://sandbox-api.paddle.com/customers/ctm_missing"))
                .andRespond(withServerError().body("""
                        {"error":{"type":"request_error","code":"not_found","detail":"customer not found"}}
                        """).contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getCustomer("ctm_missing"))
                .isInstanceOf(PaddleApiException.class)
                .hasMessageContaining("customer not found")
                .hasMessageNotContaining("pdl_sdbx_apikey_test");
    }

    @Test
    void previewsSubscriptionUpdateWithoutExposingSecrets() {
        server.expect(requestTo("https://sandbox-api.paddle.com/subscriptions/sub_1/preview"))
                .andExpect(method(PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer pdl_sdbx_apikey_test"))
                .andRespond(withSuccess("""
                        {"data":{"id":"sub_1","status":"active","currency_code":"USD","next_billed_at":"2027-01-15T12:00:00Z","immediate_transaction":{"details":{"totals":{"grand_total":"15830","subtotal":"15830","currency_code":"USD"}}}}}
                        """, MediaType.APPLICATION_JSON));
        PaddleDtos.SubscriptionPreview preview = client.previewSubscriptionUpdate(
                "sub_1",
                new PaddleDtos.UpdateSubscriptionRequest(
                        java.util.List.of(new PaddleDtos.UpdateSubscriptionItem("pri_year", 1)),
                        "prorated_immediately",
                        java.util.Map.of("tenant_id", "1")));
        assertThat(preview.currencyCode()).isEqualTo("USD");
        assertThat(preview.immediateTransaction().details().totals().grandTotal()).isEqualTo("15830");
        server.verify();
    }
}
