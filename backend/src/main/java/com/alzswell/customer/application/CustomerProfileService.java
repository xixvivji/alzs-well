package com.alzswell.customer.application;

import com.alzswell.customer.api.CustomerRequests.AccessibilitySettingsCommand;
import com.alzswell.customer.api.CustomerRequests.DisplayProfileCommand;
import com.alzswell.customer.api.CustomerRequests.PreferencesCommand;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.features.customer-profile-api-enabled",
        havingValue = "true"
)
public class CustomerProfileService {

    private static final String DEFAULT_CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final String DEFAULT_DISPLAY_NAME = "이용자 001";

    private final Clock clock;
    private final ConcurrentMap<String, CustomerRow> customers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PreferencesRow> preferences = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AccessibilityRow> accessibilitySettings = new ConcurrentHashMap<>();

    public CustomerProfileService(Clock clock) {
        this.clock = clock;
    }

    public Map<String, Object> getCustomerSummary(String customerId) {
        CustomerRow row = customers.computeIfAbsent(customerId, id -> new CustomerRow(
                id, DEFAULT_DISPLAY_NAME, "고령자보호센터", "KR-11", "ACTIVE"));
        return row.toSummary();
    }

    public Map<String, Object> getDisplayProfile(String customerId) {
        CustomerRow row = customers.computeIfAbsent(customerId, id -> new CustomerRow(
                id, DEFAULT_DISPLAY_NAME, "고령자보호센터", "KR-11", "ACTIVE"));
        return row.toDisplayProfile();
    }

    public void updateDisplayProfile(String customerId, DisplayProfileCommand request) {
        customers.compute(customerId, (id, existing) -> {
            CustomerRow row = existing == null
                    ? new CustomerRow(id, DEFAULT_DISPLAY_NAME, "고령자보호센터", "KR-11", "ACTIVE")
                    : existing;
            return new CustomerRow(
                    row.customerId(),
                    request.displayName(),
                    row.organization(),
                    row.region(),
                    row.status()
            );
        });
    }

    public Map<String, Object> getPreferences(String customerId) {
        PreferencesRow row = preferences.computeIfAbsent(customerId, id -> new PreferencesRow(
                true, true, true, OffsetDateTime.now(clock)));
        return row.toMap();
    }

    public void patchPreferences(String customerId, PreferencesCommand request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        preferences.put(customerId, new PreferencesRow(
                request.smsNotificationEnabled(),
                request.pushNotificationEnabled(),
                request.inAppNotificationEnabled(),
                now
        ));
    }

    public Map<String, Object> getAccessibilitySettings(String customerId) {
        AccessibilityRow row = accessibilitySettings.computeIfAbsent(customerId, id -> new AccessibilityRow(
                true, false, false, true, OffsetDateTime.now(clock)));
        return row.toMap();
    }

    public void putAccessibilitySettings(String customerId, AccessibilitySettingsCommand request) {
        accessibilitySettings.put(customerId, new AccessibilityRow(
                request.largeFont(),
                request.highContrast(),
                request.speechGuidance(),
                request.oneHandMode(),
                OffsetDateTime.now(clock)
        ));
    }

    public Map<String, Object> getDataSummary(String customerId) {
        getCustomerSummary(customerId); // ensure customer exists
        return new LinkedHashMap<>() {{
            put("customerId", customerId);
            put("institutions", 2);
            put("accounts", 4);
            put("transactionsSynced", 42);
            put("lastSyncAt", OffsetDateTime.now(clock).minusDays(1));
            put("dataFreshness", Map.of(
                    "accounts", "LATEST",
                    "transactions", "WITHIN_24H",
                    "baseline", "CURRENT"
            ));
        }};
    }

    private record CustomerRow(String customerId, String displayName, String organization, String region, String status) {
        public Map<String, Object> toSummary() {
            return Map.of(
                    "customerId", customerId,
                    "displayName", displayName,
                    "organization", organization,
                    "region", region,
                    "status", status
            );
        }

        public Map<String, Object> toDisplayProfile() {
            return Map.of("customerId", customerId, "displayName", displayName);
        }
    }

    private record PreferencesRow(Boolean smsNotificationEnabled, Boolean pushNotificationEnabled,
                                 Boolean inAppNotificationEnabled, OffsetDateTime updatedAt) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "smsNotificationEnabled", smsNotificationEnabled,
                    "pushNotificationEnabled", pushNotificationEnabled,
                    "inAppNotificationEnabled", inAppNotificationEnabled,
                    "updatedAt", updatedAt
            );
        }
    }

    private record AccessibilityRow(Boolean largeFont, Boolean highContrast, Boolean speechGuidance,
                                   Boolean oneHandMode, OffsetDateTime updatedAt) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "largeFont", largeFont,
                    "highContrast", highContrast,
                    "speechGuidance", speechGuidance,
                    "oneHandMode", oneHandMode,
                    "updatedAt", updatedAt
            );
        }
    }
}
