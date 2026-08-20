package com.alzswell.detection.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class DetectionPolicyRequests {
    private DetectionPolicyRequests() {}

    public record RuleInput(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,59}") String featureCode,
            @NotNull @DecimalMin("0") BigDecimal triggerDelta,
            @NotNull @DecimalMin("0") BigDecimal highDelta,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,79}") String reasonCode
    ) {}

    public record CreatePolicyCommand(
            @NotBlank @Size(max = 300) String description,
            @NotEmpty @Size(max = 50) List<@Valid RuleInput> rules
    ) {}

    public record UpdatePolicyCommand(
            @NotBlank @Size(max = 300) String description,
            @NotEmpty @Size(max = 50) List<@Valid RuleInput> rules,
            @DecimalMin("0") long expectedVersion
    ) {}
}
