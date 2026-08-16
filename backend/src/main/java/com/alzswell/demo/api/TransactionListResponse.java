package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.List;

public record TransactionListResponse(String accountId, List<TransactionItem> items,
                                      String nextCursor, boolean hasMore,
                                      SyntheticDataProvenance provenance) {
    public record TransactionItem(String transactionId, OffsetDateTime occurredAt,
                                  OffsetDateTime postedAt, String direction,
                                  String transactionType, String amount, String currency,
                                  String balanceAfter, String counterpartyDisplayName,
                                  String category, String status, String sourceProvider,
                                  String dataFreshness) {
    }
}
