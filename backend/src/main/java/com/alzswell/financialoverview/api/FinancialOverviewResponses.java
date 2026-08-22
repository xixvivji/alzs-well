package com.alzswell.financialoverview.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class FinancialOverviewResponses {
    private FinancialOverviewResponses() {}

    public record FinancialSummary(BigDecimal totalAssets, BigDecimal totalLiabilities,
                                   BigDecimal netAssets, BigDecimal periodInflow, BigDecimal periodOutflow,
                                   BigDecimal netCashflow, int accountCount, int liabilityCount,
                                   String currency, LocalDate dataAsOf, boolean syntheticData,
                                   boolean externalExecutionAvailable) {}
    public record AssetBreakdownItem(String institutionId, String institutionName, String assetClass,
                                     BigDecimal amount, BigDecimal percentage, int accountCount) {}
    public record AssetBreakdown(List<AssetBreakdownItem> items, BigDecimal totalAssets,
                                 String currency, LocalDate dataAsOf) {}
    public record AssetTrendPoint(LocalDate date, BigDecimal totalAssets,
                                  BigDecimal totalLiabilities, BigDecimal netAssets) {}
    public record AssetTrends(List<AssetTrendPoint> items, int count, LocalDate from, LocalDate to,
                              String currency, boolean syntheticData) {}
    public record Liability(UUID liabilityId, String institutionId, String institutionName,
                            String liabilityType, String displayName, String maskedReference,
                            BigDecimal outstandingAmount, BigDecimal scheduledAmount,
                            BigDecimal annualInterestRate, LocalDate nextDueDate, String status,
                            String currency, LocalDate dataAsOf, boolean repaymentAvailable) {}
    public record LiabilityList(List<Liability> items, int total, BigDecimal totalOutstanding,
                                String currency, LocalDate dataAsOf) {}
    public record CashflowCategory(String category, BigDecimal inflow, BigDecimal outflow, int count) {}
    public record CashflowSummary(BigDecimal totalInflow, BigDecimal totalOutflow, BigDecimal netCashflow,
                                  List<CashflowCategory> categories, int transactionCount,
                                  LocalDate from, LocalDate to, String currency, boolean pendingExcluded) {}
    public record ExpenseBreakdown(String category, String institutionName,
                                   BigDecimal amount, BigDecimal percentage, int count) {}
    public record ExpenseSummary(BigDecimal totalExpense, List<ExpenseBreakdown> items,
                                 LocalDate from, LocalDate to, String currency) {}
    public record AssetCalendarEvent(UUID eventId, String sourceType, String eventType, String title,
                                     LocalDate scheduledDate, String direction, BigDecimal expectedAmount,
                                     String currency, String certainty, boolean externalActionAvailable) {}
    public record AssetCalendar(List<AssetCalendarEvent> items, int total, LocalDate from, LocalDate to,
                                LocalDate dataAsOf, boolean syntheticData) {}
    public record FreshnessItem(String institutionId, String institutionName, UUID connectionId,
                                String connectionStatus, OffsetDateTime lastSyncedAt, LocalDate dataAsOf,
                                int accountCount, int transactionCount, String freshnessStatus,
                                boolean complete, String providerMode) {}
    public record DataFreshness(List<FreshnessItem> items, int total, LocalDate evaluatedAsOf,
                                boolean allFresh, boolean syntheticData) {}
}
