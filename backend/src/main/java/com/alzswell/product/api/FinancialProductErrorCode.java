package com.alzswell.product.api;
import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
public enum FinancialProductErrorCode implements ErrorCode {
 PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND,"FINANCIAL_PRODUCT_NOT_FOUND","금융상품 정보를 찾을 수 없습니다."),
 RATE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST,"DEPOSIT_PRODUCT_RATE_NOT_AVAILABLE","선택한 기간에 적용할 합성 금리가 없습니다."),
 INPUT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST,"FINANCIAL_PRODUCT_SIMULATION_OUT_OF_RANGE","금액·기간·금리가 상품 허용 범위를 벗어났습니다."),
 HOLDING_NOT_FOUND(HttpStatus.NOT_FOUND,"DEPOSIT_HOLDING_NOT_FOUND","예금 보유 정보를 찾을 수 없습니다.");
 private final HttpStatus status;private final String code;private final String message;
 FinancialProductErrorCode(HttpStatus s,String c,String m){status=s;code=c;message=m;}
 public HttpStatus status(){return status;}public String code(){return code;}public String message(){return message;}
}
