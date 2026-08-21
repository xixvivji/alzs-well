package com.alzswell.compliance.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ComplianceResponses {
    private ComplianceResponses() {}

    public record AuditEvent(
            String eventId, String sourceType, String sourceId, String eventType, String actorSubject,
            String customerId, String targetType, String targetId, String beforeState, String afterState,
            JsonNode detail, String integrityHash, OffsetDateTime occurredAt, boolean immutable
    ) {}
    public record AuditEventList(List<AuditEvent> items, String nextCursor, boolean hasNext, int count) {}
    public record TraceStep(String stepType, String referenceId, String state, String hash, JsonNode detail,
                            OffsetDateTime occurredAt) {}
    public record DecisionTrace(
            UUID decisionId, String decisionType, String customerId, String policyVersion,
            String algorithmVersion, List<TraceStep> steps, boolean complete,
            boolean externalProviderCalled, boolean externalActionExecuted
    ) {}
    public record ProvenanceNode(String resourceType, String resourceId, String version,
                                 String integrityHash, String relationship) {}
    public record DataProvenance(
            String resourceType, UUID resourceId, List<ProvenanceNode> lineage,
            boolean syntheticData, boolean externalProviderCalled, OffsetDateTime observedAt
    ) {}
    public record AuditExportRequest(
            UUID requestId, String status, OffsetDateTime from, OffsetDateTime to, List<String> sourceTypes,
            String purposeCode, String approvalReference, OffsetDateTime requestedAt, OffsetDateTime expiresAt,
            boolean artifactCreated, boolean downloadEnabled, boolean externalTransferExecuted
    ) {}
}
