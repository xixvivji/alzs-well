package com.alzswell.holding.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FinancialHoldingErrorCode implements ErrorCode {
    DEPOSIT_NOT_FOUND(HttpStatus.NOT_FOUND,"DEPOSIT_HOLDING_NOT_FOUND","예금 보유 정보를 찾을 수 없습니다."),
    LOAN_NOT_FOUND(HttpStatus.NOT_FOUND,"LOAN_HOLDING_NOT_FOUND","대출 보유 정보를 찾을 수 없습니다."),
    INVESTMENT_NOT_FOUND(HttpStatus.NOT_FOUND,"INVESTMENT_ACCOUNT_NOT_FOUND","투자계좌 정보를 찾을 수 없습니다."),
    PENSION_NOT_FOUND(HttpStatus.NOT_FOUND,"PENSION_HOLDING_NOT_FOUND","연금 보유 정보를 찾을 수 없습니다."),
    TRUST_NOT_FOUND(HttpStatus.NOT_FOUND,"TRUST_HOLDING_NOT_FOUND","신탁 보유 정보를 찾을 수 없습니다.");
    private final HttpStatus status; private final String code; private final String message;
    FinancialHoldingErrorCode(HttpStatus status,String code,String message){this.status=status;this.code=code;this.message=message;}
    public HttpStatus status(){return status;} public String code(){return code;} public String message(){return message;}
}
