package com.alzswell.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}

    public record LoginCommand(
            @NotBlank @Size(max = 80) String loginId,
            @NotBlank @Size(min = 12, max = 200) String password
    ) {}

    public record RefreshCommand(@NotBlank @Size(min = 40, max = 300) String refreshToken) {}
}
