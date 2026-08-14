package com.alzswell.system.api;

import java.util.List;

public record PublicConfigResponse(
        String apiVersion,
        String dataMode,
        boolean syntheticDataOnly,
        boolean externalActionsEnabled,
        String networkMode,
        boolean externalEgressEnabled,
        boolean remoteModelEnabled,
        boolean syntheticProviderOnly,
        List<String> supportedScenarioIds,
        String defaultLocale,
        long demoSessionTtlSeconds,
        FeatureFlags featureFlags
) {
    public PublicConfigResponse {
        supportedScenarioIds = List.copyOf(supportedScenarioIds);
    }

    public record FeatureFlags(
            boolean optionalLlmEnabled,
            boolean templateFallbackEnabled,
            boolean trustedContactDeliveryEnabled
    ) {
    }
}
