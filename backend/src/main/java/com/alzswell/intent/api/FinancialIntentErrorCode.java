package com.alzswell.intent.api;
import com.alzswell.common.exception.ErrorCode; import org.springframework.http.HttpStatus;
public enum FinancialIntentErrorCode implements ErrorCode {NOT_FOUND(HttpStatus.NOT_FOUND,"FINANCIAL_INTENT_NOT_FOUND","금융생활 의향을 찾을 수 없습니다."),
 INVALID_STATE(HttpStatus.CONFLICT,"FINANCIAL_INTENT_INVALID_STATE","현재 상태에서는 요청을 처리할 수 없습니다."),
 VERSION_CONFLICT(HttpStatus.CONFLICT,"FINANCIAL_INTENT_VERSION_CONFLICT","의향 버전이 변경되었습니다."),
 APPROVED_ALREADY_EXISTS(HttpStatus.CONFLICT,"FINANCIAL_INTENT_APPROVED_ALREADY_EXISTS","기존 승인 의향을 먼저 철회해야 새 의향을 승인할 수 있습니다."),
 IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT,"FINANCIAL_INTENT_IDEMPOTENCY_CONFLICT","같은 멱등키가 다른 요청에 사용되었습니다.");
 private final HttpStatus s;private final String c,m;FinancialIntentErrorCode(HttpStatus s,String c,String m){this.s=s;this.c=c;this.m=m;}
 public HttpStatus status(){return s;}public String code(){return c;}public String message(){return m;}}
