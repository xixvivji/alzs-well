package com.alzswell.detection.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SyntheticDatasetRequests {
    private SyntheticDatasetRequests() {}

    public record CreateDatasetCommand(
            @NotBlank @Size(max = 100) String datasetName,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$") String customerId,
            @NotEmpty @Size(max = 50) List<@Valid FeatureObservation> observations
    ) {}

    public record FeatureObservation(
            @NotBlank @Pattern(regexp = "MISSED_RECURRING_PAYMENT|DUPLICATE_TRANSFER|REPEATED_CONFIRMATION")
            String featureCode,
            @NotNull @DecimalMin("0") BigDecimal baselineValue,
            @NotNull @DecimalMin("0") BigDecimal currentValue,
            @NotBlank @Pattern(regexp = "COUNT") String unit,
            @NotEmpty @Size(max = 20) List<@Valid EvidenceInput> evidence
    ) {}

    public record EvidenceInput(
            @NotBlank @Pattern(regexp = "TRANSACTION|INTERACTION") String evidenceType,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+") String sourceReference,
            @NotNull OffsetDateTime occurredAt,
            @DecimalMin("0") BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Size(max = 300) String description
    ) {}

    public record CreateDetectionRunCommand(@NotNull UUID datasetId) {}
}
