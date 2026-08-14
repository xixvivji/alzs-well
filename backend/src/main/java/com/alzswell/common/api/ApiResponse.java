package com.alzswell.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;

public record ApiResponse<T>(
        boolean success,
        int status,
        String code,
        String message,
        T data,
        List<FieldViolation> errors,
        OffsetDateTime timestamp,
        String traceId
) {

    public ApiResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static <T> ApiResponse<T> success(
            HttpStatus status,
            String code,
            String message,
            T data,
            String traceId
    ) {
        return new ApiResponse<>(
                true,
                status.value(),
                code,
                message,
                data,
                List.of(),
                OffsetDateTime.now(ZoneOffset.UTC),
                traceId
        );
    }

    public static ApiResponse<Void> failure(
            HttpStatus status,
            String code,
            String message,
            List<FieldViolation> errors,
            String traceId
    ) {
        return failure(status, code, message, null, errors, traceId);
    }

    public static <T> ApiResponse<T> failure(
            HttpStatus status,
            String code,
            String message,
            T data,
            List<FieldViolation> errors,
            String traceId
    ) {
        return new ApiResponse<>(
                false,
                status.value(),
                code,
                message,
                data,
                errors,
                OffsetDateTime.now(ZoneOffset.UTC),
                traceId
        );
    }
}
