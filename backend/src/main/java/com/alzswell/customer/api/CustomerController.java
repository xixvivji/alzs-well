package com.alzswell.customer.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.customer.api.CustomerRequests.AccessibilitySettingsCommand;
import com.alzswell.customer.api.CustomerRequests.DisplayProfileCommand;
import com.alzswell.customer.api.CustomerRequests.PreferencesCommand;
import com.alzswell.customer.application.CustomerProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@ConditionalOnProperty(
        name = "app.features.customer-profile-api-enabled",
        havingValue = "true"
)
@Validated
@PreAuthorize("#customerId == authentication.name or hasAuthority('CUSTOMER_PROFILE_READ_ALL')")
public class CustomerController {

    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";

    private final CustomerProfileService customerProfileService;

    public CustomerController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getCustomerSummary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_SUMMARY_RETRIEVED",
                "고객 요약 정보를 조회했습니다.",
                customerProfileService.getCustomerSummary(customerId)
        );
    }

    @PatchMapping("/{customerId}/display-profile")
    public ResponseEntity<ApiResponse<Object>> updateDisplayProfile(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @Valid @RequestBody DisplayProfileCommand request
    ) {
        customerProfileService.updateDisplayProfile(customerId, request);
        return ApiResponses.ok(
                "CUSTOMER_DISPLAY_PROFILE_UPDATED",
                "표시 프로필을 갱신했습니다.",
                customerProfileService.getDisplayProfile(customerId)
        );
    }

    @GetMapping("/{customerId}/preferences")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getPreferences(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_PREFERENCES_RETRIEVED",
                "서비스 환경설정을 조회했습니다.",
                customerProfileService.getPreferences(customerId)
        );
    }

    @PatchMapping("/{customerId}/preferences")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> patchPreferences(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @Valid @RequestBody PreferencesCommand request
    ) {
        customerProfileService.patchPreferences(customerId, request);
        return ApiResponses.ok(
                "CUSTOMER_PREFERENCES_UPDATED",
                "서비스 환경설정을 반영했습니다.",
                customerProfileService.getPreferences(customerId)
        );
    }

    @GetMapping("/{customerId}/accessibility-settings")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getAccessibilitySettings(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_ACCESSIBILITY_SETTINGS_RETRIEVED",
                "접근성 설정을 조회했습니다.",
                customerProfileService.getAccessibilitySettings(customerId)
        );
    }

    @PutMapping("/{customerId}/accessibility-settings")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> putAccessibilitySettings(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @Valid @RequestBody AccessibilitySettingsCommand request
    ) {
        customerProfileService.putAccessibilitySettings(customerId, request);
        return ApiResponses.ok(
                "CUSTOMER_ACCESSIBILITY_SETTINGS_UPDATED",
                "접근성 설정을 반영했습니다.",
                customerProfileService.getAccessibilitySettings(customerId)
        );
    }

    @GetMapping("/{customerId}/data-summary")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getDataSummary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_DATA_SUMMARY_RETRIEVED",
                "보유 데이터 범위를 조회했습니다.",
                customerProfileService.getDataSummary(customerId)
        );
    }
}
