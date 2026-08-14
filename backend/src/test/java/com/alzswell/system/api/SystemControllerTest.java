package com.alzswell.system.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.alzswell.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SystemControllerTest {

    @Test
    void exposesTheMvpSafetyGuardrails() {
        SystemController controller = new SystemController("alzs-well-backend", true, false);

        ResponseEntity<ApiResponse<SystemHealthResponse>> response = controller.health();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SYSTEM_HEALTHY");
        assertThat(response.getBody().data().syntheticDataOnly()).isTrue();
        assertThat(response.getBody().data().externalActionsEnabled()).isFalse();
    }
}
