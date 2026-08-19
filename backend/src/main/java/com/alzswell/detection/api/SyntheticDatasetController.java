package com.alzswell.detection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.detection.api.SyntheticDatasetRequests.CreateDatasetCommand;
import com.alzswell.detection.api.SyntheticDatasetResponses.DatasetDetail;
import com.alzswell.detection.api.SyntheticDatasetResponses.DatasetIngestion;
import com.alzswell.detection.api.SyntheticDatasetResponses.DatasetValidation;
import com.alzswell.detection.application.SyntheticDatasetService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/synthetic-datasets")
@PreAuthorize("hasAuthority('SYNTHETIC_DATASET_ADMIN')")
public class SyntheticDatasetController {
    private final SyntheticDatasetService syntheticDatasetService;

    public SyntheticDatasetController(SyntheticDatasetService syntheticDatasetService) {
        this.syntheticDatasetService = syntheticDatasetService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DatasetDetail>> create(@Valid @RequestBody CreateDatasetCommand command) {
        return ApiResponses.created("SYNTHETIC_DATASET_CREATED", "합성 탐지 데이터셋 초안을 등록했습니다.",
                syntheticDatasetService.create(command));
    }

    @GetMapping("/{datasetId}")
    public ResponseEntity<ApiResponse<DatasetDetail>> dataset(@PathVariable UUID datasetId) {
        return ApiResponses.ok("SYNTHETIC_DATASET_RETRIEVED", "합성 탐지 데이터셋을 조회했습니다.",
                syntheticDatasetService.dataset(datasetId));
    }

    @PostMapping("/{datasetId}/validate")
    public ResponseEntity<ApiResponse<DatasetValidation>> validate(@PathVariable UUID datasetId) {
        return ApiResponses.ok("SYNTHETIC_DATASET_VALIDATED", "합성 탐지 데이터셋을 검증했습니다.",
                syntheticDatasetService.validate(datasetId));
    }

    @PostMapping("/{datasetId}/ingest")
    public ResponseEntity<ApiResponse<DatasetIngestion>> ingest(@PathVariable UUID datasetId) {
        return ApiResponses.ok("SYNTHETIC_DATASET_INGESTED", "검증된 합성 탐지 데이터셋을 적재했습니다.",
                syntheticDatasetService.ingest(datasetId));
    }
}
