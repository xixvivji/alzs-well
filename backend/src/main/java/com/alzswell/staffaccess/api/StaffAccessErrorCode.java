package com.alzswell.staffaccess.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum StaffAccessErrorCode implements ErrorCode {
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "STAFF_ACCESS_DENIED", "해당 고객과 업무 범위에 유효한 직원 접근권이 없습니다."),
    GRANT_NOT_FOUND(HttpStatus.NOT_FOUND, "STAFF_ACCESS_GRANT_NOT_FOUND", "직원 접근권을 찾을 수 없습니다."),
    PRINCIPAL_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "STAFF_ACCESS_PRINCIPAL_NOT_ELIGIBLE", "활성 보호업무 직원만 접근권을 받을 수 있습니다."),
    STATE_CONFLICT(HttpStatus.CONFLICT, "STAFF_ACCESS_GRANT_STATE_CONFLICT", "직원 접근권 상태 또는 버전이 충돌합니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "STAFF_ACCESS_GRANT_IDEMPOTENCY_CONFLICT", "같은 멱등키가 다른 접근권 요청에 사용되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    StaffAccessErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
