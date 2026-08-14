package com.alzswell.common.api;

public record FieldViolation(
        String field,
        String reason
) {
}
