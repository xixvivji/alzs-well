package com.alzswell.trustedcontact.api;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TrustedContactRequests {
    private static final String SCOPE="ALERT_REASON_SUMMARY|CONTACT_REQUEST_STATUS|PROTECTION_GUIDANCE_SUMMARY";
    public record CreateCommand(@NotNull UUID consentId,@NotBlank @Size(max=80) String displayName,
            @NotBlank @Pattern(regexp="FAMILY|CAREGIVER|OTHER") String relationshipCode,
            @NotBlank @Pattern(regexp="^(?=.*\\*)[0-9+*\\- ]{7,40}$") String maskedContact,
            @NotEmpty @Size(max=3) List<@Pattern(regexp=SCOPE) String> scopes,
            @NotNull @Future OffsetDateTime expiresAt) {}
    public record UpdateCommand(@NotNull @Positive Long expectedVersion,
            @NotEmpty @Size(max=3) List<@Pattern(regexp=SCOPE) String> scopes,
            @NotNull @Future OffsetDateTime expiresAt) {}
    public record RevokeCommand(@NotNull @Positive Long expectedVersion,
            @NotBlank @Size(max=300) String reason) {}
    private TrustedContactRequests(){}
}
