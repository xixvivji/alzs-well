package com.alzswell.detection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.detection.api.DetectionResponses.SignalDetail;
import com.alzswell.detection.api.DetectionResponses.SignalEvidenceList;
import com.alzswell.detection.application.DetectionQueryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signals")
@PreAuthorize("hasAnyAuthority('DETECTION_READ', 'DETECTION_READ_ALL')")
public class SignalController {
    private final DetectionQueryService detectionQueryService;

    public SignalController(DetectionQueryService detectionQueryService) {
        this.detectionQueryService = detectionQueryService;
    }

    @GetMapping("/{signalId}")
    public ResponseEntity<ApiResponse<SignalDetail>> signal(
            @PathVariable UUID signalId, Authentication authentication) {
        return ApiResponses.ok("SIGNAL_RETRIEVED", "변화신호 상세를 조회했습니다.",
                detectionQueryService.signal(signalId, authentication.getName(), hasReadAll(authentication)));
    }

    @GetMapping("/{signalId}/evidence")
    public ResponseEntity<ApiResponse<SignalEvidenceList>> evidence(
            @PathVariable UUID signalId, Authentication authentication) {
        return ApiResponses.ok("SIGNAL_EVIDENCE_RETRIEVED", "변화신호의 불변 근거를 조회했습니다.",
                detectionQueryService.evidence(signalId, authentication.getName(), hasReadAll(authentication)));
    }

    private boolean hasReadAll(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("DETECTION_READ_ALL"));
    }
}
