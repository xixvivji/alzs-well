package com.alzswell.support.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SupportErrorCode implements ErrorCode {
    INVALID_NOTICE_PERIOD(
            HttpStatus.BAD_REQUEST,
            "SUPPORT_NOTICE_PERIOD_INVALID",
            "공지 조회 기간은 시작일이 종료일보다 늦을 수 없고 최대 366일이어야 합니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    SupportErrorCode(HttpStatus status, String code, String message) {
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
