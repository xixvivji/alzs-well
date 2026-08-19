package com.alzswell.detection.api;

import com.alzswell.detection.api.SyntheticDatasetRequests.FeatureObservation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SyntheticDatasetResponses {
    private SyntheticDatasetResponses() {}

    public record DatasetSummary(
            UUID datasetId, String customerId, String datasetName, String status,
            int observationCount, int evidenceCount, String payloadHash, long version,
            OffsetDateTime createdAt, OffsetDateTime validatedAt, OffsetDateTime ingestedAt
    ) {}

    public record DatasetDetail(
            DatasetSummary dataset, List<FeatureObservation> observations, List<String> validationErrors,
            boolean syntheticData
    ) {}

    public record DatasetValidation(
            UUID datasetId, String status, List<String> errors, long version, OffsetDateTime validatedAt
    ) {}

    public record DatasetIngestion(
            UUID datasetId, String status, String payloadHash, long version, OffsetDateTime ingestedAt,
            boolean externalExecutionCreated
    ) {}

    public record DetectedSignal(
            String featureCode, String severity, String baselineValue, String currentValue,
            String unit, String reasonCode, List<String> evidenceReferences
    ) {}

    public record DetectionRun(
            UUID detectionRunId, UUID datasetId, String customerId, String status,
            String algorithmVersion, List<DetectedSignal> signals, int signalCount,
            OffsetDateTime startedAt, OffsetDateTime completedAt, String inputPayloadHash,
            String resultHash, String requestHash, boolean idempotencyReplayed,
            boolean advisoryAiUsed, boolean externalExecutionCreated
    ) {}
}
