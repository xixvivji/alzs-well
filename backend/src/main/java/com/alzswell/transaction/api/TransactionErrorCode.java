package com.alzswell.transaction.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TransactionErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "거래 정보를 찾을 수 없습니다."),
    COUNTERPARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSACTION_COUNTERPARTY_NOT_FOUND", "거래 상대를 찾을 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "TRANSACTION_DATE_RANGE_INVALID", "거래 조회 기간은 시작일 이후 366일 이내여야 합니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "TRANSACTION_CURSOR_INVALID", "거래 cursor가 올바르지 않습니다."),
    INVALID_AMOUNT_RANGE(HttpStatus.BAD_REQUEST, "TRANSACTION_AMOUNT_RANGE_INVALID", "최소 금액은 최대 금액보다 클 수 없습니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "TRANSACTION_PREFERENCE_VERSION_CONFLICT", "거래 설정 버전이 변경되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    TransactionErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
