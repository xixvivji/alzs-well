package com.alzswell.common.web;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceIdContext {

    public static final String MDC_KEY = "traceId";

    private TraceIdContext() {
    }

    public static String currentOrCreate() {
        String current = MDC.get(MDC_KEY);
        return current == null || current.isBlank() ? newTraceId() : current;
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
