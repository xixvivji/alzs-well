package com.alzswell.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class CustomerRequests {

    public record DisplayProfileCommand(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 80) String displayName
    ) {
    }

    public record PreferencesCommand(
            @NotNull @PositiveOrZero Long expectedVersion,
            Boolean smsNotificationEnabled,
            Boolean pushNotificationEnabled,
            Boolean inAppNotificationEnabled
    ) {
    }

    public record AccessibilitySettingsCommand(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull Boolean largeFont,
            @NotNull Boolean highContrast,
            @NotNull Boolean speechGuidance,
            @NotNull Boolean oneHandMode
    ) {
    }

    private CustomerRequests() {
    }
}
