package com.alzswell.privacy.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PrivacyErrorCode implements ErrorCode {
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","같은 멱등키가 다른 요청에 사용되었습니다."),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND,"PRIVACY_CUSTOMER_NOT_FOUND","개인정보 요청 대상 고객을 찾을 수 없습니다.");
    private final HttpStatus status; private final String code; private final String message;
    PrivacyErrorCode(HttpStatus status,String code,String message){this.status=status;this.code=code;this.message=message;}
    public HttpStatus status(){return status;} public String code(){return code;} public String message(){return message;}
}
