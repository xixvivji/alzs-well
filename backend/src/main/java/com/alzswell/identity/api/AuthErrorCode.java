package com.alzswell.identity.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "아이디 또는 비밀번호를 확인해 주세요."),
    LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOGIN_RATE_LIMITED", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 인증 토큰입니다."),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REVOKED", "종료된 인증 세션입니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND,"AUTH_SESSION_NOT_FOUND","본인의 인증 세션을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
