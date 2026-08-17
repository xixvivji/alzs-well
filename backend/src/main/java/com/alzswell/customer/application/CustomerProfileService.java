package com.alzswell.customer.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import com.alzswell.customer.api.CustomerErrorCode;
import com.alzswell.customer.api.CustomerRequests.AccessibilitySettingsCommand;
import com.alzswell.customer.api.CustomerRequests.DisplayProfileCommand;
import com.alzswell.customer.api.CustomerRequests.PreferencesCommand;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.features.customer-profile-api-enabled", havingValue = "true")
public class CustomerProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CustomerProfileService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerSummary(String customerId) {
        return single(jdbcTemplate.query(
                """
                select customer_id, display_name, organization, region, status,
                       row_version, created_at, updated_at
                  from customer_profile where customer_id = ?
                """,
                (rs, rowNum) -> map(
                        "customerId", rs.getString("customer_id"),
                        "displayName", rs.getString("display_name"),
                        "organization", rs.getString("organization"),
                        "region", rs.getString("region"),
                        "status", rs.getString("status"),
                        "version", rs.getLong("row_version"),
                        "createdAt", rs.getObject("created_at", OffsetDateTime.class),
                        "updatedAt", rs.getObject("updated_at", OffsetDateTime.class)
                ), customerId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDisplayProfile(String customerId) {
        Map<String, Object> summary = getCustomerSummary(customerId);
        return map(
                "customerId", customerId,
                "displayName", summary.get("displayName"),
                "version", summary.get("version"),
                "updatedAt", summary.get("updatedAt")
        );
    }

    @Transactional
    public void updateDisplayProfile(String customerId, DisplayProfileCommand request) {
        requireCustomer(customerId);
        requireUpdated(jdbcTemplate.update(
                """
                update customer_profile
                   set display_name = ?, row_version = row_version + 1, updated_at = ?
                 where customer_id = ? and row_version = ?
                """,
                request.displayName().trim(), OffsetDateTime.now(clock), customerId, request.expectedVersion()
        ));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPreferences(String customerId) {
        requireCustomer(customerId);
        return single(jdbcTemplate.query(
                """
                select sms_notification_enabled, push_notification_enabled,
                       in_app_notification_enabled, row_version, updated_at
                  from customer_preferences where customer_id = ?
                """,
                (rs, rowNum) -> map(
                        "customerId", customerId,
                        "smsNotificationEnabled", rs.getBoolean("sms_notification_enabled"),
                        "pushNotificationEnabled", rs.getBoolean("push_notification_enabled"),
                        "inAppNotificationEnabled", rs.getBoolean("in_app_notification_enabled"),
                        "version", rs.getLong("row_version"),
                        "updatedAt", rs.getObject("updated_at", OffsetDateTime.class)
                ), customerId));
    }

    @Transactional
    public void patchPreferences(String customerId, PreferencesCommand request) {
        requireCustomer(customerId);
        if (request.smsNotificationEnabled() == null
                && request.pushNotificationEnabled() == null
                && request.inAppNotificationEnabled() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "변경할 환경설정을 하나 이상 입력해 주세요.");
        }
        requireUpdated(jdbcTemplate.update(
                """
                update customer_preferences
                   set sms_notification_enabled = coalesce(?, sms_notification_enabled),
                       push_notification_enabled = coalesce(?, push_notification_enabled),
                       in_app_notification_enabled = coalesce(?, in_app_notification_enabled),
                       row_version = row_version + 1, updated_at = ?
                 where customer_id = ? and row_version = ?
                """,
                request.smsNotificationEnabled(), request.pushNotificationEnabled(),
                request.inAppNotificationEnabled(), OffsetDateTime.now(clock),
                customerId, request.expectedVersion()
        ));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAccessibilitySettings(String customerId) {
        requireCustomer(customerId);
        return single(jdbcTemplate.query(
                """
                select large_font, high_contrast, speech_guidance, one_hand_mode,
                       row_version, updated_at
                  from customer_accessibility_settings where customer_id = ?
                """,
                (rs, rowNum) -> map(
                        "customerId", customerId,
                        "largeFont", rs.getBoolean("large_font"),
                        "highContrast", rs.getBoolean("high_contrast"),
                        "speechGuidance", rs.getBoolean("speech_guidance"),
                        "oneHandMode", rs.getBoolean("one_hand_mode"),
                        "version", rs.getLong("row_version"),
                        "updatedAt", rs.getObject("updated_at", OffsetDateTime.class)
                ), customerId));
    }

    @Transactional
    public void putAccessibilitySettings(String customerId, AccessibilitySettingsCommand request) {
        requireCustomer(customerId);
        requireUpdated(jdbcTemplate.update(
                """
                update customer_accessibility_settings
                   set large_font = ?, high_contrast = ?, speech_guidance = ?, one_hand_mode = ?,
                       row_version = row_version + 1, updated_at = ?
                 where customer_id = ? and row_version = ?
                """,
                request.largeFont(), request.highContrast(), request.speechGuidance(), request.oneHandMode(),
                OffsetDateTime.now(clock), customerId, request.expectedVersion()
        ));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDataSummary(String customerId) {
        requireCustomer(customerId);
        return single(jdbcTemplate.query(
                """
                select institution_count, account_count, transaction_count,
                       account_freshness, transaction_freshness, baseline_freshness,
                       last_sync_at, updated_at
                  from customer_data_inventory where customer_id = ?
                """,
                (rs, rowNum) -> map(
                        "customerId", customerId,
                        "institutions", rs.getInt("institution_count"),
                        "accounts", rs.getInt("account_count"),
                        "transactionsSynced", rs.getInt("transaction_count"),
                        "lastSyncAt", rs.getObject("last_sync_at", OffsetDateTime.class),
                        "dataFreshness", map(
                                "accounts", rs.getString("account_freshness"),
                                "transactions", rs.getString("transaction_freshness"),
                                "baseline", rs.getString("baseline_freshness")
                        ),
                        "updatedAt", rs.getObject("updated_at", OffsetDateTime.class)
                ), customerId));
    }

    private void requireCustomer(String customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_profile where customer_id = ?", Integer.class, customerId
        );
        if (count == null || count == 0) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }
    }

    private Map<String, Object> single(List<Map<String, Object>> rows) {
        if (rows.size() != 1) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_VERSION_CONFLICT);
        }
    }

    private Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }
}
