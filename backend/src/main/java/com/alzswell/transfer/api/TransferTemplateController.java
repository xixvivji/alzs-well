package com.alzswell.transfer.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.transfer.application.TransferTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/transfer-templates")
@Validated
public class TransferTemplateController {
    private static final String CUSTOMER_ID = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private static final String IDEMPOTENCY_KEY = "[A-Za-z0-9._:-]+";
    private final TransferTemplateService service;

    public TransferTemplateController(TransferTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "저장 이체 양식 조회",
            description = "본인의 활성 양식을 마스킹된 합성 계좌·수취인 정보와 함께 조회하며 송금은 실행하지 않습니다.")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSFER_TEMPLATE_READ')")
    public ResponseEntity<ApiResponse<TransferTemplateResponses.TemplateList>> list(
            @PathVariable @Pattern(regexp = CUSTOMER_ID) String customerId) {
        return ApiResponses.ok("TRANSFER_TEMPLATES_RETRIEVED", "저장 이체 양식을 조회했습니다.",
                service.list(customerId));
    }

    @PostMapping
    @Operation(summary = "저장 이체 양식 생성",
            description = "활성 합성 계좌와 마스킹 수취인을 참조하는 양식만 저장하며 이체·승인을 생성하지 않습니다.")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSFER_TEMPLATE_WRITE')")
    public ResponseEntity<ApiResponse<TransferTemplateResponses.Template>> create(
            @PathVariable @Pattern(regexp = CUSTOMER_ID) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody TransferTemplateRequests.Create command,
            Authentication authentication) {
        return ApiResponses.created("TRANSFER_TEMPLATE_CREATED", "저장 이체 양식을 생성했습니다.",
                service.create(customerId, command, idempotencyKey, AuditActor.from(authentication)));
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "저장 이체 양식 삭제",
            description = "본인 양식을 재사용할 수 없도록 논리 삭제하며 실제 이체나 외부 삭제 요청은 실행하지 않습니다.")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSFER_TEMPLATE_WRITE')")
    public ResponseEntity<ApiResponse<TransferTemplateResponses.Deletion>> delete(
            @PathVariable @Pattern(regexp = CUSTOMER_ID) String customerId,
            @PathVariable UUID templateId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = IDEMPOTENCY_KEY) String idempotencyKey,
            Authentication authentication) {
        return ApiResponses.ok("TRANSFER_TEMPLATE_DELETED", "저장 이체 양식을 삭제했습니다.",
                service.delete(customerId, templateId, idempotencyKey, AuditActor.from(authentication)));
    }
}
