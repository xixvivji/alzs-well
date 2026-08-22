package com.alzswell.account.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AccountRequests {
    private AccountRequests() {}

    public record UpdateDisplaySetting(
            @Size(max = 40) String alias,
            @Min(0) @Max(99) Integer displayOrder,
            Boolean hidden,
            @NotNull @Min(1) Long expectedVersion
    ) {}
}
