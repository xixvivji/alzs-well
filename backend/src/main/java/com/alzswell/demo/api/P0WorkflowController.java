package com.alzswell.demo.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.DemoCapabilityService;
import com.alzswell.demo.api.P0WorkflowRequests.CaseReviewCommand;
import com.alzswell.demo.api.P0WorkflowRequests.ContextCommand;
import com.alzswell.demo.api.P0WorkflowRequests.GuidancePlanCommand;
import com.alzswell.demo.application.P0WorkflowService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/sessions/{sessionId}")
public class P0WorkflowController {

    public static final String DEMO_RUN_HEADER = "X-Demo-Run-Id";

    private final P0WorkflowService workflowService;

    public P0WorkflowController(P0WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/customers/{customerId}/alerts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> alertList(
            @PathVariable UUID sessionId,
            @PathVariable String customerId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId
    ) {
        return withRun(ApiResponses.ok(
                "ALERT_LIST_RETRIEVED",
                "금융생활 변화 알림을 조회했습니다.",
                workflowService.alertList(sessionId, demoRunId, customerId)
        ), demoRunId);
    }

    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> alertDetail(
            @PathVariable UUID sessionId,
            @PathVariable String alertId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId
    ) {
        return withRun(ApiResponses.ok(
                "ALERT_DETAIL_RETRIEVED",
                "변화 알림 상세를 조회했습니다.",
                workflowService.alertDetail(sessionId, demoRunId, alertId)
        ), demoRunId);
    }

    @PostMapping("/alerts/{alertId}/context")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyContext(
            @PathVariable UUID sessionId,
            @PathVariable String alertId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId,
            @RequestAttribute(name = DemoCapabilityService.REQUEST_HASH_ATTRIBUTE) String capabilityHash,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ContextCommand request
    ) {
        P0WorkflowResult result = workflowService.applyContext(
                sessionId, demoRunId, alertId, capabilityHash, idempotencyKey, request
        );
        return withRun(ApiResponses.ok(result.code(), result.message(), result.data()), demoRunId);
    }

    @GetMapping("/alerts/{alertId}/audit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> alertAudit(
            @PathVariable UUID sessionId,
            @PathVariable String alertId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return withRun(ApiResponses.ok(
                "ALERT_AUDIT_RETRIEVED",
                "감사이력을 조회했습니다.",
                workflowService.alertAudit(sessionId, demoRunId, alertId, cursor, limit)
        ), demoRunId);
    }

    @GetMapping("/staff/cases")
    public ResponseEntity<ApiResponse<Map<String, Object>>> caseQueue(
            @PathVariable UUID sessionId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String reviewPriority,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return withRun(ApiResponses.ok(
                "CASE_QUEUE_RETRIEVED",
                "행원 사건큐를 조회했습니다.",
                workflowService.caseQueue(sessionId, demoRunId, state, reviewPriority, cursor, limit)
        ), demoRunId);
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> caseDetail(
            @PathVariable UUID sessionId,
            @PathVariable String caseId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId
    ) {
        return withRun(ApiResponses.ok(
                "CASE_DETAIL_RETRIEVED",
                "보호업무 사건 상세를 조회했습니다.",
                workflowService.caseDetail(sessionId, demoRunId, caseId)
        ), demoRunId);
    }

    @PostMapping("/cases/{caseId}/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewCase(
            @PathVariable UUID sessionId,
            @PathVariable String caseId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId,
            @RequestAttribute(name = DemoCapabilityService.REQUEST_HASH_ATTRIBUTE) String capabilityHash,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CaseReviewCommand request
    ) {
        P0WorkflowResult result = workflowService.reviewCase(
                sessionId, demoRunId, caseId, capabilityHash, idempotencyKey, request
        );
        return withRun(ApiResponses.ok(result.code(), result.message(), result.data()), demoRunId);
    }

    @PostMapping("/cases/{caseId}/guidance-plan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveGuidancePlan(
            @PathVariable UUID sessionId,
            @PathVariable String caseId,
            @RequestHeader(name = DEMO_RUN_HEADER, required = false) UUID demoRunId,
            @RequestAttribute(name = DemoCapabilityService.REQUEST_HASH_ATTRIBUTE) String capabilityHash,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GuidancePlanCommand request
    ) {
        P0WorkflowResult result = workflowService.approveGuidancePlan(
                sessionId, demoRunId, caseId, capabilityHash, idempotencyKey, request
        );
        return withRun(ApiResponses.ok(result.code(), result.message(), result.data()), demoRunId);
    }

    private <T> ResponseEntity<ApiResponse<T>> withRun(
            ResponseEntity<ApiResponse<T>> response,
            UUID demoRunId
    ) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders());
        if (demoRunId != null) {
            builder.header(DEMO_RUN_HEADER, demoRunId.toString());
        }
        return builder.body(response.getBody());
    }
}
