package com.alzswell.customer.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import com.alzswell.customer.api.CustomerErrorCode;
import com.alzswell.customer.api.CustomerRequests.AccessibilitySettingsCommand;
import com.alzswell.customer.api.CustomerRequests.DisplayProfileCommand;
import com.alzswell.customer.api.CustomerRequests.PreferencesCommand;
import com.alzswell.customer.api.CustomerResponses.AccessibilitySettings;
import com.alzswell.customer.api.CustomerResponses.CustomerSummary;
import com.alzswell.customer.api.CustomerResponses.DataFreshness;
import com.alzswell.customer.api.CustomerResponses.DataSummary;
import com.alzswell.customer.api.CustomerResponses.DisplayProfile;
import com.alzswell.customer.api.CustomerResponses.Preferences;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
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
    public CustomerSummary getCustomerSummary(String customerId) {
        return single(jdbcTemplate.query(
                """
                select customer_id, display_name, organization, region, status,
                       row_version, created_at, updated_at
                  from customer_profile where customer_id = ?
                """,
                (rs, rowNum) -> new CustomerSummary(
                        rs.getString("customer_id"),
                        rs.getString("display_name"),
                        rs.getString("organization"),
                        rs.getString("region"),
                        rs.getString("status"),
                        rs.getLong("row_version"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ), customerId));
    }

    @Transactional(readOnly = true)
    public DisplayProfile getDisplayProfile(String customerId) {
        CustomerSummary summary = getCustomerSummary(customerId);
        return new DisplayProfile(
                customerId,
                summary.displayName(),
                summary.version(),
                summary.updatedAt()
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
    public Preferences getPreferences(String customerId) {
        requireCustomer(customerId);
        return single(jdbcTemplate.query(
                """
                select sms_notification_enabled, push_notification_enabled,
                       in_app_notification_enabled, row_version, updated_at
                  from customer_preferences where customer_id = ?
                """,
                (rs, rowNum) -> new Preferences(
                        customerId,
                        rs.getBoolean("sms_notification_enabled"),
                        rs.getBoolean("push_notification_enabled"),
                        rs.getBoolean("in_app_notification_enabled"),
                        rs.getLong("row_version"),
                        rs.getObject("updated_at", OffsetDateTime.class)
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
    public AccessibilitySettings getAccessibilitySettings(String customerId) {
        requireCustomer(customerId);
        return single(jdbcTemplate.query(
                """
                select large_font, high_contrast, speech_guidance, one_hand_mode,
                       row_version, updated_at
                  from customer_accessibility_settings where customer_id = ?
                """,
                (rs, rowNum) -> new AccessibilitySettings(
                        customerId,
                        rs.getBoolean("large_font"),
                        rs.getBoolean("high_contrast"),
                        rs.getBoolean("speech_guidance"),
                        rs.getBoolean("one_hand_mode"),
                        rs.getLong("row_version"),
                        rs.getObject("updated_at", OffsetDateTime.class)
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
    public DataSummary getDataSummary(String customerId) {
        requireCustomer(customerId);
        return single(jdbcTemplate.query(
                """
                select institution_count, account_count, transaction_count,
                       account_freshness, transaction_freshness, baseline_freshness,
                       last_sync_at, updated_at
                  from customer_data_inventory where customer_id = ?
                """,
                (rs, rowNum) -> new DataSummary(
                        customerId,
                        rs.getInt("institution_count"),
                        rs.getInt("account_count"),
                        rs.getInt("transaction_count"),
                        rs.getObject("last_sync_at", OffsetDateTime.class),
                        new DataFreshness(
                                rs.getString("account_freshness"),
                                rs.getString("transaction_freshness"),
                                rs.getString("baseline_freshness")
                        ),
                        rs.getObject("updated_at", OffsetDateTime.class)
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

    private <T> T single(List<T> rows) {
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

}
