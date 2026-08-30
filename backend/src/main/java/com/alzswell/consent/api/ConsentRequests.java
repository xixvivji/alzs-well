package com.alzswell.consent.api;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ConsentRequests {
    private static final String PURPOSE = "FINANCIAL_ANALYSIS|PROTECTION_GUIDANCE|TRUSTED_CONTACT_DISCLOSURE";
    private static final String SCOPE = "ACCOUNT_SUMMARY|TRANSACTION_SUMMARY|BASELINE_SIGNAL|PROTECTION_CASE|CONTACT_MINIMUM";
    public record GrantCommand(
            @NotBlank @Pattern(regexp=PURPOSE) String purposeCode,
            @NotEmpty @Size(max=5) List<@NotBlank @Pattern(regexp=SCOPE) String> scopes,
            @NotNull @Future OffsetDateTime expiresAt) {}
    public record WithdrawCommand(@NotNull @Positive Long expectedVersion,
            @NotBlank @Size(max=300) String reason) {}
    public record DisclosureEvaluationCommand(
            @NotNull UUID consentId,
            @NotBlank @Pattern(regexp=PURPOSE) String purposeCode,
            @NotEmpty @Size(max=5) List<@NotBlank @Pattern(regexp=SCOPE) String> requestedScopes) {}
    private ConsentRequests() {}
}
