package com.animalin.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

@Component
public class ResendApiClient {

    private static final Logger log = LoggerFactory.getLogger(ResendApiClient.class);

    private final ResendProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public ResendApiClient(
            ResendProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("resendRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public EmailDtos.ResendEmailResponse send(EmailDtos.ResendEmailRequest request, String emailType) {
        if (!properties.configured()) {
            log.warn("Email not sent type={} to={} status=skipped reason=missing_api_key",
                    emailType, EmailLogSupport.mask(firstRecipient(request)));
            throw new EmailDeliveryException(401, "El envío de correo no está configurado", emailType);
        }
        try {
            EmailDtos.ResendEmailResponse response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> handleError(httpResponse, emailType))
                    .body(EmailDtos.ResendEmailResponse.class);
            if (response == null || !StringUtils.hasText(response.id())) {
                log.warn("Email failed type={} to={} status=empty_response",
                        emailType, EmailLogSupport.mask(firstRecipient(request)));
                throw new EmailDeliveryException(502, "Resend devolvió una respuesta vacía o inesperada", emailType);
            }
            log.info("Email sent type={} to={} status=sent resendId={}",
                    emailType, EmailLogSupport.mask(firstRecipient(request)), response.id());
            return response;
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapStatus(ex.getStatusCode().value(), ex.getResponseBodyAsString(), emailType);
        } catch (ResourceAccessException ex) {
            boolean timeout = ex.getCause() instanceof SocketTimeoutException
                    || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"));
            int status = timeout ? 408 : 503;
            String reason = timeout ? "timeout" : "connection_error";
            log.warn("Email failed type={} to={} status={} httpStatus={}",
                    emailType, EmailLogSupport.mask(firstRecipient(request)), reason, status);
            throw new EmailDeliveryException(status, timeout
                    ? "Tiempo de espera agotado al contactar Resend"
                    : "No se pudo contactar el servicio de correo", emailType);
        } catch (RestClientException ex) {
            if (ex.getCause() instanceof EmailDeliveryException delivery) {
                throw delivery;
            }
            log.warn("Email failed type={} to={} status=client_error cause={}",
                    emailType, EmailLogSupport.mask(firstRecipient(request)), ex.getClass().getSimpleName());
            throw new EmailDeliveryException(502, "No se pudo enviar el correo", emailType);
        }
    }

    private void handleError(ClientHttpResponse response, String emailType) throws IOException {
        int status = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        throw mapStatus(status, body, emailType);
    }

    private EmailDeliveryException mapStatus(int status, String body, String emailType) {
        String code = errorName(body);
        String reason = switch (status) {
            case 400 -> "bad_request";
            case 401 -> "invalid_api_key";
            case 403 -> domainUnverified(code, body) ? "unverified_sender_domain" : "forbidden";
            case 408 -> "timeout";
            case 422 -> domainUnverified(code, body) ? "unverified_sender_domain" : "unprocessable";
            case 429 -> "rate_limited";
            default -> status >= 500 ? "resend_unavailable" : "unexpected";
        };
        log.warn("Email failed type={} status={} httpStatus={} reason={}", emailType, reason, status, reason);
        String publicMessage = switch (status) {
            case 401, 403 -> "El servicio de correo no está autorizado";
            case 429 -> "Se alcanzó el límite de envío de correos. Inténtelo más tarde";
            case 408 -> "Tiempo de espera agotado al contactar Resend";
            default -> status >= 500
                    ? "El servicio de correo no está disponible temporalmente"
                    : "No se pudo enviar el correo";
        };
        return new EmailDeliveryException(status, publicMessage, emailType);
    }

    private String errorName(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            EmailDtos.ResendErrorResponse error = objectMapper.readValue(body, EmailDtos.ResendErrorResponse.class);
            return error == null || error.name() == null ? "" : error.name();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean domainUnverified(String code, String body) {
        String haystack = ((code == null ? "" : code) + " " + (body == null ? "" : body)).toLowerCase();
        return haystack.contains("domain") && (haystack.contains("not verified")
                || haystack.contains("unverified")
                || haystack.contains("validation_error"));
    }

    private static String firstRecipient(EmailDtos.ResendEmailRequest request) {
        if (request == null || request.to() == null || request.to().isEmpty()) {
            return "";
        }
        return request.to().getFirst();
    }
}
