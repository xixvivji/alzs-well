package com.alzswell.transfer.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TransferTemplateErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSFER_TEMPLATE_NOT_FOUND",
            "저장 이체 양식을 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSFER_TEMPLATE_RESOURCE_NOT_FOUND",
            "양식에 지정할 활성 합성 계좌 또는 마스킹 수취인을 찾을 수 없습니다."),
    LIMIT_EXCEEDED(HttpStatus.CONFLICT, "TRANSFER_TEMPLATE_LIMIT_EXCEEDED",
            "고객당 저장 이체 양식은 최대 20개입니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "TRANSFER_TEMPLATE_IDEMPOTENCY_CONFLICT",
            "같은 멱등키의 요청 내용 또는 처리 상태가 충돌합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    TransferTemplateErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
