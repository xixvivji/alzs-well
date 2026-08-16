package com.alzswell.demo.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BaselineListResponse(String customerId, DatePeriod baselinePeriod,
                                   DatePeriod observationPeriod, List<BaselineItem> items,
                                   SyntheticDataProvenance provenance) {
    public record DatePeriod(LocalDate from, LocalDate to) {
    }

    public record BaselineItem(String baselineId, String featureCode, String baselineValue,
                               String currentValue, String unit, String readiness,
                               String comparisonText, List<String> reasonCodes,
                               String algorithmVersion, OffsetDateTime calculatedAt) {
    }
}
