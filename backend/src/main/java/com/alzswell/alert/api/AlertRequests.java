package com.alzswell.alert.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

public final class AlertRequests {
    private AlertRequests() {}

    public record ContextResponseCommand(
            @NotBlank @Pattern(regexp = "EXPECTED_CHANGE|UNRECOGNIZED|NOT_SURE") String responseCode,
            @Positive long expectedVersion
    ) {}

    public record DeferCommand(
            @NotNull @Future OffsetDateTime deferredUntil,
            @Positive long expectedVersion
    ) {}
}
