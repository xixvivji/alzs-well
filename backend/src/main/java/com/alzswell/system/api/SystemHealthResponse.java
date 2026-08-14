package com.alzswell.system.api;

public record SystemHealthResponse(
        String status,
        String service,
        boolean syntheticDataOnly,
        boolean externalActionsEnabled
) {
}
