package com.alzswell.transfer.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TransferPreviewErrorCode implements ErrorCode {
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSFER_PREVIEW_RESOURCE_NOT_FOUND",
            "이체 미리보기에 필요한 합성 계좌·수취인·한도 정보를 찾을 수 없습니다."),
    LIMIT_NOT_AVAILABLE(HttpStatus.CONFLICT, "TRANSFER_PREVIEW_LIMIT_NOT_AVAILABLE",
            "현재 기준일에 사용할 수 있는 합성 이체한도가 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    TransferPreviewErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
