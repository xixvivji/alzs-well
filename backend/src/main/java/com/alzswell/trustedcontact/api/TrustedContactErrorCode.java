package com.alzswell.trustedcontact.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TrustedContactErrorCode implements ErrorCode {
    CONTACT_NOT_FOUND(HttpStatus.NOT_FOUND,"TRUSTED_CONTACT_NOT_FOUND","신뢰연락인을 찾을 수 없습니다."),
    CONSENT_NOT_ELIGIBLE(HttpStatus.CONFLICT,"TRUSTED_CONTACT_CONSENT_NOT_ELIGIBLE","유효한 최소정보 동의가 필요합니다."),
    STATE_CONFLICT(HttpStatus.CONFLICT,"TRUSTED_CONTACT_STATE_CONFLICT","신뢰연락인 상태 또는 버전이 충돌합니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT,"TRUSTED_CONTACT_IDEMPOTENCY_CONFLICT","같은 멱등키가 다른 요청에 사용되었습니다."),
    INVALID_MASKED_CONTACT(HttpStatus.BAD_REQUEST,"TRUSTED_CONTACT_INVALID_MASK","안전하게 마스킹된 연락처 형식이 아닙니다."),
    INVALID_SCOPE(HttpStatus.BAD_REQUEST,"TRUSTED_CONTACT_INVALID_SCOPE","신뢰연락인 정보 범위가 올바르지 않습니다.");
    private final HttpStatus status;private final String code;private final String message;
    TrustedContactErrorCode(HttpStatus status,String code,String message){this.status=status;this.code=code;this.message=message;}
    @Override public HttpStatus status(){return status;} @Override public String code(){return code;}
    @Override public String message(){return message;}
}
