package com.alzswell.demo.api;

import java.time.LocalDate;
import java.util.List;

public record ProtectionActionListResponse(List<ProtectionActionItem> items,
                                           boolean syntheticData, String dataMode) {
    public record ProtectionActionItem(String actionCode, String title, String status,
                                       String executionType, String eligibilitySummary,
                                       Source source) {
    }

    public record Source(String issuer, String url, LocalDate effectiveFrom, LocalDate checkedAt) {
    }
}
