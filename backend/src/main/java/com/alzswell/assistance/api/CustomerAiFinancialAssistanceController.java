package com.alzswell.assistance.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.demo.api.AiFinancialAssistanceResponses.ChangeAnalysis;
import com.alzswell.demo.application.DemoAiFinancialAssistanceService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/ai-financial-assistance")
@Validated
@PreAuthorize("(#customerId == authentication.name and hasAuthority('DETECTION_READ')) or "
        + "hasAuthority('DETECTION_READ_ALL')")
public class CustomerAiFinancialAssistanceController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final DemoAiFinancialAssistanceService assistanceService;

    public CustomerAiFinancialAssistanceController(DemoAiFinancialAssistanceService assistanceService) {
        this.assistanceService = assistanceService;
    }

    @PostMapping("/change-analysis")
    public ResponseEntity<ApiResponse<ChangeAnalysis>> analyze(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("CUSTOMER_AI_CHANGE_ANALYSIS_COMPLETED",
                "회원 합성 기준선의 30·60·90일 장기 변화를 분석했습니다.",
                assistanceService.analyzeMember(customerId));
    }
}
