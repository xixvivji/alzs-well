package com.alzswell.fx.api;
import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
public enum FxErrorCode implements ErrorCode {
 RATE_NOT_FOUND(HttpStatus.NOT_FOUND,"FX_RATE_NOT_FOUND","지원하는 합성 환율을 찾을 수 없습니다."),
 INPUT_INVALID(HttpStatus.BAD_REQUEST,"FX_SIMULATION_INPUT_INVALID","KRW와 지원 외화 사이의 유효한 환전 금액을 입력해야 합니다.");
 private final HttpStatus status;private final String code;private final String message;
 FxErrorCode(HttpStatus s,String c,String m){status=s;code=c;message=m;} public HttpStatus status(){return status;}public String code(){return code;}public String message(){return message;}
}
