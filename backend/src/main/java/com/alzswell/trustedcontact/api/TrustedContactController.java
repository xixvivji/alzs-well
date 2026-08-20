package com.alzswell.trustedcontact.api;

import com.alzswell.common.api.*;import com.alzswell.trustedcontact.api.TrustedContactRequests.*;
import com.alzswell.trustedcontact.api.TrustedContactResponses.*;import com.alzswell.trustedcontact.application.TrustedContactService;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.security.Principal;import java.util.UUID;
import org.springframework.http.ResponseEntity;import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/customers/{customerId}/trusted-contacts")
public class TrustedContactController {
    private static final String CUSTOMER="^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";private final TrustedContactService service;
    public TrustedContactController(TrustedContactService service){this.service=service;}
    @GetMapping @PreAuthorize("(#customerId==authentication.name and hasAuthority('TRUSTED_CONTACT_READ')) or hasAuthority('TRUSTED_CONTACT_READ_ALL')")
    public ResponseEntity<ApiResponse<ContactList>> list(@PathVariable @Pattern(regexp=CUSTOMER) String customerId){return ApiResponses.ok("TRUSTED_CONTACTS_RETRIEVED","신뢰연락인 지정을 조회했습니다.",service.list(customerId));}
    @PostMapping @PreAuthorize("(#customerId==authentication.name and hasAuthority('TRUSTED_CONTACT_WRITE')) or hasAuthority('TRUSTED_CONTACT_WRITE_ALL')")
    public ResponseEntity<ApiResponse<Contact>> create(@PathVariable @Pattern(regexp=CUSTOMER) String customerId,@Valid @RequestBody CreateCommand command,Principal principal){return ApiResponses.created("TRUSTED_CONTACT_CREATED","신뢰연락인을 지정했습니다.",service.create(customerId,command,principal.getName()));}
    @GetMapping("/{contactId}") @PreAuthorize("(#customerId==authentication.name and hasAuthority('TRUSTED_CONTACT_READ')) or hasAuthority('TRUSTED_CONTACT_READ_ALL')")
    public ResponseEntity<ApiResponse<Contact>> detail(@PathVariable @Pattern(regexp=CUSTOMER) String customerId,@PathVariable UUID contactId){return ApiResponses.ok("TRUSTED_CONTACT_RETRIEVED","신뢰연락인 지정 상세를 조회했습니다.",service.detail(customerId,contactId));}
    @PatchMapping("/{contactId}") @PreAuthorize("(#customerId==authentication.name and hasAuthority('TRUSTED_CONTACT_WRITE')) or hasAuthority('TRUSTED_CONTACT_WRITE_ALL')")
    public ResponseEntity<ApiResponse<Contact>> update(@PathVariable @Pattern(regexp=CUSTOMER) String customerId,@PathVariable UUID contactId,@Valid @RequestBody UpdateCommand command,Principal principal){return ApiResponses.ok("TRUSTED_CONTACT_UPDATED","최소정보 범위를 변경했습니다.",service.update(customerId,contactId,command,principal.getName()));}
    @DeleteMapping("/{contactId}") @PreAuthorize("(#customerId==authentication.name and hasAuthority('TRUSTED_CONTACT_WRITE')) or hasAuthority('TRUSTED_CONTACT_WRITE_ALL')")
    public ResponseEntity<ApiResponse<Contact>> revoke(@PathVariable @Pattern(regexp=CUSTOMER) String customerId,@PathVariable UUID contactId,@RequestParam @Positive long expectedVersion,@RequestParam @NotBlank @Size(max=300) String reason,Principal principal){return ApiResponses.ok("TRUSTED_CONTACT_REVOKED","신뢰연락인 지정을 철회했습니다.",service.revoke(customerId,contactId,expectedVersion,reason,principal.getName()));}
}
