package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DemoSessionResetResponse(
        UUID sessionId,
        UUID previousDemoRunId,
        UUID demoRunId,
        String scenarioSeed,
        String scenarioId,
        String snapshotHash,
        String alertId,
        int resetVersion,
        OffsetDateTime restoredAt,
        CommandMetadata command
) {
}
