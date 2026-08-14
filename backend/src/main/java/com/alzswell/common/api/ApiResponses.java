package com.alzswell.common.api;

import com.alzswell.common.exception.ErrorCode;
import com.alzswell.common.web.TraceIdContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ApiResponses {

    private ApiResponses() {
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ok("COMMON_OK", "요청을 성공적으로 처리했습니다.", data);
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String code, String message, T data) {
        return success(HttpStatus.OK, code, message, data);
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(String code, String message, T data) {
        return success(HttpStatus.CREATED, code, message, data);
    }

    public static ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.message(), List.of());
    }

    public static ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode, String message) {
        return error(errorCode, message, List.of());
    }

    public static ResponseEntity<ApiResponse<Void>> error(
            ErrorCode errorCode,
            String message,
            List<FieldViolation> errors
    ) {
        ApiResponse<Void> body = ApiResponse.failure(
                errorCode.status(),
                errorCode.code(),
                message,
                errors,
                TraceIdContext.currentOrCreate()
        );
        return ResponseEntity.status(errorCode.status()).body(body);
    }

    private static <T> ResponseEntity<ApiResponse<T>> success(
            HttpStatus status,
            String code,
            String message,
            T data
    ) {
        ApiResponse<T> body = ApiResponse.success(
                status,
                code,
                message,
                data,
                TraceIdContext.currentOrCreate()
        );
        return ResponseEntity.status(status).body(body);
    }
}
