package com.alzswell.system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.system.application.SystemInformationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SystemControllerTest {

    @Test
    void exposesTheMvpSafetyGuardrails() {
        SystemInformationService service = mock(SystemInformationService.class);
        when(service.health()).thenReturn(new SystemHealthResponse(
                "UP",
                "alzs-well-backend",
                true,
                false
        ));
        SystemController controller = new SystemController(service);

        ResponseEntity<ApiResponse<SystemHealthResponse>> response = controller.health();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SYSTEM_HEALTHY");
        assertThat(response.getBody().data().syntheticDataOnly()).isTrue();
        assertThat(response.getBody().data().externalActionsEnabled()).isFalse();
    }
}
