package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DemoStaffCapabilityIssuedResponse(
        UUID sessionId,
        OffsetDateTime expiresAt
) {
}
