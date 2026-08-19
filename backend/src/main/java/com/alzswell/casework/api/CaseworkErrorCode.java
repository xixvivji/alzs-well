package com.alzswell.casework.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CaseworkErrorCode implements ErrorCode {
    CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "STAFF_CASE_NOT_FOUND", "운영형 행원 사건을 찾을 수 없습니다."),
    CASE_STATE_CONFLICT(HttpStatus.CONFLICT, "STAFF_CASE_STATE_CONFLICT",
            "현재 사건 상태 또는 버전에서는 요청을 처리할 수 없습니다."),
    REVIEW_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "STAFF_CASE_REVIEW_IDEMPOTENCY_CONFLICT",
            "같은 멱등키가 다른 사건 검토 요청에 사용되었습니다."),
    NOTE_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "STAFF_CASE_NOTE_IDEMPOTENCY_CONFLICT",
            "같은 멱등키가 다른 내부 메모 요청에 사용되었습니다."),
    GUIDANCE_ALREADY_APPROVED(HttpStatus.CONFLICT, "STAFF_GUIDANCE_ALREADY_APPROVED",
            "안내계획이 이미 승인되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CaseworkErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
