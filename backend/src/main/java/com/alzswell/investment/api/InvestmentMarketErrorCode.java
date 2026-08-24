package com.alzswell.investment.api;
import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
public enum InvestmentMarketErrorCode implements ErrorCode {
 ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND,"INVESTMENT_ACCOUNT_NOT_FOUND","투자계좌 정보를 찾을 수 없습니다."),
 INSTRUMENT_NOT_FOUND(HttpStatus.NOT_FOUND,"MARKET_INSTRUMENT_NOT_FOUND","합성 종목 정보를 찾을 수 없습니다."),
 CHART_RANGE_INVALID(HttpStatus.BAD_REQUEST,"MARKET_CHART_RANGE_INVALID","차트 기간은 366일 이내여야 합니다."),
 WATCHLIST_INVALID(HttpStatus.BAD_REQUEST,"INVESTMENT_WATCHLIST_INVALID","관심종목은 중복 없이 활성 합성 종목만 지정해야 합니다."),
 WATCHLIST_VERSION_CONFLICT(HttpStatus.CONFLICT,"INVESTMENT_WATCHLIST_VERSION_CONFLICT","관심종목 버전이 변경됐습니다."),
 IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT,"INVESTMENT_WATCHLIST_IDEMPOTENCY_CONFLICT","같은 멱등키의 요청 내용 또는 처리 상태가 충돌합니다.");
 private final HttpStatus status;private final String code;private final String message;
 InvestmentMarketErrorCode(HttpStatus s,String c,String m){status=s;code=c;message=m;}
 public HttpStatus status(){return status;}public String code(){return code;}public String message(){return message;}
}
