package com.alzswell.transfer.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TransferPreviewResponses {
    private TransferPreviewResponses() {}

    public record Beneficiary(UUID beneficiaryId, String institutionId, String institutionName,
                              String displayName, String maskedAccountReference,
                              String beneficiaryType, String status, boolean favorite,
                              String providerMode, LocalDate dataAsOf) {}

    public record BeneficiaryList(List<Beneficiary> items, int total, LocalDate dataAsOf,
                                  boolean syntheticData, boolean externalProviderCalled) {}

    public record TransferLimit(UUID limitSnapshotId, BigDecimal perTransferLimit,
                                BigDecimal dailyLimit, BigDecimal dailyUsedAmount,
                                BigDecimal dailyRemainingAmount, String currency,
                                LocalDate dataAsOf, String providerMode,
                                boolean syntheticData, boolean externalProviderCalled) {}

    public record EvaluationContext(UUID sourceAccountId, BigDecimal availableBalance,
                                    UUID beneficiaryId, String beneficiaryDisplayName,
                                    String maskedAccountReference, BigDecimal amount,
                                    String currency, BigDecimal perTransferLimit,
                                    BigDecimal dailyRemainingAmount) {}

    public record ValidationCheck(String checkCode, boolean passed, String message) {}

    public record SimulationResult(EvaluationContext context, BigDecimal estimatedFee,
                                   BigDecimal totalDebit, BigDecimal projectedAvailableBalance,
                                   String outcomeCode, List<ValidationCheck> checks,
                                   LocalDate dataAsOf, boolean syntheticData,
                                   boolean externalProviderCalled, boolean transferCreated,
                                   boolean authorizationCreated) {}

    public record ValidationResult(EvaluationContext context, String purposeCode,
                                   boolean allowed, String decisionCode,
                                   List<ValidationCheck> checks, LocalDate dataAsOf,
                                   boolean syntheticData, boolean externalProviderCalled,
                                   boolean transferCreated, boolean authorizationCreated) {}
}
