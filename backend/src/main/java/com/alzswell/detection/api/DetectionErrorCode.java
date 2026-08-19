package com.alzswell.detection.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DetectionErrorCode implements ErrorCode {
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_CUSTOMER_NOT_FOUND", "탐지 대상 고객을 찾을 수 없습니다."),
    BASELINE_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_BASELINE_NOT_FOUND", "기준선을 찾을 수 없습니다."),
    SIGNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_SIGNAL_NOT_FOUND", "변화신호를 찾을 수 없습니다."),
    SNAPSHOT_NOT_READY(HttpStatus.UNPROCESSABLE_ENTITY, "DETECTION_SNAPSHOT_NOT_READY",
            "기준선 계산에 필요한 합성 snapshot이 준비되지 않았습니다."),
    DATASET_NOT_FOUND(HttpStatus.NOT_FOUND, "SYNTHETIC_DATASET_NOT_FOUND", "합성 데이터셋을 찾을 수 없습니다."),
    DATASET_STATE_CONFLICT(HttpStatus.CONFLICT, "SYNTHETIC_DATASET_STATE_CONFLICT",
            "현재 상태에서는 합성 데이터셋 작업을 수행할 수 없습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "DETECTION_IDEMPOTENCY_CONFLICT",
            "같은 멱등키가 다른 탐지 실행 요청에 사용되었습니다."),
    DETECTION_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_RUN_NOT_FOUND", "탐지 실행을 찾을 수 없습니다."),
    PROMOTION_NOT_FOUND(HttpStatus.NOT_FOUND, "DETECTION_PROMOTION_NOT_FOUND", "탐지 실행 승격 결과를 찾을 수 없습니다."),
    PROMOTION_STATE_CONFLICT(HttpStatus.CONFLICT, "DETECTION_PROMOTION_STATE_CONFLICT",
            "완료된 탐지 실행만 승격할 수 있습니다."),
    PROMOTION_SOURCE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "DETECTION_PROMOTION_SOURCE_INVALID",
            "탐지 결과와 합성 원본의 특징이 일치하지 않습니다."),
    PROMOTION_BASELINE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "DETECTION_PROMOTION_BASELINE_MISMATCH",
            "운영형 신호로 승격할 기준선 snapshot이 없거나 값이 일치하지 않습니다.");

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
