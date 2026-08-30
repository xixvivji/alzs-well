package com.alzswell.customer.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.customer.api.CustomerRequests.AccessibilitySettingsCommand;
import com.alzswell.customer.api.CustomerRequests.DisplayProfileCommand;
import com.alzswell.customer.api.CustomerRequests.PreferencesCommand;
import com.alzswell.customer.api.CustomerResponses.AccessibilitySettings;
import com.alzswell.customer.api.CustomerResponses.CustomerSummary;
import com.alzswell.customer.api.CustomerResponses.DataSummary;
import com.alzswell.customer.api.CustomerResponses.DisplayProfile;
import com.alzswell.customer.api.CustomerResponses.Preferences;
import com.alzswell.customer.application.CustomerProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
@PreAuthorize("(#customerId == authentication.name and hasAuthority('CUSTOMER_PROFILE_READ')) or "
        + "hasAuthority('CUSTOMER_PROFILE_READ_ALL')")
public class CustomerController {

    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";

    private final CustomerProfileService customerProfileService;

    public CustomerController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerSummary>> getCustomerSummary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_SUMMARY_RETRIEVED",
                "고객 요약 정보를 조회했습니다.",
                customerProfileService.getCustomerSummary(customerId)
        );
    }

    @PatchMapping("/{customerId}/display-profile")
    @PreAuthorize("(#customerId == authentication.name and hasAuthority('CUSTOMER_PROFILE_WRITE')) or "
            + "hasAuthority('CUSTOMER_PROFILE_WRITE_ALL')")
    public ResponseEntity<ApiResponse<DisplayProfile>> updateDisplayProfile(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody DisplayProfileCommand request
    ) {
        return ApiResponses.ok(
                "CUSTOMER_DISPLAY_PROFILE_UPDATED",
                "표시 프로필을 갱신했습니다.",
                customerProfileService.updateDisplayProfile(customerId, request, idempotencyKey)
        );
    }

    @GetMapping("/{customerId}/preferences")
    public ResponseEntity<ApiResponse<Preferences>> getPreferences(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_PREFERENCES_RETRIEVED",
                "서비스 환경설정을 조회했습니다.",
                customerProfileService.getPreferences(customerId)
        );
    }

    @PatchMapping("/{customerId}/preferences")
    @PreAuthorize("(#customerId == authentication.name and hasAuthority('CUSTOMER_PROFILE_WRITE')) or "
            + "hasAuthority('CUSTOMER_PROFILE_WRITE_ALL')")
    public ResponseEntity<ApiResponse<Preferences>> patchPreferences(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody PreferencesCommand request
    ) {
        return ApiResponses.ok(
                "CUSTOMER_PREFERENCES_UPDATED",
                "서비스 환경설정을 반영했습니다.",
                customerProfileService.patchPreferences(customerId, request, idempotencyKey)
        );
    }

    @GetMapping("/{customerId}/accessibility-settings")
    public ResponseEntity<ApiResponse<AccessibilitySettings>> getAccessibilitySettings(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_ACCESSIBILITY_SETTINGS_RETRIEVED",
                "접근성 설정을 조회했습니다.",
                customerProfileService.getAccessibilitySettings(customerId)
        );
    }

    @PutMapping("/{customerId}/accessibility-settings")
    @PreAuthorize("(#customerId == authentication.name and hasAuthority('CUSTOMER_PROFILE_WRITE')) or "
            + "hasAuthority('CUSTOMER_PROFILE_WRITE_ALL')")
    public ResponseEntity<ApiResponse<AccessibilitySettings>> putAccessibilitySettings(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody AccessibilitySettingsCommand request
    ) {
        return ApiResponses.ok(
                "CUSTOMER_ACCESSIBILITY_SETTINGS_UPDATED",
                "접근성 설정을 반영했습니다.",
                customerProfileService.putAccessibilitySettings(customerId, request, idempotencyKey)
        );
    }

    @GetMapping("/{customerId}/data-summary")
    public ResponseEntity<ApiResponse<DataSummary>> getDataSummary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId
    ) {
        return ApiResponses.ok(
                "CUSTOMER_DATA_SUMMARY_RETRIEVED",
                "보유 데이터 범위를 조회했습니다.",
                customerProfileService.getDataSummary(customerId)
        );
    }
}
