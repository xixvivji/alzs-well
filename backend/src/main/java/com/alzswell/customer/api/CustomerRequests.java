package com.alzswell.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CustomerRequests {

    public record DisplayProfileCommand(@NotBlank @Size(max = 80) String displayName) {
    }

    public record PreferencesCommand(
            @NotNull Boolean smsNotificationEnabled,
            @NotNull Boolean pushNotificationEnabled,
            @NotNull Boolean inAppNotificationEnabled
    ) {
    }

    public record AccessibilitySettingsCommand(
            @NotNull Boolean largeFont,
            @NotNull Boolean highContrast,
            @NotNull Boolean speechGuidance,
            @NotNull Boolean oneHandMode
    ) {
    }

    private CustomerRequests() {
    }
}
