package com.alzswell.system.api;

import com.alzswell.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FeatureFlagErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "FEATURE_FLAG_NOT_FOUND", "기능 플래그를 찾을 수 없습니다."),
    IMMUTABLE(HttpStatus.CONFLICT, "FEATURE_FLAG_IMMUTABLE", "안전 가드레일 플래그는 API로 변경할 수 없습니다."),
    PUBLIC_ENABLE_FORBIDDEN(HttpStatus.CONFLICT, "FEATURE_FLAG_PUBLIC_ENABLE_FORBIDDEN",
            "공개 배포에서는 사설 기능을 활성화할 수 없습니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "FEATURE_FLAG_VERSION_CONFLICT",
            "기능 플래그가 다른 요청에 의해 변경되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
    FeatureFlagErrorCode(HttpStatus status, String code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
