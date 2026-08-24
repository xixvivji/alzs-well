package com.alzswell.alert.api;

import com.alzswell.alert.api.AlertResponses.AlertList;
import com.alzswell.alert.application.OperationalAlertService;
import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/alerts")
@Validated
@PreAuthorize("(#customerId == authentication.name and hasAuthority('ALERT_READ')) or "
        + "hasAuthority('ALERT_READ_ALL')")
public class CustomerAlertController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final OperationalAlertService alertService;

    public CustomerAlertController(OperationalAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AlertList>> alerts(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false)
            @Pattern(regexp = "AWAITING_CONTEXT|DEFERRED|CLOSED_NORMAL|BANK_REVIEW") String state,
            @RequestParam(required = false) @Pattern(regexp = "LOW|MEDIUM|HIGH") String severity,
            Authentication authentication) {
        return ApiResponses.ok("CUSTOMER_ALERTS_RETRIEVED", "고객 운영형 경보를 조회했습니다.",
                alertService.alerts(customerId, state, severity, AuditActor.from(authentication)));
    }
}
