package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Capability 원문은 보안상 JSON 본문이 아니라 일회성 응답 헤더로만 반환한다. */
public record DemoSessionCreatedResponse(
        UUID sessionId,
        String scenarioSeed,
        UUID demoRunId,
        OffsetDateTime expiresAt,
        int resetVersion,
        String dataMode
) {
}
