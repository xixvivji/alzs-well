package com.alzswell.detection.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class AiQualityResponses {
    private AiQualityResponses() {}

    public record AiQualitySummary(
            int windowHours,
            OffsetDateTime from,
            OffsetDateTime to,
            String status,
            long searchRequests,
            long groundedSearches,
            long fallbackSearches,
            long emptySearches,
            long rejectedCitations,
            BigDecimal searchFallbackRate,
            long assistanceRequests,
            long assistanceGenerated,
            long assistanceFallbacks,
            BigDecimal assistanceFallbackRate,
            boolean syntheticDataOnly,
            boolean externalActionsExecuted
    ) {}
}
