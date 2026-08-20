package com.alzswell.consent.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ConsentErrorCode implements ErrorCode {
    CONSENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONSENT_NOT_FOUND", "동의를 찾을 수 없습니다."),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CONSENT_CUSTOMER_NOT_FOUND", "고객을 찾을 수 없습니다."),
    CONSENT_STATE_CONFLICT(HttpStatus.CONFLICT, "CONSENT_STATE_CONFLICT", "동의 상태 또는 버전이 충돌합니다.");
    private final HttpStatus status; private final String code; private final String message;
    ConsentErrorCode(HttpStatus status, String code, String message) {
        this.status=status; this.code=code; this.message=message;
    }
    @Override public HttpStatus status(){return status;}
    @Override public String code(){return code;}
    @Override public String message(){return message;}
}
