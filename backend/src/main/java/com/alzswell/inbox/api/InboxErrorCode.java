package com.alzswell.inbox.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InboxErrorCode implements ErrorCode {
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "INBOX_CUSTOMER_NOT_FOUND", "고객 정보를 찾을 수 없습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "INBOX_MESSAGE_NOT_FOUND", "알림을 찾을 수 없습니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "INBOX_VERSION_CONFLICT", "알림 상태가 변경되었습니다. 다시 조회해 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "INBOX_INVALID_CURSOR", "알림 목록 커서가 올바르지 않습니다."),
    TEMPLATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "NOTIFICATION_TEMPLATE_NOT_ALLOWED", "승인되지 않은 알림 템플릿입니다.");
    private final HttpStatus status; private final String code; private final String message;
    InboxErrorCode(HttpStatus status, String code, String message) { this.status=status; this.code=code; this.message=message; }
    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
