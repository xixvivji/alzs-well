package com.alzswell.card.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CardResponses {
    private CardResponses() {}

    public record CardSummary(UUID cardId, String institutionId, String institutionName,
                              UUID linkedAccountId, String displayName, String maskedCardNumber,
                              String cardType, String brandCode, String status, int paymentDay,
                              BigDecimal currentUsageAmount, String currency,
                              String providerMode, LocalDate dataAsOf) {}

    public record CardList(List<CardSummary> items, int total, LocalDate dataAsOf,
                           boolean syntheticData, boolean externalProviderCalled) {}

    public record CardDetail(CardSummary card, LocalDate nextPaymentDueDate,
                             BigDecimal currentDueAmount, boolean lockAvailable,
                             boolean unlockAvailable, boolean replacementAvailable,
                             boolean syntheticData, boolean externalProviderCalled,
                             boolean externalActionExecuted) {}

    public record CardTransaction(UUID cardTransactionId, OffsetDateTime occurredAt,
                                  String merchantDisplayName, String categoryCode,
                                  BigDecimal amount, String status, int installmentMonths,
                                  String currency, LocalDate dataAsOf) {}

    public record CardTransactionPage(UUID cardId, List<CardTransaction> items, int count,
                                      UUID nextCursor, boolean hasNext, LocalDate from, LocalDate to,
                                      boolean syntheticData, boolean externalProviderCalled) {}

    public record CardStatement(UUID statementId, LocalDate periodFrom, LocalDate periodTo,
                                LocalDate statementDate, LocalDate dueDate, BigDecimal totalAmount,
                                BigDecimal paidAmount, BigDecimal remainingDueAmount, String status,
                                String currency, LocalDate dataAsOf) {}

    public record CardStatementList(UUID cardId, List<CardStatement> items, int total,
                                    boolean downloadable, boolean syntheticData,
                                    boolean externalProviderCalled, boolean externalActionExecuted) {}

    public record CardPaymentDue(UUID cardId, UUID sourceStatementId, LocalDate dueDate,
                                 BigDecimal amount, String currency, String paymentStatus,
                                 LocalDate dataAsOf, boolean paymentAvailable, boolean syntheticData,
                                 boolean externalProviderCalled,
                                 boolean externalActionExecuted) {}

    public record CardLimit(UUID cardId, String limitType, BigDecimal totalLimitAmount,
                            BigDecimal usedAmount, BigDecimal availableLimitAmount,
                            String currency, LocalDate dataAsOf, boolean limitChangeAvailable,
                            boolean syntheticData, boolean externalProviderCalled,
                            boolean externalActionExecuted) {}
}
