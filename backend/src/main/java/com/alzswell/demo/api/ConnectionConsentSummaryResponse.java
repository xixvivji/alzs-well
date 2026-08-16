package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ConnectionConsentSummaryResponse(List<ConnectionItem> items, ConsentSummary consentSummary,
                                               SyntheticDataProvenance provenance) {
    public record ConnectionItem(String connectionId, String institutionId, String institutionName,
                                 String institutionType, String status, String sourceProvider,
                                 OffsetDateTime sourceUpdatedAt, String dataFreshness, String consentId,
                                 List<String> consentScope) {
    }

    public record ConsentSummary(String purpose, boolean granted, OffsetDateTime grantedAt,
                                 OffsetDateTime expiresAt, boolean revocable,
                                 boolean trustedContactGranted) {
    }
}
