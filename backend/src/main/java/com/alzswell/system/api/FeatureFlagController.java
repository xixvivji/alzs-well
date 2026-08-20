package com.alzswell.system.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.system.api.FeatureFlagRequests.UpdateFeatureFlagCommand;
import com.alzswell.system.api.FeatureFlagResponses.FeatureFlag;
import com.alzswell.system.api.FeatureFlagResponses.FeatureFlagList;
import com.alzswell.system.application.FeatureFlagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/feature-flags")
public class FeatureFlagController {
    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('FEATURE_FLAG_READ')")
    public ResponseEntity<ApiResponse<FeatureFlagList>> flags() {
        return ApiResponses.ok("FEATURE_FLAGS_RETRIEVED", "환경별 기능 플래그를 조회했습니다.", service.flags());
    }

    @PutMapping("/{flagKey}")
    @PreAuthorize("hasAuthority('FEATURE_FLAG_WRITE')")
    public ResponseEntity<ApiResponse<FeatureFlag>> update(
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{2,79}") String flagKey,
            @Valid @RequestBody UpdateFeatureFlagCommand command,
            Authentication authentication) {
        return ApiResponses.ok("FEATURE_FLAG_CHANGE_RECORDED",
                "승인된 기능 플래그 희망값을 기록했습니다. 런타임 적용은 배포 설정과 재기동이 필요합니다.",
                service.update(flagKey, command, AuditActor.from(authentication)));
    }
}
