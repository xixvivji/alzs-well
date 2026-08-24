package com.alzswell.alert.api;

import com.alzswell.alert.api.AlertRequests.ContextResponseCommand;
import com.alzswell.alert.api.AlertRequests.DeferCommand;
import com.alzswell.alert.api.AlertResponses.AlertDetail;
import com.alzswell.alert.api.AlertResponses.AlertTransition;
import com.alzswell.alert.api.AlertResponses.AuditTrail;
import com.alzswell.alert.api.AlertResponses.ContextOptions;
import com.alzswell.alert.application.OperationalAlertService;
import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@Validated
public class OperationalAlertController {
    private final OperationalAlertService alertService;

    public OperationalAlertController(OperationalAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/{alertId}")
    @PreAuthorize("hasAnyAuthority('ALERT_READ', 'ALERT_READ_ALL')")
    public ResponseEntity<ApiResponse<AlertDetail>> alert(
            @PathVariable UUID alertId, Authentication authentication) {
        AuditActor actor = AuditActor.from(authentication);
        return ApiResponses.ok("ALERT_RETRIEVED", "운영형 경보 상세를 조회했습니다.",
                alertService.alert(alertId, actor, has(authentication, "ALERT_READ_ALL")));
    }

    @GetMapping("/{alertId}/context-options")
    @PreAuthorize("hasAnyAuthority('ALERT_READ', 'ALERT_READ_ALL')")
    public ResponseEntity<ApiResponse<ContextOptions>> contextOptions(
            @PathVariable UUID alertId, Authentication authentication) {
        AuditActor actor = AuditActor.from(authentication);
        return ApiResponses.ok("ALERT_CONTEXT_OPTIONS_RETRIEVED", "허용된 생활맥락 응답을 조회했습니다.",
                alertService.contextOptions(alertId, actor,
                        has(authentication, "ALERT_READ_ALL")));
    }

    @PostMapping("/{alertId}/context-responses")
    @PreAuthorize("hasAnyAuthority('ALERT_RESPOND', 'ALERT_RESPOND_ALL')")
    public ResponseEntity<ApiResponse<AlertTransition>> respond(
            @PathVariable UUID alertId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody ContextResponseCommand command,
            Authentication authentication) {
        AuditActor actor = AuditActor.from(authentication);
        return ApiResponses.ok("ALERT_CONTEXT_APPLIED", "생활맥락을 반영해 경보를 재평가했습니다.",
                alertService.respond(alertId, command, idempotencyKey,
                        has(authentication, "ALERT_RESPOND_ALL"), actor));
    }

    @PostMapping("/{alertId}/defer")
    @PreAuthorize("hasAnyAuthority('ALERT_RESPOND', 'ALERT_RESPOND_ALL')")
    public ResponseEntity<ApiResponse<AlertTransition>> defer(
            @PathVariable UUID alertId, @Valid @RequestBody DeferCommand command,
            Authentication authentication) {
        AuditActor actor = AuditActor.from(authentication);
        return ApiResponses.ok("ALERT_DEFERRED", "경보 확인을 지정한 시각까지 연기했습니다.",
                alertService.defer(alertId, command,
                        has(authentication, "ALERT_RESPOND_ALL"), actor));
    }

    @GetMapping("/{alertId}/audit")
    @PreAuthorize("hasAnyAuthority('ALERT_READ', 'ALERT_READ_ALL')")
    public ResponseEntity<ApiResponse<AuditTrail>> audit(
            @PathVariable UUID alertId, Authentication authentication) {
        AuditActor actor = AuditActor.from(authentication);
        return ApiResponses.ok("ALERT_AUDIT_RETRIEVED", "운영형 경보 감사이력을 조회했습니다.",
                alertService.audit(alertId, actor, has(authentication, "ALERT_READ_ALL")));
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(item -> item.getAuthority().equals(authority));
    }
}
