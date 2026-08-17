package com.alzswell.identity.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AuthResponses {
    private AuthResponses() {}

    public record TokenPair(String tokenType, String accessToken, OffsetDateTime accessExpiresAt,
                            String refreshToken, OffsetDateTime refreshExpiresAt) {}
    public record CurrentUser(UUID principalId, String loginId, String customerId,
                              String displayName, List<String> roles) {}
    public record PermissionList(List<String> permissions) {}
}
