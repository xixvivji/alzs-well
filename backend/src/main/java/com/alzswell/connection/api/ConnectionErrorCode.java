package com.alzswell.connection.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ConnectionErrorCode implements ErrorCode {
    INSTITUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONNECTION_INSTITUTION_NOT_FOUND", "금융기관을 찾을 수 없습니다."),
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND", "금융기관 연결을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ConnectionErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
