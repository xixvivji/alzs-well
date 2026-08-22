package com.alzswell.financialoverview.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FinancialOverviewErrorCode implements ErrorCode {
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "FINANCIAL_OVERVIEW_DATE_RANGE_INVALID",
            "조회 기간은 시작일 이후 허용 범위 이내여야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    FinancialOverviewErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
