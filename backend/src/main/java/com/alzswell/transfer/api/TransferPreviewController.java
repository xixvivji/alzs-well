package com.alzswell.transfer.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.transfer.application.TransferPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class TransferPreviewController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final TransferPreviewService service;

    public TransferPreviewController(TransferPreviewService service) {
        this.service = service;
    }

    @GetMapping("/customers/{customerId}/beneficiaries")
    @Operation(summary = "마스킹된 합성 수취인 조회",
            description = "본인 소유의 마스킹된 합성 수취인 snapshot만 조회하며 외부 금융사를 호출하지 않습니다.")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSFER_PREVIEW_READ')")
    public ResponseEntity<ApiResponse<TransferPreviewResponses.BeneficiaryList>> beneficiaries(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("TRANSFER_BENEFICIARIES_RETRIEVED", "마스킹된 합성 수취인 목록을 조회했습니다.",
                service.beneficiaries(customerId));
    }

    @GetMapping("/customers/{customerId}/transfer-limits")
    @Operation(summary = "합성 이체한도 조회",
            description = "고정 기준일의 건별·일일 합성 한도만 조회하며 한도를 변경하지 않습니다.")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSFER_PREVIEW_READ')")
    public ResponseEntity<ApiResponse<TransferPreviewResponses.TransferLimit>> transferLimits(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("TRANSFER_LIMIT_RETRIEVED", "외부 호출 없이 합성 이체한도를 조회했습니다.",
                service.transferLimit(customerId));
    }

    @PostMapping("/transfer-simulations")
    @Operation(summary = "실행 없는 이체 모의계산",
            description = "가용잔액과 합성 한도로 결과를 계산할 뿐 이체·승인·외부 호출을 생성하지 않습니다.")
    @PreAuthorize("#command.customerId == authentication.name and hasAuthority('TRANSFER_PREVIEW_EVALUATE')")
    public ResponseEntity<ApiResponse<TransferPreviewResponses.SimulationResult>> simulate(
            @Valid @RequestBody TransferPreviewRequests.Simulation command) {
        return ApiResponses.ok("TRANSFER_SIMULATION_COMPLETED", "실제 이체 없이 합성 결과를 계산했습니다.",
                service.simulate(command));
    }

    @PostMapping("/transfer-validations")
    @Operation(summary = "실행 없는 이체 사전검증",
            description = "구조화된 안전 조건을 평가할 뿐 이체·OTP/MFA 승인·외부 호출을 생성하지 않습니다.")
    @PreAuthorize("#command.customerId == authentication.name and hasAuthority('TRANSFER_PREVIEW_EVALUATE')")
    public ResponseEntity<ApiResponse<TransferPreviewResponses.ValidationResult>> validate(
            @Valid @RequestBody TransferPreviewRequests.Validation command) {
        return ApiResponses.ok("TRANSFER_VALIDATION_COMPLETED", "실행 권한을 만들지 않고 이체 조건을 사전검증했습니다.",
                service.validate(command));
    }
}
