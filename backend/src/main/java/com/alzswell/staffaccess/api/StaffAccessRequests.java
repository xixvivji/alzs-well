package com.alzswell.staffaccess.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class StaffAccessRequests {
    private StaffAccessRequests() {}

    public record GrantCommand(
            @NotNull UUID staffPrincipalId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,59}") String purposeCode,
            @NotEmpty @Size(max = 12) List<@Pattern(regexp = "[A-Z][A-Z0-9_]{2,59}") String> scopes,
            @NotNull @Future OffsetDateTime expiresAt
    ) {}

    public record RevokeCommand(
            @Positive long expectedVersion,
            @NotBlank @Size(max = 300) String reason
    ) {}

    public record EvaluationCommand(
            @NotNull UUID staffPrincipalId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_:-]{2,79}") String customerId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,59}") String scope
    ) {}
}
