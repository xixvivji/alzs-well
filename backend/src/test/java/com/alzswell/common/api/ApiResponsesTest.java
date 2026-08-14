package com.alzswell.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiResponsesTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void successResponseUsesTheCurrentTraceId() {
        MDC.put("traceId", "trace-test-0001");

        ResponseEntity<ApiResponse<String>> response = ApiResponses.ok(
                "TEST_OK",
                "테스트 성공",
                "payload"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("payload");
        assertThat(response.getBody().errors()).isEmpty();
        assertThat(response.getBody().traceId()).isEqualTo("trace-test-0001");
    }
}
