package com.alzswell.account.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AccountResponses {
    private AccountResponses() {}

    public record AccountSummary(UUID accountId, String customerId, UUID connectionId,
                                 String institutionId, String institutionName, String accountType,
                                 String displayName, String maskedAccountNumber, String accountStatus,
                                 BigDecimal currentBalance, BigDecimal availableBalance, String currency,
                                 OffsetDateTime balanceAsOf, String providerMode, LocalDate dataAsOf,
                                 boolean syntheticData, boolean externalExecutionAvailable) {}
    public record AccountList(List<AccountSummary> items, int total, LocalDate dataAsOf) {}
    public record AccountDetail(AccountSummary account, boolean accountNumberFullyMasked,
                                boolean transferAvailable, boolean closureAvailable) {}
    public record Balance(UUID accountId, BigDecimal currentBalance, BigDecimal availableBalance,
                          String currency, OffsetDateTime balanceAsOf, LocalDate dataAsOf) {}
    public record BalancePoint(LocalDate balanceDate, BigDecimal currentBalance,
                               BigDecimal availableBalance, String currency) {}
    public record BalanceHistory(UUID accountId, List<BalancePoint> items, int total,
                                 LocalDate from, LocalDate to, LocalDate dataAsOf) {}
    public record Restriction(UUID restrictionId, String restrictionCode, String title,
                              String description, String status, LocalDate effectiveFrom,
                              LocalDate effectiveTo, boolean externalActionAvailable) {}
    public record RestrictionList(UUID accountId, List<Restriction> items, int total, LocalDate dataAsOf) {}
    public record InterestSummary(UUID accountId, String interestType, BigDecimal annualInterestRate,
                                  BigDecimal accruedInterest, String currency, LocalDate interestAsOf,
                                  boolean estimated, LocalDate dataAsOf) {}
    public record StatementSummary(UUID statementId, LocalDate periodFrom, LocalDate periodTo,
                                   BigDecimal openingBalance, BigDecimal closingBalance,
                                   BigDecimal totalInflow, BigDecimal totalOutflow,
                                   int transactionCount, String currency, OffsetDateTime generatedAt,
                                   boolean fileAvailable) {}
    public record StatementList(UUID accountId, List<StatementSummary> items, int total, LocalDate dataAsOf) {}
    public record StatementDetail(UUID accountId, StatementSummary statement,
                                  boolean transactionRowsIncluded, boolean externalDownloadAvailable) {}
}
