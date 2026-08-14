package com.alzswell.system.api;

import java.util.LinkedHashMap;
import java.util.Map;

public record SystemReadinessResponse(
        boolean ready,
        String status,
        Map<String, String> checks
) {
    public SystemReadinessResponse {
        checks = Map.copyOf(new LinkedHashMap<>(checks));
    }
}
