package com.alzswell.alert.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AlertResponses {
    private AlertResponses() {}

    public record AlertSummary(
            UUID alertId, UUID signalId, String customerId, String state, String severity,
            String reasonCode, long version, OffsetDateTime deferredUntil,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    public record AlertList(List<AlertSummary> items, int totalCount) {}

    public record AlertDetail(
            AlertSummary alert, String baselineValue, String currentValue, String unit,
            String algorithmVersion, boolean financialActionExecuted, boolean externalNotificationSent
    ) {}

    public record ContextOption(String responseCode, String label, String description) {}
    public record ContextOptions(UUID alertId, String question, List<ContextOption> options) {}

    public record AlertTransition(
            UUID alertId, String previousState, String currentState, long version,
            String responseCode, OffsetDateTime deferredUntil, OffsetDateTime changedAt,
            boolean idempotencyReplayed, boolean financialActionExecuted,
            boolean externalNotificationSent
    ) {}

    public record AuditEvent(
            UUID auditEventId, String eventType, String previousState, String resultingState,
            Map<String, Object> detail, String integrityHash, OffsetDateTime createdAt
    ) {}

    public record AuditTrail(UUID alertId, List<AuditEvent> items, int totalCount) {}
}
