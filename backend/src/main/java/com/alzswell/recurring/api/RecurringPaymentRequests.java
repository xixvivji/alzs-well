package com.alzswell.recurring.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class RecurringPaymentRequests {
    private RecurringPaymentRequests() {}

    public record ReminderSettingsCommand(
            @NotNull Boolean enabled,
            @NotNull @Min(0) @Max(30) Integer leadDays,
            @NotNull @Min(0) Long expectedVersion
    ) {}
}
