package com.alzswell.transaction.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TransactionRequests {
    private TransactionRequests() {}

    public record UpdateCategory(
            @NotBlank @Pattern(regexp = "INCOME|HOUSING|UTILITIES|COMMUNICATION|FOOD|TRANSPORT|HEALTH|FINANCE|SHOPPING|OTHER")
            String category,
            @NotNull @Min(1) Long expectedVersion
    ) {}

    public record UpdateNote(
            @NotNull @Size(max = 120) String note,
            @NotNull @Min(1) Long expectedVersion
    ) {}
}
