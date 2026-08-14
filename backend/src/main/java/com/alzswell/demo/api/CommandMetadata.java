package com.alzswell.demo.api;

public record CommandMetadata(
        String requestHash,
        boolean idempotencyReplayed
) {
}
