package com.alzswell.recurring.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RecurringPaymentErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "RECURRING_PAYMENT_NOT_FOUND", "정기납부 정보를 찾을 수 없습니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "RECURRING_PAYMENT_VERSION_CONFLICT", "다른 요청이 알림 설정을 먼저 변경했습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "RECURRING_PAYMENT_DATE_RANGE_INVALID", "조회 기간은 시작일 이후 93일 이내여야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    RecurringPaymentErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
