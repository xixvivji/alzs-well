package com.alzswell.staffaccess.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.staffaccess.application.StaffAccessPolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class StaffAccessController {
    private static final String CUSTOMER = "[A-Za-z0-9][A-Za-z0-9_:-]{2,79}";
    private final StaffAccessPolicyService service;

    public StaffAccessController(StaffAccessPolicyService service) { this.service = service; }

    @GetMapping("/api/v1/customers/{customerId}/staff-access-grants")
    @PreAuthorize("hasAuthority('STAFF_ACCESS_GRANT_READ')")
    public ResponseEntity<ApiResponse<StaffAccessResponses.GrantList>> list(
            @PathVariable @Pattern(regexp = CUSTOMER) String customerId) {
        return ApiResponses.ok("STAFF_ACCESS_GRANTS_RETRIEVED", "고객별 직원 접근권을 조회했습니다.", service.list(customerId));
    }

    @PostMapping("/api/v1/customers/{customerId}/staff-access-grants")
    @PreAuthorize("hasAuthority('STAFF_ACCESS_GRANT_WRITE')")
    public ResponseEntity<ApiResponse<StaffAccessResponses.Grant>> create(
            @PathVariable @Pattern(regexp = CUSTOMER) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String key,
            @Valid @RequestBody StaffAccessRequests.GrantCommand command, Authentication authentication) {
        return ApiResponses.created("STAFF_ACCESS_GRANT_CREATED", "만료가 있는 고객별 직원 접근권을 생성했습니다.",
                service.create(customerId, command, key, AuditActor.from(authentication)));
    }

    @GetMapping("/api/v1/customers/{customerId}/staff-access-grants/{grantId}")
    @PreAuthorize("hasAuthority('STAFF_ACCESS_GRANT_READ')")
    public ResponseEntity<ApiResponse<StaffAccessResponses.Grant>> detail(
            @PathVariable @Pattern(regexp = CUSTOMER) String customerId, @PathVariable UUID grantId) {
        return ApiResponses.ok("STAFF_ACCESS_GRANT_RETRIEVED", "직원 접근권 상세를 조회했습니다.", service.detail(customerId, grantId));
    }

    @PostMapping("/api/v1/customers/{customerId}/staff-access-grants/{grantId}/revoke")
    @PreAuthorize("hasAuthority('STAFF_ACCESS_GRANT_WRITE')")
    public ResponseEntity<ApiResponse<StaffAccessResponses.Grant>> revoke(
            @PathVariable @Pattern(regexp = CUSTOMER) String customerId, @PathVariable UUID grantId,
            @Valid @RequestBody StaffAccessRequests.RevokeCommand command, Authentication authentication) {
        return ApiResponses.ok("STAFF_ACCESS_GRANT_REVOKED", "직원 접근권을 철회했습니다.",
                service.revoke(customerId, grantId, command, AuditActor.from(authentication)));
    }

    @PostMapping("/api/v1/staff-access-policy/evaluations")
    @PreAuthorize("hasAuthority('STAFF_ACCESS_EVALUATE')")
    public ResponseEntity<ApiResponse<StaffAccessResponses.Evaluation>> evaluate(
            @Valid @RequestBody StaffAccessRequests.EvaluationCommand command, Authentication authentication) {
        return ApiResponses.ok("STAFF_ACCESS_EVALUATED", "직원·고객·범위 접근권을 평가했습니다.",
                service.evaluate(command, AuditActor.from(authentication)));
    }

    @GetMapping("/api/v1/customers/{customerId}/staff-access-grants/{grantId}/audit")
    @PreAuthorize("hasAuthority('STAFF_ACCESS_GRANT_READ')")
    public ResponseEntity<ApiResponse<StaffAccessResponses.GrantHistory>> audit(
            @PathVariable @Pattern(regexp = CUSTOMER) String customerId, @PathVariable UUID grantId) {
        return ApiResponses.ok("STAFF_ACCESS_GRANT_AUDIT_RETRIEVED", "접근권 불변 감사이력을 조회했습니다.",
                service.history(customerId, grantId));
    }
}
