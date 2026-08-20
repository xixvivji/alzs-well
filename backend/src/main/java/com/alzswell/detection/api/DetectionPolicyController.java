package com.alzswell.detection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.detection.api.DetectionPolicyRequests.CreatePolicyCommand;
import com.alzswell.detection.api.DetectionPolicyRequests.UpdatePolicyCommand;
import com.alzswell.detection.api.DetectionPolicyResponses.AlgorithmVersionList;
import com.alzswell.detection.api.DetectionPolicyResponses.PolicyDetail;
import com.alzswell.detection.api.DetectionPolicyResponses.PolicyList;
import com.alzswell.detection.api.DetectionPolicyResponses.VersionList;
import com.alzswell.detection.application.DetectionPolicyService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class DetectionPolicyController {
    private final DetectionPolicyService service;

    public DetectionPolicyController(DetectionPolicyService service) { this.service = service; }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_READ')")
    public ResponseEntity<ApiResponse<PolicyList>> rules() {
        return ApiResponses.ok("DETECTION_POLICIES_RETRIEVED", "탐지 정책 목록을 조회했습니다.", service.policies());
    }

    @GetMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_READ')")
    public ResponseEntity<ApiResponse<PolicyDetail>> rule(@PathVariable UUID ruleId) {
        return ApiResponses.ok("DETECTION_POLICY_RETRIEVED", "탐지 정책을 조회했습니다.", service.policy(ruleId));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_WRITE')")
    public ResponseEntity<ApiResponse<PolicyDetail>> create(@Valid @RequestBody CreatePolicyCommand command,
                                                             Authentication authentication) {
        return ApiResponses.created("DETECTION_POLICY_DRAFT_CREATED", "탐지 정책 초안을 생성했습니다.",
                service.create(command, AuditActor.from(authentication)));
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_WRITE')")
    public ResponseEntity<ApiResponse<PolicyDetail>> update(@PathVariable UUID ruleId,
                                                             @Valid @RequestBody UpdatePolicyCommand command,
                                                             Authentication authentication) {
        return ApiResponses.ok("DETECTION_POLICY_DRAFT_UPDATED", "탐지 정책 초안을 변경했습니다.",
                service.update(ruleId, command, AuditActor.from(authentication)));
    }

    @PostMapping("/rules/{ruleId}/publish")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_WRITE')")
    public ResponseEntity<ApiResponse<PolicyDetail>> publish(@PathVariable UUID ruleId,
                                                              Authentication authentication) {
        return ApiResponses.ok("DETECTION_POLICY_PUBLISHED", "탐지 정책을 활성화했습니다.",
                service.publish(ruleId, AuditActor.from(authentication)));
    }

    @PostMapping("/rules/{ruleId}/rollback")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_WRITE')")
    public ResponseEntity<ApiResponse<PolicyDetail>> rollback(@PathVariable UUID ruleId,
                                                               Authentication authentication) {
        return ApiResponses.created("DETECTION_POLICY_ROLLED_BACK", "선택한 정책을 새 버전으로 복귀했습니다.",
                service.rollback(ruleId, AuditActor.from(authentication)));
    }

    @GetMapping("/policies/versions")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_READ')")
    public ResponseEntity<ApiResponse<VersionList>> versions() {
        return ApiResponses.ok("DETECTION_POLICY_VERSIONS_RETRIEVED", "탐지 정책 버전을 조회했습니다.", service.versions());
    }

    @GetMapping("/algorithms/versions")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_READ')")
    public ResponseEntity<ApiResponse<AlgorithmVersionList>> algorithms() {
        return ApiResponses.ok("DETECTION_ALGORITHM_VERSIONS_RETRIEVED", "탐지 알고리즘 버전을 조회했습니다.",
                service.algorithms());
    }
}
