package com.alzswell.protection.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class ProtectionRequests {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    public record EligibilityEvaluationCommand(
            @NotBlank @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @NotBlank @Pattern(regexp = "MISSED_RECURRING_PAYMENT|DUPLICATE_TRANSFER|REPEATED_CONFIRMATION")
            String reasonCode) {}
    private ProtectionRequests() {}
}
