package com.animalin.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException sessionInactive() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INACTIVE",
                "Su sesión expiró por inactividad. Inicie sesión nuevamente.");
    }

    public static ApiException emailNotVerified() {
        return new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                "Debe verificar su correo electrónico antes de continuar");
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }

    public static ApiException subscriptionSuspended(String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.FORBIDDEN, "TENANT_SUBSCRIPTION_SUSPENDED", message, details);
    }

    public static ApiException planLimitReached(String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.CONFLICT, "PLAN_LIMIT_REACHED", message, details);
    }

    public static ApiException planFeatureUnavailable(String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.FORBIDDEN, "PLAN_FEATURE_NOT_AVAILABLE", message, details);
    }

    public HttpStatus getStatus() {
        return status;
    }
    public String getCode() {
        return code;
    }
    public Map<String, Object> getDetails() {
        return details;
    }
}
