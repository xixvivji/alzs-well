package com.alzswell.compliance.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ComplianceErrorCode implements ErrorCode {
    AUDIT_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUDIT_EVENT_NOT_FOUND", "감사이벤트를 찾을 수 없습니다."),
    DECISION_TRACE_NOT_FOUND(HttpStatus.NOT_FOUND, "DECISION_TRACE_NOT_FOUND", "판단 추적 정보를 찾을 수 없습니다."),
    PROVENANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "DATA_PROVENANCE_NOT_FOUND", "데이터 출처 정보를 찾을 수 없습니다."),
    RESOURCE_TYPE_UNSUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "DATA_PROVENANCE_RESOURCE_TYPE_UNSUPPORTED",
            "지원하지 않는 데이터 출처 resourceType입니다."),
    CURSOR_INVALID(HttpStatus.BAD_REQUEST, "AUDIT_CURSOR_INVALID", "감사이벤트 cursor 형식이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
    ComplianceErrorCode(HttpStatus status, String code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
