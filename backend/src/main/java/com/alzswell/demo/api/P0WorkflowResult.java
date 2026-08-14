package com.alzswell.demo.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record P0WorkflowResult(
        String code,
        String message,
        Map<String, Object> data
) {
    public P0WorkflowResult {
        data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
