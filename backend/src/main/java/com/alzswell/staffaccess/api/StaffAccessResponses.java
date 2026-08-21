package com.alzswell.staffaccess.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class StaffAccessResponses {
    private StaffAccessResponses() {}

    public record Grant(
            UUID grantId, UUID staffPrincipalId, String customerId, String purposeCode,
            List<String> scopes, String status, OffsetDateTime grantedAt, OffsetDateTime expiresAt,
            OffsetDateTime revokedAt, String revocationReason, long version,
            boolean externalIamCalled, boolean externalActionExecuted
    ) {}

    public record GrantList(String customerId, List<Grant> items, int totalCount) {}

    public record Evaluation(
            UUID evaluationId, UUID staffPrincipalId, String customerId, String scope,
            boolean allowed, UUID grantId, String decisionCode, OffsetDateTime evaluatedAt,
            boolean externalIamCalled
    ) {}

    public record GrantEvent(
            UUID eventId, String eventType, String statusSnapshot, List<String> scopesSnapshot,
            UUID actorPrincipalId, String actorType, JsonNode detail, OffsetDateTime occurredAt
    ) {}

    public record GrantHistory(UUID grantId, List<GrantEvent> items, int totalCount) {}
}
