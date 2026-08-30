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
    public record AuthSessionSummary(UUID sessionId,String status,boolean currentSession,
            OffsetDateTime createdAt,OffsetDateTime lastRotatedAt,OffsetDateTime accessExpiresAt,
            OffsetDateTime refreshExpiresAt,OffsetDateTime absoluteExpiresAt,OffsetDateTime revokedAt,
            String revokeReason) {}
    public record AuthSessionList(List<AuthSessionSummary> items,int total,int activeCount) {}
    public record AuthSessionRevocation(UUID sessionId,String status,boolean currentSession,
            boolean alreadyEnded,OffsetDateTime revokedAt) {}
}
