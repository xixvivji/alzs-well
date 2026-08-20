package com.alzswell.casework.api;

import com.alzswell.casework.api.CaseworkRequests.AssignmentCommand;
import com.alzswell.casework.api.CaseworkRequests.GuidancePlanCommand;
import com.alzswell.casework.api.CaseworkRequests.FollowUpCommand;
import com.alzswell.casework.api.CaseworkRequests.NoteCommand;
import com.alzswell.casework.api.CaseworkRequests.ReviewCommand;
import com.alzswell.casework.api.CaseworkResponses.CaseDetail;
import com.alzswell.casework.api.CaseworkResponses.CaseEvidence;
import com.alzswell.casework.api.CaseworkResponses.CaseNote;
import com.alzswell.casework.api.CaseworkResponses.CaseNotes;
import com.alzswell.casework.api.CaseworkResponses.CaseQueue;
import com.alzswell.casework.api.CaseworkResponses.CaseTimeline;
import com.alzswell.casework.api.CaseworkResponses.CaseTransition;
import com.alzswell.casework.api.CaseworkResponses.GuidancePlan;
import com.alzswell.casework.api.CaseworkResponses.FollowUp;
import com.alzswell.casework.api.CaseworkResponses.FollowUps;
import com.alzswell.casework.application.OperationalCaseService;
import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/cases")
@Validated
public class OperationalCaseController {
    private final OperationalCaseService caseService;

    public OperationalCaseController(OperationalCaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STAFF_CASE_READ')")
    public ResponseEntity<ApiResponse<CaseQueue>> queue(
            @RequestParam(required = false)
            @Pattern(regexp = "PENDING|IN_REVIEW|GUIDANCE_APPROVED|COMPLETED") String status,
            @RequestParam(required = false) @Pattern(regexp = "HIGH|MEDIUM|LOW") String priority,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponses.ok("STAFF_CASE_QUEUE_RETRIEVED", "운영형 행원 사건큐를 조회했습니다.",
                caseService.queue(status, priority, cursor, limit));
    }

    @GetMapping("/{caseId}")
    @PreAuthorize("hasAuthority('STAFF_CASE_READ')")
    public ResponseEntity<ApiResponse<CaseDetail>> detail(@PathVariable UUID caseId) {
        return ApiResponses.ok("STAFF_CASE_RETRIEVED", "운영형 행원 사건 상세를 조회했습니다.",
                caseService.detail(caseId));
    }

    @PutMapping("/{caseId}/assignment")
    @PreAuthorize("hasAuthority('STAFF_CASE_ASSIGN')")
    public ResponseEntity<ApiResponse<CaseTransition>> assign(
            @PathVariable UUID caseId, @Valid @RequestBody AssignmentCommand command,
            Authentication authentication) {
        return ApiResponses.ok("STAFF_CASE_ASSIGNED", "사건 담당 팀과 행원을 배정했습니다.",
                caseService.assign(caseId, command, AuditActor.from(authentication).legacyActorId()));
    }

    @PostMapping("/{caseId}/reviews")
    @PreAuthorize("hasAuthority('STAFF_CASE_REVIEW')")
    public ResponseEntity<ApiResponse<CaseTransition>> review(
            @PathVariable UUID caseId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody ReviewCommand command,
            Authentication authentication) {
        return ApiResponses.ok("STAFF_CASE_REVIEW_APPLIED", "사건 검토 상태를 변경했습니다.",
                caseService.review(caseId, command, idempotencyKey,
                        AuditActor.from(authentication).legacyActorId()));
    }

    @PostMapping("/{caseId}/guidance-plans")
    @PreAuthorize("hasAuthority('STAFF_GUIDANCE_APPROVE')")
    public ResponseEntity<ApiResponse<GuidancePlan>> guidance(
            @PathVariable UUID caseId, @Valid @RequestBody GuidancePlanCommand command,
            Authentication authentication) {
        return ApiResponses.created("STAFF_GUIDANCE_PLAN_APPROVED",
                "외부 실행 없이 고객 안내계획을 승인했습니다.",
                caseService.approveGuidance(caseId, command,
                        AuditActor.from(authentication).legacyActorId()));
    }

    @GetMapping("/{caseId}/evidence")
    @PreAuthorize("hasAuthority('STAFF_CASE_READ')")
    public ResponseEntity<ApiResponse<CaseEvidence>> evidence(@PathVariable UUID caseId) {
        return ApiResponses.ok("STAFF_CASE_EVIDENCE_RETRIEVED", "사건의 불변 합성 근거를 조회했습니다.",
                caseService.evidence(caseId));
    }

    @GetMapping("/{caseId}/timeline")
    @PreAuthorize("hasAuthority('STAFF_CASE_READ')")
    public ResponseEntity<ApiResponse<CaseTimeline>> timeline(@PathVariable UUID caseId) {
        return ApiResponses.ok("STAFF_CASE_TIMELINE_RETRIEVED", "사건 통합 타임라인을 조회했습니다.",
                caseService.timeline(caseId));
    }

    @GetMapping("/{caseId}/notes")
    @PreAuthorize("hasAnyAuthority('STAFF_CASE_READ', 'STAFF_CASE_NOTE')")
    public ResponseEntity<ApiResponse<CaseNotes>> notes(@PathVariable UUID caseId) {
        return ApiResponses.ok("STAFF_CASE_NOTES_RETRIEVED", "사건 내부 메모를 조회했습니다.",
                caseService.notes(caseId));
    }

    @PostMapping("/{caseId}/notes")
    @PreAuthorize("hasAuthority('STAFF_CASE_NOTE')")
    public ResponseEntity<ApiResponse<CaseNote>> addNote(
            @PathVariable UUID caseId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody NoteCommand command,
            Authentication authentication) {
        return ApiResponses.created("STAFF_CASE_NOTE_CREATED", "외부 전송 없이 사건 내부 메모를 등록했습니다.",
                caseService.addNote(caseId, command, idempotencyKey,
                        AuditActor.from(authentication).legacyActorId()));
    }

    @GetMapping("/{caseId}/follow-ups")
    @PreAuthorize("hasAnyAuthority('STAFF_CASE_READ', 'STAFF_FOLLOW_UP')")
    public ResponseEntity<ApiResponse<FollowUps>> followUps(
            @PathVariable UUID caseId,
            @RequestParam(required = false) @Pattern(regexp = "SCHEDULED|COMPLETED|CANCELLED") String status) {
        return ApiResponses.ok("STAFF_CASE_FOLLOW_UPS_RETRIEVED", "사건 내부 후속 일정을 조회했습니다.",
                caseService.followUps(caseId, status));
    }

    @PostMapping("/{caseId}/follow-ups")
    @PreAuthorize("hasAuthority('STAFF_FOLLOW_UP')")
    public ResponseEntity<ApiResponse<FollowUp>> createFollowUp(
            @PathVariable UUID caseId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody FollowUpCommand command,
            Authentication authentication) {
        return ApiResponses.created("STAFF_CASE_FOLLOW_UP_CREATED", "외부 연락 없이 후속 일정을 등록했습니다.",
                caseService.createFollowUp(caseId, command, idempotencyKey,
                        AuditActor.from(authentication).legacyActorId()));
    }

}
