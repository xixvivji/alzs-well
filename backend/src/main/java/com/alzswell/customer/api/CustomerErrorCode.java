package com.alzswell.customer.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CustomerErrorCode implements ErrorCode {
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "고객 정보를 찾을 수 없습니다."),
    CUSTOMER_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "CUSTOMER_VERSION_CONFLICT",
            "고객 설정이 다른 요청으로 변경되었습니다. 최신 상태를 다시 조회해 주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    CustomerErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
