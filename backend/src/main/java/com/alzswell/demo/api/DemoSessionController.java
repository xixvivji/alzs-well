package com.alzswell.demo.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.DemoCapabilityService;
import com.alzswell.demo.application.DemoSessionService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoSessionController {

    private final DemoSessionService demoSessionService;
    private final DemoCapabilityService capabilityService;

    public DemoSessionController(
            DemoSessionService demoSessionService,
            DemoCapabilityService capabilityService
    ) {
        this.demoSessionService = demoSessionService;
        this.capabilityService = capabilityService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<DemoSessionCreatedResponse>> createSession() {
        DemoCapabilityService.IssuedCapabilities capabilities = capabilityService.issue();
        DemoSessionCreatedResponse created = demoSessionService.createSession(
                capabilities.customerCapabilityHash(),
                capabilities.staffCapabilityHash()
        );
        ResponseEntity<ApiResponse<DemoSessionCreatedResponse>> response = ApiResponses.created(
                "DEMO_SESSION_CREATED",
                "익명 데모 세션을 생성했습니다.",
                created
        );
        return ResponseEntity.status(response.getStatusCode())
                .header(DemoCapabilityService.CUSTOMER_RESPONSE_HEADER, capabilities.customerCapability())
                .header(DemoCapabilityService.STAFF_RESPONSE_HEADER, capabilities.staffCapability())
                .cacheControl(CacheControl.noStore())
                .body(response.getBody());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<DemoSessionResponse>> getSession(@PathVariable UUID sessionId) {
        return ApiResponses.ok(
                "DEMO_SESSION_RETRIEVED",
                "데모 세션 상태를 조회했습니다.",
                demoSessionService.getSession(sessionId)
        );
    }

    @GetMapping("/scenarios")
    public ResponseEntity<ApiResponse<DemoScenarioListResponse>> getScenarios() {
        return ApiResponses.ok(
                "DEMO_SCENARIO_LIST_RETRIEVED",
                "사용 가능한 합성 시나리오를 조회했습니다.",
                demoSessionService.getScenarios()
        );
    }

    @PostMapping("/sessions/{sessionId}/scenarios/{scenarioId}/ingest")
    public ResponseEntity<ApiResponse<DemoScenarioIngestedResponse>> ingest(
            @PathVariable UUID sessionId,
            @PathVariable String scenarioId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        DemoScenarioIngestedResponse ingested =
                demoSessionService.ingest(sessionId, scenarioId, idempotencyKey);
        ResponseEntity<ApiResponse<DemoScenarioIngestedResponse>> response = ApiResponses.created(
                "DEMO_SCENARIO_INGESTED",
                "고정 합성 시나리오를 적재했습니다.",
                ingested
        );
        return ResponseEntity.status(response.getStatusCode())
                .header(DemoCapabilityService.RUN_HEADER, ingested.demoRunId().toString())
                .body(response.getBody());
    }

    @PostMapping("/sessions/{sessionId}/reset")
    public ResponseEntity<ApiResponse<DemoSessionResetResponse>> reset(
            @PathVariable UUID sessionId,
            @RequestHeader(name = DemoCapabilityService.RUN_HEADER, required = false) UUID demoRunId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        DemoSessionResetResponse reset = demoSessionService.reset(sessionId, demoRunId, idempotencyKey);
        ResponseEntity<ApiResponse<DemoSessionResetResponse>> response = ApiResponses.ok(
                "DEMO_SESSION_RESET",
                "동일한 seed와 원시 snapshot으로 초기화했습니다.",
                reset
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
        if (reset.demoRunId() != null) {
            builder.header(DemoCapabilityService.RUN_HEADER, reset.demoRunId().toString());
        }
        return builder.body(response.getBody());
    }
}
