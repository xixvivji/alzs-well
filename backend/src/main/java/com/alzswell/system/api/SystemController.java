package com.alzswell.system.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.system.application.SystemInformationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemInformationService systemInformationService;

    public SystemController(SystemInformationService systemInformationService) {
        this.systemInformationService = systemInformationService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<SystemHealthResponse>> health() {
        return ApiResponses.ok(
                "SYSTEM_HEALTHY",
                "서비스가 정상 동작 중입니다.",
                systemInformationService.health()
        );
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<SystemReadinessResponse>> readiness() {
        SystemReadinessResponse response = systemInformationService.readiness();
        if (!response.ready()) {
            return ApiResponses.errorWithData(
                    SystemErrorCode.SYSTEM_NOT_READY,
                    SystemErrorCode.SYSTEM_NOT_READY.message(),
                    response
            );
        }
        return ApiResponses.ok("SYSTEM_READY", "공개 데모 실행 준비가 완료되었습니다.", response);
    }

    @GetMapping("/public-config")
    public ResponseEntity<ApiResponse<PublicConfigResponse>> publicConfig() {
        return ApiResponses.ok(
                "PUBLIC_CONFIG_RETRIEVED",
                "공개 데모 설정을 조회했습니다.",
                systemInformationService.publicConfig()
        );
    }

    @GetMapping("/versions")
    public ResponseEntity<ApiResponse<SystemVersionsResponse>> versions() {
        return ApiResponses.ok(
                "SYSTEM_VERSIONS_RETRIEVED",
                "서비스 버전을 조회했습니다.",
                systemInformationService.versions()
        );
    }
}
