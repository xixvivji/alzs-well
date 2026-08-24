package com.alzswell.alert.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AlertErrorCode implements ErrorCode {
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "운영형 경보를 찾을 수 없습니다."),
    STATE_CONFLICT(HttpStatus.CONFLICT, "ALERT_STATE_CONFLICT", "현재 상태에서는 경보를 변경할 수 없습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "ALERT_IDEMPOTENCY_CONFLICT",
            "같은 멱등키가 다른 생활맥락 응답에 사용되었습니다."),
    APPEAL_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "ALERT_APPEAL_ALREADY_SUBMITTED",
            "이 경보에는 이미 재검토 요청이 등록되었습니다."),
    APPEAL_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "ALERT_APPEAL_IDEMPOTENCY_CONFLICT",
            "같은 멱등키가 다른 경보 재검토 요청에 사용되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AlertErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
