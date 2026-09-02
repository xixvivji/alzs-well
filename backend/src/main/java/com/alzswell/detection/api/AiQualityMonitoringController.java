package com.alzswell.detection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.detection.api.AiQualityResponses.AiQualitySummary;
import com.alzswell.detection.application.AiQualityMonitoringService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/ai-quality")
public class AiQualityMonitoringController {
    private final AiQualityMonitoringService service;

    public AiQualityMonitoringController(AiQualityMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DETECTION_POLICY_READ')")
    public ResponseEntity<ApiResponse<AiQualitySummary>> summary(
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours
    ) {
        return ApiResponses.ok("AI_QUALITY_SUMMARY_RETRIEVED", "AI 운영 품질을 집계했습니다.",
                service.summary(hours));
    }
}
