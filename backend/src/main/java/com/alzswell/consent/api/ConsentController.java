package com.alzswell.consent.api;

import com.alzswell.common.api.*;
import com.alzswell.common.security.AuditActor;
import com.alzswell.consent.api.ConsentRequests.*;
import com.alzswell.consent.api.ConsentResponses.*;
import com.alzswell.consent.application.ConsentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/customers/{customerId}")
public class ConsentController {
    private static final String CUSTOMER_ID_PATTERN="^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final ConsentService service;
    public ConsentController(ConsentService service){this.service=service;}

    @GetMapping("/consents") @PreAuthorize("(#customerId==authentication.name and hasAuthority('CONSENT_READ')) or hasAuthority('CONSENT_READ_ALL')")
    public ResponseEntity<ApiResponse<ConsentList>> consents(@PathVariable @Pattern(regexp=CUSTOMER_ID_PATTERN) String customerId,org.springframework.security.core.Authentication authentication){
        return ApiResponses.ok("CONSENTS_RETRIEVED","유효한 목적별 동의를 조회했습니다.",service.active(customerId,AuditActor.from(authentication)));
    }
    @GetMapping("/consents/{consentId}") @PreAuthorize("(#customerId==authentication.name and hasAuthority('CONSENT_READ')) or hasAuthority('CONSENT_READ_ALL')")
    public ResponseEntity<ApiResponse<Consent>> consent(@PathVariable @Pattern(regexp=CUSTOMER_ID_PATTERN) String customerId,@PathVariable UUID consentId,org.springframework.security.core.Authentication authentication){
        return ApiResponses.ok("CONSENT_RETRIEVED","동의 상세를 조회했습니다.",service.detailAudited(customerId,consentId,AuditActor.from(authentication)));
    }
    @PostMapping("/consents") @PreAuthorize("(#customerId==authentication.name and hasAuthority('CONSENT_WRITE')) or hasAuthority('CONSENT_WRITE_ALL')")
    public ResponseEntity<ApiResponse<Consent>> grant(@PathVariable @Pattern(regexp=CUSTOMER_ID_PATTERN) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100) @Pattern(regexp="[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody GrantCommand command,org.springframework.security.core.Authentication authentication){
        return ApiResponses.created("CONSENT_GRANTED","목적별 동의를 등록했습니다.",service.grant(customerId,command,idempotencyKey,AuditActor.from(authentication)));
    }
    @PostMapping("/consents/{consentId}/withdraw") @PreAuthorize("(#customerId==authentication.name and hasAuthority('CONSENT_WRITE')) or hasAuthority('CONSENT_WRITE_ALL')")
    public ResponseEntity<ApiResponse<Consent>> withdraw(@PathVariable @Pattern(regexp=CUSTOMER_ID_PATTERN) String customerId,
            @PathVariable UUID consentId,@Valid @RequestBody WithdrawCommand command,org.springframework.security.core.Authentication authentication){
        return ApiResponses.ok("CONSENT_WITHDRAWN","동의를 철회했습니다.",service.withdraw(customerId,consentId,command,AuditActor.from(authentication)));
    }
    @GetMapping("/consents/{consentId}/history") @PreAuthorize("(#customerId==authentication.name and hasAuthority('CONSENT_READ')) or hasAuthority('CONSENT_READ_ALL')")
    public ResponseEntity<ApiResponse<ConsentHistory>> history(@PathVariable @Pattern(regexp=CUSTOMER_ID_PATTERN) String customerId,@PathVariable UUID consentId,org.springframework.security.core.Authentication authentication){
        return ApiResponses.ok("CONSENT_HISTORY_RETRIEVED","동의 변경 이력을 조회했습니다.",service.history(customerId,consentId,AuditActor.from(authentication)));
    }
    @PostMapping("/disclosure-evaluations") @PreAuthorize("(#customerId==authentication.name or hasAuthority('CONSENT_READ_ALL')) and hasAuthority('DISCLOSURE_EVALUATE')")
    public ResponseEntity<ApiResponse<DisclosureEvaluation>> evaluate(@PathVariable @Pattern(regexp=CUSTOMER_ID_PATTERN) String customerId,
            @Valid @RequestBody DisclosureEvaluationCommand command,org.springframework.security.core.Authentication authentication){
        return ApiResponses.ok("DISCLOSURE_EVALUATED","최소정보 제공 가능성을 평가했습니다.",service.evaluate(customerId,command,AuditActor.from(authentication)));
    }
}
