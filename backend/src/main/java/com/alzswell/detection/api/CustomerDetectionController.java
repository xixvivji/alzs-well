package com.alzswell.detection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.detection.api.DetectionResponses.BaselineCalculation;
import com.alzswell.detection.api.DetectionResponses.BaselineDetail;
import com.alzswell.detection.api.DetectionResponses.BaselineFeatureList;
import com.alzswell.detection.api.DetectionResponses.BaselineList;
import com.alzswell.detection.api.DetectionResponses.SignalList;
import com.alzswell.detection.application.DetectionQueryService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@Validated
@PreAuthorize("(#customerId == authentication.name and hasAuthority('DETECTION_READ')) or "
        + "hasAuthority('DETECTION_READ_ALL')")
public class CustomerDetectionController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final DetectionQueryService detectionQueryService;

    public CustomerDetectionController(DetectionQueryService detectionQueryService) {
        this.detectionQueryService = detectionQueryService;
    }

    @GetMapping("/baselines")
    public ResponseEntity<ApiResponse<BaselineList>> baselines(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("CUSTOMER_BASELINES_RETRIEVED", "고객 개인 기준선을 조회했습니다.",
                detectionQueryService.baselines(customerId));
    }

    @GetMapping("/baselines/{baselineId}")
    public ResponseEntity<ApiResponse<BaselineDetail>> baseline(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @PathVariable UUID baselineId) {
        return ApiResponses.ok("CUSTOMER_BASELINE_RETRIEVED", "고객 개인 기준선 상세를 조회했습니다.",
                detectionQueryService.baseline(customerId, baselineId));
    }

    @GetMapping("/baselines/{baselineId}/features")
    public ResponseEntity<ApiResponse<BaselineFeatureList>> baselineFeatures(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @PathVariable UUID baselineId) {
        return ApiResponses.ok("CUSTOMER_BASELINE_FEATURES_RETRIEVED", "기준선 특징값을 조회했습니다.",
                detectionQueryService.baselineFeatures(customerId, baselineId));
    }

    @PostMapping("/baseline-calculations")
    @PreAuthorize("(#customerId == authentication.name and hasAuthority('DETECTION_CALCULATE')) or "
            + "hasAuthority('DETECTION_CALCULATE_ALL')")
    public ResponseEntity<ApiResponse<BaselineCalculation>> calculate(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestHeader("Idempotency-Key")
            @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey) {
        return ApiResponses.accepted("BASELINE_CALCULATION_COMPLETED",
                "현재 합성 snapshot으로 기준선 계산을 완료했습니다.",
                detectionQueryService.calculate(customerId, idempotencyKey));
    }

    @GetMapping("/signals")
    public ResponseEntity<ApiResponse<SignalList>> signals(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @Pattern(regexp = "LOW|MEDIUM|HIGH") String severity,
            @RequestParam(required = false) @Pattern(regexp = "OPEN|ACKNOWLEDGED|CLOSED") String status) {
        return ApiResponses.ok("CUSTOMER_SIGNALS_RETRIEVED", "고객 변화신호를 조회했습니다.",
                detectionQueryService.signals(customerId, severity, status));
    }
}
