package com.alzswell.account.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AccountErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계좌 정보를 찾을 수 없습니다."),
    STATEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_STATEMENT_NOT_FOUND", "거래명세서를 찾을 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "ACCOUNT_BALANCE_DATE_RANGE_INVALID", "잔액 조회 기간은 시작일 이후 366일 이내여야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AccountErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
