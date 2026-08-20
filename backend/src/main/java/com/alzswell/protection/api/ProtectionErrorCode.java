package com.alzswell.protection.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProtectionErrorCode implements ErrorCode {
    ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "PROTECTION_ACTION_NOT_FOUND", "보호수단을 찾을 수 없습니다."),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "PROTECTION_CUSTOMER_NOT_FOUND", "고객을 찾을 수 없습니다.");
    private final HttpStatus status; private final String code; private final String message;
    ProtectionErrorCode(HttpStatus status, String code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
