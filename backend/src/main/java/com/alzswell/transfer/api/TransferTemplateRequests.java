package com.alzswell.transfer.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class TransferTemplateRequests {
    private TransferTemplateRequests() {}

    public record Create(
            @NotBlank @Size(max = 50) String templateName,
            @NotNull UUID sourceAccountId,
            @NotNull UUID beneficiaryId,
            @DecimalMin("1") @DecimalMax("100000000") @Digits(integer = 9, fraction = 0)
            BigDecimal amount,
            @NotBlank @Pattern(regexp = "KRW") String currency,
            @NotBlank @Pattern(regexp = "LIVING_EXPENSE|FAMILY_SUPPORT|BILL_PAYMENT|OWN_ACCOUNT|OTHER")
            String purposeCode
    ) {}
}
