package com.alzswell.demo.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DemoErrorCode implements ErrorCode {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "DEMO_SESSION_NOT_FOUND", "데모 세션을 찾을 수 없습니다."),
    CAPABILITY_SCOPE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "DEMO_CAPABILITY_SCOPE_FORBIDDEN",
            "이 capability로는 해당 데모 역할의 기능을 사용할 수 없습니다."
    ),
    RUN_STALE(HttpStatus.CONFLICT, "DEMO_RUN_STALE", "현재 실행과 일치하지 않는 demoRunId입니다."),
    IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_CONFLICT",
            "같은 Idempotency-Key를 다른 요청에 재사용할 수 없습니다."
    ),
    SESSION_RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS,
            "DEMO_SESSION_RATE_LIMITED",
            "현재 생성 가능한 데모 세션 수를 초과했습니다. 잠시 후 다시 시도해 주세요."
    ),
    SYNTHETIC_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SYNTHETIC_ACCOUNT_NOT_FOUND",
            "세션 내 합성 계좌를 찾을 수 없습니다."
    ),
    SYNTHETIC_FIXTURE_NOT_READY(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "SYNTHETIC_FIXTURE_NOT_READY",
            "합성 시나리오를 먼저 적재해 주세요."
    ),
    AI_INTENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "DEMO_AI_INTENT_NOT_FOUND",
            "이 데모 실행에서 저장한 금융생활 의향이 없습니다."
    ),
    AI_INTENT_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "DEMO_AI_INTENT_VERSION_CONFLICT",
            "금융생활 의향이 다른 화면에서 변경되었습니다. 다시 확인해 주세요."
    ),
    AI_INTENT_INVALID_STATE(
            HttpStatus.CONFLICT,
            "DEMO_AI_INTENT_INVALID_STATE",
            "현재 상태에서는 금융생활 의향을 변경할 수 없습니다."
    ),
    SCENARIO_NOT_SUPPORTED(
            HttpStatus.BAD_REQUEST,
            "DEMO_SCENARIO_NOT_SUPPORTED",
            "지원하지 않는 합성 시나리오입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    DemoErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
