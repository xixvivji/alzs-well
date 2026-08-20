package com.alzswell.compliance.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.compliance.api.ComplianceResponses.AuditEvent;
import com.alzswell.compliance.api.ComplianceResponses.AuditEventList;
import com.alzswell.compliance.api.ComplianceResponses.DataProvenance;
import com.alzswell.compliance.api.ComplianceResponses.DecisionTrace;
import com.alzswell.compliance.application.ComplianceQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class ComplianceController {
    private final ComplianceQueryService service;

    public ComplianceController(ComplianceQueryService service) { this.service = service; }

    @GetMapping("/api/v1/audit/events")
    @PreAuthorize("hasAuthority('AUDIT_READ_ALL')")
    public ResponseEntity<ApiResponse<AuditEventList>> events(
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{2,39}") String sourceType,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{2,79}") String eventType,
            @RequestParam(required = false) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_:-]{2,79}") String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ApiResponses.ok("AUDIT_EVENTS_RETRIEVED", "권한 범위의 불변 감사이벤트를 조회했습니다.",
                service.events(sourceType,eventType,customerId,from,to,cursor,limit));
    }

    @GetMapping("/api/v1/audit/events/{eventId}")
    @PreAuthorize("hasAuthority('AUDIT_READ_ALL')")
    public ResponseEntity<ApiResponse<AuditEvent>> event(
            @PathVariable @Size(min = 10, max = 140)
            @Pattern(regexp = "[A-Z_]+:[A-Fa-f0-9-]+") String eventId) {
        return ApiResponses.ok("AUDIT_EVENT_RETRIEVED", "불변 감사이벤트 상세를 조회했습니다.", service.event(eventId));
    }

    @GetMapping("/api/v1/compliance/decision-traces/{decisionId}")
    @PreAuthorize("hasAuthority('COMPLIANCE_TRACE_READ')")
    public ResponseEntity<ApiResponse<DecisionTrace>> decision(@PathVariable UUID decisionId) {
        return ApiResponses.ok("DECISION_TRACE_RETRIEVED", "판단·규칙·근거 추적 정보를 조회했습니다.",
                service.decision(decisionId));
    }

    @GetMapping("/api/v1/compliance/data-provenance/{resourceType}/{resourceId}")
    @PreAuthorize("hasAuthority('COMPLIANCE_TRACE_READ')")
    public ResponseEntity<ApiResponse<DataProvenance>> provenance(
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{2,39}") String resourceType,
            @PathVariable UUID resourceId) {
        return ApiResponses.ok("DATA_PROVENANCE_RETRIEVED", "합성 데이터 출처와 버전을 조회했습니다.",
                service.provenance(resourceType,resourceId));
    }
}
