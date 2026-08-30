package com.alzswell.transaction.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TransactionResponses {
    private TransactionResponses() {}

    public record TransactionItem(UUID transactionId, UUID accountId, String accountDisplayName,
                                  String institutionName, UUID counterpartyId, String counterpartyName,
                                  OffsetDateTime occurredAt, LocalDate postedOn, String direction,
                                  String transactionType, String status, BigDecimal amount, String currency,
                                  BigDecimal balanceAfter, String description, String category,
                                  String customerNote, long preferenceVersion, String providerMode,
                                  LocalDate dataAsOf, boolean syntheticData, boolean externalActionAvailable) {}
    public record TransactionPage(List<TransactionItem> items, int count, UUID nextCursor,
                                  boolean hasNext, LocalDate from, LocalDate to) {}
    public record TransactionDetail(TransactionItem transaction, boolean originalDescriptionAvailable,
                                    boolean cancellationAvailable, boolean correctionAvailable) {}
    public record CategorySummary(String category, BigDecimal inflow, BigDecimal outflow, int count) {}
    public record TransactionSummary(BigDecimal totalInflow, BigDecimal totalOutflow, BigDecimal netCashflow,
                                     int transactionCount, String currency, List<CategorySummary> categories,
                                     LocalDate from, LocalDate to, boolean pendingExcluded) {}
    public record CounterpartyItem(UUID counterpartyId, String displayName, String counterpartyType,
                                   LocalDate firstSeenOn, LocalDate lastSeenOn, int transactionCount,
                                   boolean newCounterparty, BigDecimal totalAmount, String currency,
                                   LocalDate dataAsOf) {}
    public record CounterpartyList(List<CounterpartyItem> items, int total, boolean syntheticData) {}
    public record CounterpartyHistory(UUID counterpartyId, String displayName, List<TransactionItem> items,
                                      int count, UUID nextCursor, boolean hasNext,
                                      LocalDate from, LocalDate to) {}
    public record TransactionEnrichment(UUID transactionId, String normalizedDescription,
                                        String inferredCategory, String effectiveCategory,
                                        boolean customerCategoryOverride, boolean recurringCandidate,
                                        boolean newCounterparty, BigDecimal confidence,
                                        String enrichmentVersion, List<String> reasonCodes,
                                        boolean deterministic) {}
    public record TransactionPreference(UUID transactionId, String category, String note,
                                        long rowVersion, OffsetDateTime updatedAt,
                                        boolean externalActionExecuted) {}
}
