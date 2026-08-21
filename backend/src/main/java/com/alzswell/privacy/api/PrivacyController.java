package com.alzswell.privacy.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.privacy.api.PrivacyRequests.CorrectionRequest;
import com.alzswell.privacy.api.PrivacyRequests.DeletionRequest;
import com.alzswell.privacy.api.PrivacyResponses.PrivacyRequest;
import com.alzswell.privacy.api.PrivacyResponses.RetentionPolicyList;
import com.alzswell.privacy.application.PrivacyRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
public class PrivacyController {
    private static final String CUSTOMER="[A-Za-z0-9][A-Za-z0-9_:-]{2,79}";
    private final PrivacyRequestService service;
    public PrivacyController(PrivacyRequestService service){this.service=service;}

    @GetMapping("/api/v1/compliance/retention-policies")
    @PreAuthorize("hasAuthority('RETENTION_POLICY_READ')")
    public ResponseEntity<ApiResponse<RetentionPolicyList>> policies(){return ApiResponses.ok("RETENTION_POLICIES_RETRIEVED","보존·파기 정책을 조회했습니다.",service.retentionPolicies());}

    @PostMapping("/api/v1/customers/{customerId}/privacy/deletion-requests")
    @PreAuthorize("(#customerId==authentication.name and hasAuthority('PRIVACY_REQUEST_WRITE')) or hasAuthority('PRIVACY_REQUEST_WRITE_ALL')")
    public ResponseEntity<ApiResponse<PrivacyRequest>> deletion(@PathVariable @Pattern(regexp=CUSTOMER) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,
            @Valid @RequestBody DeletionRequest request,Authentication authentication){
        return ApiResponses.created("PRIVACY_DELETION_REQUESTED","삭제 요청을 접수하고 법적 보존 예외 검토를 시작했습니다.",service.create(customerId,"DELETION",request.targetType(),request.targetReference(),request.reasonCode(),null,key,AuditActor.from(authentication)));}

    @PostMapping("/api/v1/customers/{customerId}/privacy/correction-requests")
    @PreAuthorize("(#customerId==authentication.name and hasAuthority('PRIVACY_REQUEST_WRITE')) or hasAuthority('PRIVACY_REQUEST_WRITE_ALL')")
    public ResponseEntity<ApiResponse<PrivacyRequest>> correction(@PathVariable @Pattern(regexp=CUSTOMER) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,
            @Valid @RequestBody CorrectionRequest request,Authentication authentication){
        return ApiResponses.created("PRIVACY_CORRECTION_REQUESTED","정정 요청을 접수했습니다.",service.create(customerId,"CORRECTION",request.targetType(),request.targetReference(),request.reasonCode(),request.correctedValue(),key,AuditActor.from(authentication)));}
}
