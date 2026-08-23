package com.alzswell.card.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CardErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "카드 정보를 찾을 수 없습니다."),
    CURSOR_INVALID(HttpStatus.BAD_REQUEST, "CARD_TRANSACTION_CURSOR_INVALID", "카드 이용내역 cursor가 올바르지 않습니다."),
    DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "CARD_TRANSACTION_DATE_RANGE_INVALID", "카드 이용내역 조회 기간은 366일 이내여야 합니다."),
    STATEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CARD_STATEMENT_NOT_FOUND", "카드 청구 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CardErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
