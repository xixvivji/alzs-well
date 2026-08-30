package com.alzswell.system.api;

import java.time.OffsetDateTime;
import java.util.List;

public final class FeatureFlagResponses {
    private FeatureFlagResponses() {}

    public record FeatureFlag(
            String flagKey, String propertyKey, boolean desiredEnabled, boolean runtimeEnabled,
            boolean mutable, String safetyClass, String description, String environment,
            long version, OffsetDateTime updatedAt, boolean appliedToRuntime, boolean restartRequired,
            boolean externalActionExecuted
    ) {}

    public record FeatureFlagList(List<FeatureFlag> items, int totalCount, String environment) {}
}
