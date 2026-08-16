package com.alzswell.system.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SystemErrorCode implements ErrorCode {

    SYSTEM_NOT_READY(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SYSTEM_NOT_READY",
            "공개 데모 실행 준비가 완료되지 않았습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    SystemErrorCode(HttpStatus status, String code, String message) {
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
