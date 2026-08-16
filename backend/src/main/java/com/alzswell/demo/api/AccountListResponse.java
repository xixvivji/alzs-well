package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.List;

public record AccountListResponse(String customerId, List<AccountItem> items,
                                  String nextCursor, boolean hasMore,
                                  SyntheticDataProvenance provenance) {
    public record AccountItem(String accountId, String institutionId, String accountType,
                              String displayName, String maskedAccountNumber,
                              MoneyAmount currentBalance, MoneyAmount availableBalance,
                              String connectionId, String consentId, String sourceProvider,
                              OffsetDateTime sourceUpdatedAt, String dataFreshness) {
    }
}
