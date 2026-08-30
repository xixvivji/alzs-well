package com.alzswell.detection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.detection.api.SyntheticDatasetRequests.CreateDetectionRunCommand;
import com.alzswell.detection.api.SyntheticDatasetResponses.DetectionRun;
import com.alzswell.detection.application.SyntheticDatasetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class DetectionRunController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final SyntheticDatasetService syntheticDatasetService;

    public DetectionRunController(SyntheticDatasetService syntheticDatasetService) {
        this.syntheticDatasetService = syntheticDatasetService;
    }

    @PostMapping("/api/v1/customers/{customerId}/detection-runs")
    @PreAuthorize("hasAuthority('DETECTION_RUN_CREATE')")
    public ResponseEntity<ApiResponse<DetectionRun>> create(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody CreateDetectionRunCommand command) {
        return ApiResponses.accepted("DETECTION_RUN_COMPLETED", "합성 데이터셋 탐지 실행을 완료했습니다.",
                syntheticDatasetService.run(customerId, command.datasetId(), idempotencyKey));
    }

    @GetMapping("/api/v1/detection-runs/{detectionRunId}")
    @PreAuthorize("hasAuthority('DETECTION_RUN_READ')")
    public ResponseEntity<ApiResponse<DetectionRun>> run(@PathVariable UUID detectionRunId) {
        return ApiResponses.ok("DETECTION_RUN_RETRIEVED", "합성 데이터셋 탐지 실행 결과를 조회했습니다.",
                syntheticDatasetService.run(detectionRunId));
    }
}
