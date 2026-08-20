package com.alzswell.system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class FeatureFlagRequests {
    private FeatureFlagRequests() {}

    public record UpdateFeatureFlagCommand(
            boolean enabled,
            long expectedVersion,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:/-]+") String approvalReference,
            @NotBlank @Size(min = 10, max = 500) String changeReason
    ) {}
}
