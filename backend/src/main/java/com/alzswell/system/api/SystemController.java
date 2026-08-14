package com.alzswell.system.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final String serviceName;
    private final boolean syntheticDataOnly;
    private final boolean externalActionsEnabled;

    public SystemController(
            @Value("${spring.application.name}") String serviceName,
            @Value("${app.guardrails.synthetic-data-only:true}") boolean syntheticDataOnly,
            @Value("${app.guardrails.external-actions-enabled:false}") boolean externalActionsEnabled
    ) {
        this.serviceName = serviceName;
        this.syntheticDataOnly = syntheticDataOnly;
        this.externalActionsEnabled = externalActionsEnabled;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<SystemHealthResponse>> health() {
        SystemHealthResponse response = new SystemHealthResponse(
                "UP",
                serviceName,
                syntheticDataOnly,
                externalActionsEnabled
        );
        return ApiResponses.ok("SYSTEM_HEALTHY", "서비스가 정상 동작 중입니다.", response);
    }
}
