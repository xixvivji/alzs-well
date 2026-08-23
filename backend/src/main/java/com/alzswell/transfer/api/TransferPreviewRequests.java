package com.alzswell.transfer.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

public final class TransferPreviewRequests {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";

    private TransferPreviewRequests() {}

    public record Simulation(
            @NotBlank @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @NotNull UUID sourceAccountId,
            @NotNull UUID beneficiaryId,
            @NotNull @DecimalMin("1") @DecimalMax("100000000") @Digits(integer = 9, fraction = 0)
            BigDecimal amount,
            @NotBlank @Pattern(regexp = "KRW") String currency
    ) {}

    public record Validation(
            @NotBlank @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @NotNull UUID sourceAccountId,
            @NotNull UUID beneficiaryId,
            @NotNull @DecimalMin("1") @DecimalMax("100000000") @Digits(integer = 9, fraction = 0)
            BigDecimal amount,
            @NotBlank @Pattern(regexp = "KRW") String currency,
            @NotBlank @Pattern(regexp = "LIVING_EXPENSE|FAMILY_SUPPORT|BILL_PAYMENT|OWN_ACCOUNT|OTHER")
            String purposeCode,
            @NotNull Boolean recipientConfirmed
    ) {}
}
