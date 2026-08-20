package com.alzswell.casework.api;

import com.alzswell.casework.api.CaseworkRequests.FollowUpUpdateCommand;
import com.alzswell.casework.api.CaseworkResponses.FollowUp;
import com.alzswell.casework.application.OperationalCaseService;
import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/follow-ups")
public class OperationalFollowUpController {
    private final OperationalCaseService caseService;

    public OperationalFollowUpController(OperationalCaseService caseService) {
        this.caseService = caseService;
    }

    @PatchMapping("/{followUpId}")
    @PreAuthorize("hasAuthority('STAFF_FOLLOW_UP')")
    public ResponseEntity<ApiResponse<FollowUp>> update(
            @PathVariable UUID followUpId, @Valid @RequestBody FollowUpUpdateCommand command,
            Authentication authentication) {
        return ApiResponses.ok("STAFF_CASE_FOLLOW_UP_UPDATED", "후속 일정 상태를 변경했습니다.",
                caseService.updateFollowUp(followUpId, command,
                        AuditActor.from(authentication).legacyActorId()));
    }
}
