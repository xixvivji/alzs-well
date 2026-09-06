package com.alzswell.copilot.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.copilot.application.CopilotPort.CopilotDraft;
import com.alzswell.copilot.application.OperationalCopilotService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/cases")
public class OperationalCopilotController {
    private final OperationalCopilotService service;

    public OperationalCopilotController(OperationalCopilotService service) {
        this.service = service;
    }

    @PostMapping("/{caseId}/copilot-drafts")
    @PreAuthorize("hasAuthority('STAFF_CASE_READ') and hasAuthority('STAFF_CASE_REVIEW')")
    public ResponseEntity<ApiResponse<CopilotDraft>> generate(@PathVariable UUID caseId, Authentication authentication) {
        return ApiResponses.ok("STAFF_COPILOT_DRAFT_GENERATED", "검토용 초안입니다. 사건 상태나 안내계획은 변경되지 않았습니다.",
                service.generate(caseId, AuditActor.from(authentication)));
    }
}
