package com.alzswell.privacy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PrivacyRequests {
    private PrivacyRequests() {}
    public record DeletionRequest(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{2,59}") String targetType,
            @Size(max=120) @Pattern(regexp="[A-Za-z0-9_:-]*") String targetReference,
            @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{2,59}") String reasonCode) {}
    public record CorrectionRequest(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{2,59}") String targetType,
            @Size(max=120) @Pattern(regexp="[A-Za-z0-9_:-]*") String targetReference,
            @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{2,59}") String reasonCode,
            @NotBlank @Size(max=500) String correctedValue) {}
}
