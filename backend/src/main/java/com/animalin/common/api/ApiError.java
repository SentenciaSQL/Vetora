package com.animalin.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> errors,
        Map<String, Object> details
) {
    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, String path, List<FieldError> errors) {
        return of(status, code, message, path, errors, Map.of());
    }

    public static ApiError of(int status, String code, String message, String path, List<FieldError> errors,
                              Map<String, Object> details) {
        return new ApiError(Instant.now(), status, code, message, path, errors, details);
    }
}
