package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DemoSessionResponse(
        UUID sessionId,
        UUID demoRunId,
        String scenarioSeed,
        String scenarioId,
        String status,
        int resetVersion,
        String snapshotHash,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        String dataMode
) {
}
