package com.alzswell.detection.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DetectionResponses {
    private DetectionResponses() {}

    public record DatePeriod(LocalDate from, LocalDate to) {}

    public record BaselineSummary(
            UUID baselineId, String customerId, String featureCode, String baselineValue,
            String currentValue, String unit, String readiness, String comparisonText,
            String algorithmVersion, OffsetDateTime calculatedAt, long version
    ) {}

    public record BaselineList(List<BaselineSummary> items, int total) {}

    public record BaselineDetail(
            BaselineSummary baseline, DatePeriod baselinePeriod, DatePeriod observationPeriod,
            String snapshotHash
    ) {}

    public record BaselineFeature(
            UUID featureId, String featureCode, String value, String unit, DatePeriod observedPeriod,
            int sampleCount, String snapshotHash
    ) {}

    public record BaselineFeatureList(UUID baselineId, List<BaselineFeature> items, int total) {}

    public record BaselineCalculation(
            UUID calculationId, String customerId, String status, String algorithmVersion,
            int baselinesEvaluated, int signalsEvaluated, boolean reusedCurrentSnapshot,
            OffsetDateTime requestedAt, OffsetDateTime completedAt, String resultSnapshotHash,
            String requestHash, boolean idempotencyReplayed, boolean externalExecutionCreated
    ) {}

    public record SignalSummary(
            UUID signalId, String customerId, UUID baselineId, String signalType, String severity,
            String baselineValue, String currentValue, String unit, String reasonCode, String status,
            String algorithmVersion, OffsetDateTime detectedAt
    ) {}

    public record SignalList(List<SignalSummary> items, int total) {}
    public record SignalDetail(SignalSummary signal, String snapshotHash) {}

    public record SignalEvidence(
            UUID evidenceId, String evidenceType, String sourceReference, OffsetDateTime occurredAt,
            String amount, String currency, String description, String integrityHash
    ) {}

    public record SignalEvidenceList(UUID signalId, List<SignalEvidence> items, int total) {}
}
