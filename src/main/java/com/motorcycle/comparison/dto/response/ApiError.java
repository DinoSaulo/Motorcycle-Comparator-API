package com.motorcycle.comparison.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** One error shape for the whole API, so clients only ever write one handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Uniform error payload")
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> violations
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError withViolations(int status, String error, String message, String path, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, error, message, path, violations);
    }

    @Schema(description = "A single failed validation constraint")
    public record FieldViolation(String field, String message) {}
}
