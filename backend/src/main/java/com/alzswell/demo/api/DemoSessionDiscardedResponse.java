package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 세션 폐기 결과입니다. 합성 fixture와 capability hash는 함께 삭제되며,
 * 감사 체인은 불변 로그로 보존됩니다.
 */
public record DemoSessionDiscardedResponse(
        UUID sessionId,
        UUID demoRunId,
        OffsetDateTime discardedAt,
        boolean syntheticDataDeleted,
        boolean externalActionCreated
) {
}
