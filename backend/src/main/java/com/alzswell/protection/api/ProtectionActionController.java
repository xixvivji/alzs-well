package com.alzswell.protection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.protection.api.ProtectionRequests.EligibilityEvaluationCommand;
import com.alzswell.protection.api.ProtectionResponses.*;
import com.alzswell.protection.application.ProtectionCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ProtectionActionController {
    private static final String ACTION_CODE_PATTERN = "^[A-Z][A-Z0-9_]{2,59}$";
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final ProtectionCatalogService service;
    public ProtectionActionController(ProtectionCatalogService service) { this.service = service; }

    @GetMapping("/protection-actions") @PreAuthorize("hasAuthority('PROTECTION_ACTION_READ')")
    public ResponseEntity<ApiResponse<ActionList>> actions() {
        return ApiResponses.ok("PROTECTION_ACTIONS_RETRIEVED", "공식 보호수단 안내 목록을 조회했습니다.", service.actions());
    }

    @GetMapping("/protection-actions/{actionCode}") @PreAuthorize("hasAuthority('PROTECTION_ACTION_READ')")
    // Stateless bearer auth has no ambient browser credential; the only write is append-only access audit.
    public ResponseEntity<ApiResponse<ActionDetail>> action(
            @PathVariable @Pattern(regexp = ACTION_CODE_PATTERN) String actionCode,Authentication authentication) {
        return ApiResponses.ok("PROTECTION_ACTION_RETRIEVED", "보호수단 안내 상세를 조회했습니다.",
                service.action(actionCode,authentication));
    }

    @PostMapping("/protection-actions/{actionCode}/eligibility-evaluations")
    @PreAuthorize("hasAuthority('PROTECTION_ACTION_EVALUATE') and "
            + "(#command.customerId == authentication.name or hasAuthority('PROTECTION_ENROLLMENT_READ_ALL'))")
    public ResponseEntity<ApiResponse<EligibilityEvaluation>> evaluate(
            @PathVariable @Pattern(regexp = ACTION_CODE_PATTERN) String actionCode,
            @Valid @RequestBody EligibilityEvaluationCommand command, Authentication authentication) {
        return ApiResponses.ok("PROTECTION_ELIGIBILITY_EVALUATED", "보호수단 안내 가능성을 평가했습니다.",
                service.evaluate(actionCode, command, AuditActor.from(authentication)));
    }

    @GetMapping("/customers/{customerId}/protection-enrollments")
    @PreAuthorize("(#customerId == authentication.name and hasAuthority('PROTECTION_ENROLLMENT_READ')) or "
            + "hasAuthority('PROTECTION_ENROLLMENT_READ_ALL')")
    public ResponseEntity<ApiResponse<EnrollmentList>> enrollments(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            Authentication authentication) {
        return ApiResponses.ok("PROTECTION_ENROLLMENTS_RETRIEVED", "합성 보호수단 가입상태를 조회했습니다.",
                service.enrollments(customerId, AuditActor.from(authentication)));
    }
}
