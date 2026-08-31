package com.alzswell.demo.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum P0WorkflowErrorCode implements ErrorCode {

    DEMO_RUN_STALE(
            HttpStatus.CONFLICT,
            "DEMO_RUN_STALE",
            "현재 데모 실행과 일치하지 않습니다. 최신 화면으로 다시 시도해 주세요."
    ),
    SYNTHETIC_CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SYNTHETIC_CUSTOMER_NOT_FOUND",
            "세션 내 합성 고객을 찾을 수 없습니다."
    ),
    ALERT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ALERT_NOT_FOUND",
            "세션 내 변화 알림을 찾을 수 없습니다."
    ),
    ALERT_CONTEXT_ALREADY_SUBMITTED(
            HttpStatus.CONFLICT,
            "ALERT_CONTEXT_ALREADY_SUBMITTED",
            "생활맥락 응답이 이미 제출되었습니다."
    ),
    ALERT_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "ALERT_VERSION_CONFLICT",
            "변화 알림이 다른 요청으로 변경되었습니다. 최신 상태를 다시 조회해 주세요."
    ),
    INVALID_STATE_TRANSITION(
            HttpStatus.CONFLICT,
            "INVALID_STATE_TRANSITION",
            "현재 상태에서는 요청한 변경을 수행할 수 없습니다."
    ),
    CASE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CASE_NOT_FOUND",
            "세션 내 보호업무 사건을 찾을 수 없습니다."
    ),
    CASE_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "CASE_VERSION_CONFLICT",
            "사건이 다른 요청으로 변경되었습니다. 최신 상태를 다시 조회해 주세요."
    ),
    IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_CONFLICT",
            "같은 Idempotency-Key를 다른 요청에 재사용할 수 없습니다."
    ),
    FOLLOW_UP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FOLLOW_UP_NOT_FOUND",
            "행원 후속 일정이 존재하지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    P0WorkflowErrorCode(HttpStatus status, String code, String message) {
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
