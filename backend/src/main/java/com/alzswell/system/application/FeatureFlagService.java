package com.alzswell.system.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.system.api.FeatureFlagErrorCode;
import com.alzswell.system.api.FeatureFlagRequests.UpdateFeatureFlagCommand;
import com.alzswell.system.api.FeatureFlagResponses.FeatureFlag;
import com.alzswell.system.api.FeatureFlagResponses.FeatureFlagList;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final Map<String, Boolean> runtimeValues;
    private final String environment;
    private final boolean publicExposure;

    public FeatureFlagService(
            JdbcTemplate jdbcTemplate,
            Clock clock,
            Environment springEnvironment,
            @Value("${app.deployment.public-exposure:false}") boolean publicExposure,
            @Value("${app.features.customer-profile-api-enabled:false}") boolean customerProfileEnabled,
            @Value("${app.features.local-auth-api-enabled:false}") boolean localAuthEnabled,
            @Value("${app.guardrails.external-actions-enabled:false}") boolean externalActionsEnabled,
            @Value("${app.guardrails.external-egress-enabled:false}") boolean externalEgressEnabled,
            @Value("${app.guardrails.remote-model-enabled:false}") boolean remoteModelEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.publicExposure = publicExposure;
        this.runtimeValues = Map.of(
                "CUSTOMER_PROFILE_API_ENABLED", customerProfileEnabled,
                "LOCAL_AUTH_API_ENABLED", localAuthEnabled,
                "EXTERNAL_ACTIONS_ENABLED", externalActionsEnabled,
                "EXTERNAL_EGRESS_ENABLED", externalEgressEnabled,
                "REMOTE_MODEL_ENABLED", remoteModelEnabled);
        String[] profiles = springEnvironment.getActiveProfiles();
        this.environment = profiles.length == 0 ? "default" : String.join(",", Arrays.asList(profiles));
    }

    @Transactional(readOnly = true)
    public FeatureFlagList flags() {
        List<FeatureFlag> items = jdbcTemplate.query("""
                select flag_key, property_key, desired_enabled, mutable, safety_class,
                       description, row_version, updated_at
                  from operational_feature_flag order by flag_key
                """, (rs, rowNum) -> map(new Row(
                rs.getString("flag_key"), rs.getString("property_key"), rs.getBoolean("desired_enabled"),
                rs.getBoolean("mutable"), rs.getString("safety_class"), rs.getString("description"),
                rs.getLong("row_version"), rs.getObject("updated_at", OffsetDateTime.class))));
        return new FeatureFlagList(items, items.size(), environment);
    }

    @Transactional
    public FeatureFlag update(String flagKey, UpdateFeatureFlagCommand command, AuditActor actor) {
        Row row = locked(flagKey);
        if (!row.mutable()) throw new BusinessException(FeatureFlagErrorCode.IMMUTABLE);
        if (command.expectedVersion() != row.version()) {
            throw new BusinessException(FeatureFlagErrorCode.VERSION_CONFLICT);
        }
        if (publicExposure && command.enabled()) {
            throw new BusinessException(FeatureFlagErrorCode.PUBLIC_ENABLE_FORBIDDEN);
        }
        if (row.desiredEnabled() == command.enabled()) return map(row);

        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = jdbcTemplate.update("""
                update operational_feature_flag
                   set desired_enabled=?, row_version=row_version+1, updated_by=?, updated_at=?
                 where flag_key=? and row_version=? and mutable=true
                """, command.enabled(), actor.legacyActorId(), now, flagKey, command.expectedVersion());
        if (updated != 1) throw new BusinessException(FeatureFlagErrorCode.VERSION_CONFLICT);
        jdbcTemplate.update("""
                insert into feature_flag_change_event (
                    event_id, flag_key, actor_subject, previous_desired_enabled, requested_enabled,
                    approval_reference, change_reason, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), flagKey, actor.legacyActorId(), row.desiredEnabled(), command.enabled(),
                command.approvalReference(), command.changeReason().trim(), now);
        return map(locked(flagKey));
    }

    private Row locked(String flagKey) {
        List<Row> rows = jdbcTemplate.query("""
                select flag_key, property_key, desired_enabled, mutable, safety_class,
                       description, row_version, updated_at
                  from operational_feature_flag where flag_key=? for update
                """, (rs, rowNum) -> new Row(
                rs.getString("flag_key"), rs.getString("property_key"), rs.getBoolean("desired_enabled"),
                rs.getBoolean("mutable"), rs.getString("safety_class"), rs.getString("description"),
                rs.getLong("row_version"), rs.getObject("updated_at", OffsetDateTime.class)), flagKey);
        if (rows.size() != 1) throw new BusinessException(FeatureFlagErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private FeatureFlag map(Row row) {
        boolean runtime = runtimeValues.getOrDefault(row.flagKey(), false);
        boolean applied = runtime == row.desiredEnabled();
        return new FeatureFlag(row.flagKey(), row.propertyKey(), row.desiredEnabled(), runtime,
                row.mutable(), row.safetyClass(), row.description(), environment, row.version(), row.updatedAt(),
                applied, !applied, false);
    }

    private record Row(String flagKey, String propertyKey, boolean desiredEnabled, boolean mutable,
                       String safetyClass, String description, long version, OffsetDateTime updatedAt) {}
}
