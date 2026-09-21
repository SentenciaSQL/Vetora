package com.animalin.email;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class EmailDtos {

    private EmailDtos() {
    }

    public record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResendEmailResponse(
            String id,
            String object
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResendErrorResponse(
            Integer statusCode,
            String name,
            String message,
            @JsonProperty("status_code") Integer statusCodeSnake
    ) {
        public int status() {
            if (statusCode != null) {
                return statusCode;
            }
            return statusCodeSnake == null ? 0 : statusCodeSnake;
        }
    }
}
