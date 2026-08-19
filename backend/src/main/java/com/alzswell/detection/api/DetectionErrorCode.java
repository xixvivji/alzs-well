package com.alzswell.detection.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DetectionErrorCode implements ErrorCode {
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_CUSTOMER_NOT_FOUND", "탐지 대상 고객을 찾을 수 없습니다."),
    BASELINE_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_BASELINE_NOT_FOUND", "기준선을 찾을 수 없습니다."),
    SIGNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_SIGNAL_NOT_FOUND", "변화신호를 찾을 수 없습니다."),
    SNAPSHOT_NOT_READY(HttpStatus.UNPROCESSABLE_ENTITY, "DETECTION_SNAPSHOT_NOT_READY",
            "기준선 계산에 필요한 합성 snapshot이 준비되지 않았습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    DetectionErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
