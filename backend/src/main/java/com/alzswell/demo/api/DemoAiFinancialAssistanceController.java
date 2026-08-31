package com.alzswell.demo.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.demo.api.AiFinancialAssistanceRequests.IntentApproval;
import com.alzswell.demo.api.AiFinancialAssistanceRequests.IntentDraft;
import com.alzswell.demo.api.AiFinancialAssistanceRequests.IntentSuggestion;
import com.alzswell.demo.api.AiFinancialAssistanceRequests.PlainLanguage;
import com.alzswell.demo.application.DemoAiFinancialAssistanceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/sessions/{sessionId}/customers/{customerId}/ai-financial-assistance")
@PreAuthorize("hasAuthority('CUSTOMER_DEMO')")
@Validated
public class DemoAiFinancialAssistanceController {
    private final DemoAiFinancialAssistanceService service;

    public DemoAiFinancialAssistanceController(DemoAiFinancialAssistanceService service) {
        this.service = service;
    }

    @PostMapping("/intent-suggestions")
    public ResponseEntity<ApiResponse<AiFinancialAssistanceResponses.IntentSuggestion>> suggest(
            @PathVariable UUID sessionId, @PathVariable String customerId,
            @RequestHeader(P0WorkflowController.DEMO_RUN_HEADER) UUID demoRunId,
            @Valid @RequestBody IntentSuggestion request
    ) {
        return ApiResponses.ok("DEMO_AI_INTENT_SUGGESTED", "AI가 고객 확인용 금융생활 의향 초안을 정리했습니다.",
                service.suggest(sessionId, demoRunId, customerId, request.utterance()));
    }

    @PutMapping("/intent")
    public ResponseEntity<ApiResponse<AiFinancialAssistanceResponses.Intent>> saveDraft(
            @PathVariable UUID sessionId, @PathVariable String customerId,
            @RequestHeader(P0WorkflowController.DEMO_RUN_HEADER) UUID demoRunId,
            @Valid @RequestBody IntentDraft request
    ) {
        return ApiResponses.ok("DEMO_AI_INTENT_DRAFT_SAVED", "고객이 확인한 금융생활 의향 초안을 저장했습니다.",
                service.saveDraft(sessionId, demoRunId, customerId, request));
    }

    @PostMapping("/intent/approve")
    public ResponseEntity<ApiResponse<AiFinancialAssistanceResponses.Intent>> approve(
            @PathVariable UUID sessionId, @PathVariable String customerId,
            @RequestHeader(P0WorkflowController.DEMO_RUN_HEADER) UUID demoRunId,
            @Valid @RequestBody IntentApproval request
    ) {
        return ApiResponses.ok("DEMO_AI_INTENT_APPROVED", "고객이 법적 효력 제한을 확인하고 의향을 승인했습니다.",
                service.approve(sessionId, demoRunId, customerId, request.expectedVersion()));
    }

    @GetMapping("/intent")
    public ResponseEntity<ApiResponse<AiFinancialAssistanceResponses.Intent>> current(
            @PathVariable UUID sessionId, @PathVariable String customerId,
            @RequestHeader(P0WorkflowController.DEMO_RUN_HEADER) UUID demoRunId
    ) {
        return ApiResponses.ok("DEMO_AI_INTENT_RETRIEVED", "현재 데모 금융생활 의향을 조회했습니다.",
                service.current(sessionId, demoRunId, customerId));
    }

    @PostMapping("/change-analysis")
    public ResponseEntity<ApiResponse<AiFinancialAssistanceResponses.ChangeAnalysis>> changes(
            @PathVariable UUID sessionId, @PathVariable String customerId,
            @RequestHeader(P0WorkflowController.DEMO_RUN_HEADER) UUID demoRunId
    ) {
        return ApiResponses.ok("DEMO_AI_CHANGE_ANALYZED", "30·60·90일 장기 금융생활 변화를 분석했습니다.",
                service.analyze(sessionId, demoRunId, customerId));
    }

    @PostMapping("/plain-language")
    public ResponseEntity<ApiResponse<AiFinancialAssistanceResponses.PlainLanguage>> plainLanguage(
            @PathVariable UUID sessionId, @PathVariable String customerId,
            @RequestHeader(P0WorkflowController.DEMO_RUN_HEADER) UUID demoRunId,
            @Valid @RequestBody PlainLanguage request
    ) {
        return ApiResponses.ok("DEMO_AI_PLAIN_LANGUAGE_GENERATED", "고객 의향에 맞는 쉬운 설명을 만들었습니다.",
                service.plainLanguage(sessionId, demoRunId, customerId, request.featureCode()));
    }
}
