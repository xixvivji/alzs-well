package com.alzswell.casework.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CaseworkResponses {
    private CaseworkResponses() {}

    public record CaseSummary(
            UUID caseId, UUID alertId, UUID signalId, String customerId, String reviewPriority,
            String taskStatus, long version, String assignedTeam, String assignedTo,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    public record CaseQueue(List<CaseSummary> items, int count, UUID nextCursor) {}

    public record CaseDetail(
            CaseSummary caseSummary, String alertState, String reasonCode, String severity,
            String customerResponseCode, UUID guidancePlanId, List<String> selectedActionCodes,
            boolean financialActionExecuted, boolean externalNotificationSent
    ) {}

    public record CaseTransition(
            UUID caseId, String previousStatus, String currentStatus, long version,
            String actionCode, OffsetDateTime changedAt, boolean idempotencyReplayed,
            boolean financialActionExecuted, boolean externalNotificationSent
    ) {}

    public record GuidancePlan(
            UUID guidancePlanId, UUID caseId, List<String> selectedActionCodes,
            String approvedBy, OffsetDateTime approvedAt, long caseVersion,
            boolean delivered, boolean externalExecutionCreated
    ) {}

    public record EvidenceItem(
            UUID evidenceId, String evidenceType, String sourceReference, OffsetDateTime occurredAt,
            String amount, String currency, String description, String integrityHash
    ) {}

    public record CaseEvidence(
            UUID caseId, UUID signalId, String reasonCode, String baselineValue, String currentValue,
            String unit, List<EvidenceItem> items, int count, boolean syntheticData
    ) {}

    public record TimelineEvent(
            String eventType, String actorSubject, String previousState, String resultingState,
            String summary, OffsetDateTime occurredAt
    ) {}

    public record CaseTimeline(UUID caseId, List<TimelineEvent> items, int count) {}

    public record CaseNote(
            UUID noteId, UUID caseId, String noteText, String createdBy, String integrityHash,
            OffsetDateTime createdAt, boolean idempotencyReplayed
    ) {}

    public record CaseNotes(UUID caseId, List<CaseNote> items, int count) {}

    public record FollowUp(
            UUID followUpId, UUID caseId, String followUpType, String status,
            OffsetDateTime scheduledAt, String purpose, String outcome, long version,
            String createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            boolean idempotencyReplayed, boolean externalContactExecuted
    ) {}

    public record FollowUps(UUID caseId, List<FollowUp> items, int count) {}

    public record CaseOverride(
            UUID overrideEventId, UUID caseId, String reasonCode, String policyVersion,
            String previousStatus, String currentStatus, long version, String reviewedBy,
            OffsetDateTime reviewedAt, boolean idempotencyReplayed,
            boolean financialActionExecuted, boolean externalNotificationSent
    ) {}
}
