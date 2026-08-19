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
}
