package com.animalin.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

class ResendApiClientTest {

    private MockRestServiceServer server;
    private ResendApiClient client;
    private final EmailDtos.ResendEmailRequest request = new EmailDtos.ResendEmailRequest(
            "LunaVeta <no-reply@lunaveta.com>",
            List.of("usuario@correo.com"),
            "Asunto",
            "<html><body>Hola</body></html>"
    );

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ResendApiClient(
                new ResendProperties("re_test_key", "LunaVeta <no-reply@lunaveta.com>", "https://api.resend.com"),
                new ObjectMapper(),
                builder.build()
        );
    }

    @Test
    void sendsJsonBodyAndReturnsResendId() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test_key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\":\"email_123\",\"object\":\"email\"}", MediaType.APPLICATION_JSON));

        EmailDtos.ResendEmailResponse response = client.send(request, "email_verification");
        assertThat(response.id()).isEqualTo("email_123");
        server.verify();
    }

    @Test
    void mapsUnauthorizedWithoutExposingApiKey() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(UNAUTHORIZED).body("""
                        {"statusCode":401,"name":"validation_error","message":"API key is invalid"}
                        """).contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.send(request, "email_verification"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageNotContaining("re_test_key")
                .hasMessageNotContaining("API key is invalid")
                .extracting(ex -> ((EmailDeliveryException) ex).getHttpStatus())
                .isEqualTo(401);
    }

    @Test
    void mapsRateLimitAndServerErrors() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":429,\"name\":\"rate_limit_exceeded\",\"message\":\"Too many\"}"));
        assertThatThrownBy(() -> client.send(request, "password_reset"))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(ex -> ((EmailDeliveryException) ex).getHttpStatus())
                .isEqualTo(429);

        setUp();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"unavailable\"}"));
        assertThatThrownBy(() -> client.send(request, "password_reset"))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(ex -> ((EmailDeliveryException) ex).getHttpStatus())
                .isEqualTo(500);
    }

    @Test
    void mapsUnverifiedDomainAndEmptyResponse() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":403,\"name\":\"validation_error\",\"message\":\"The lunaveta.com domain is not verified\"}"));
        assertThatThrownBy(() -> client.send(request, "email_verification"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageNotContaining("lunaveta.com domain is not verified");

        setUp();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":400,\"message\":\"invalid from\"}"));
        assertThatThrownBy(() -> client.send(request, "email_verification"))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(ex -> ((EmailDeliveryException) ex).getHttpStatus())
                .isEqualTo(400);

        setUp();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"domain not verified\"}"));
        assertThatThrownBy(() -> client.send(request, "email_verification"))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(ex -> ((EmailDeliveryException) ex).getHttpStatus())
                .isEqualTo(422);

        setUp();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.send(request, "email_verification"))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(ex -> ((EmailDeliveryException) ex).getHttpStatus())
                .isEqualTo(502);
    }

    @Test
    void skipsSendWhenApiKeyIsMissing() {
        ResendApiClient missing = new ResendApiClient(
                new ResendProperties("", "LunaVeta <no-reply@lunaveta.com>", "https://api.resend.com"),
                new ObjectMapper(),
                RestClient.builder().baseUrl("https://api.resend.com").build()
        );
        assertThatThrownBy(() -> missing.send(request, "email_verification"))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(ex -> ((EmailDeliveryException) ex).getHttpStatus())
                .isEqualTo(401);
    }
}
