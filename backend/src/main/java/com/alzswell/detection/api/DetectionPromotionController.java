package com.alzswell.detection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.detection.api.DetectionPromotionResponses.DetectionPromotion;
import com.alzswell.detection.application.DetectionPromotionService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/detection-runs/{detectionRunId}/promotion")
public class DetectionPromotionController {
    private final DetectionPromotionService promotionService;

    public DetectionPromotionController(DetectionPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DETECTION_PROMOTE')")
    public ResponseEntity<ApiResponse<DetectionPromotion>> promote(
            @PathVariable UUID detectionRunId, Authentication authentication) {
        return ApiResponses.created("DETECTION_RUN_PROMOTED",
                "합성 탐지 실행 결과를 운영형 변화신호와 경보로 승격했습니다.",
                promotionService.promote(detectionRunId, AuditActor.from(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DETECTION_PROMOTION_READ')")
    public ResponseEntity<ApiResponse<DetectionPromotion>> promotion(@PathVariable UUID detectionRunId) {
        return ApiResponses.ok("DETECTION_RUN_PROMOTION_RETRIEVED", "탐지 실행 승격 결과를 조회했습니다.",
                promotionService.promotion(detectionRunId));
    }
}
