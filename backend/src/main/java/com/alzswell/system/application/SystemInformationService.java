package com.alzswell.system.application;

import com.alzswell.system.api.PublicConfigResponse;
import com.alzswell.system.api.SystemHealthResponse;
import com.alzswell.system.api.SystemReadinessResponse;
import com.alzswell.system.api.SystemVersionsResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemInformationService {

    private static final List<String> SUPPORTED_SCENARIO_IDS = List.of("FIN_MGMT_AB_001");

    private final JdbcTemplate jdbcTemplate;
    private final AiDeploymentReadiness aiDeploymentReadiness;
    private final String serviceName;
    private final String applicationVersion;
    private final boolean syntheticDataOnly;
    private final boolean externalActionsEnabled;
    private final String networkMode;
    private final boolean externalEgressEnabled;
    private final boolean remoteModelEnabled;
    private final boolean syntheticProviderOnly;
    private final long sessionTtlSeconds;
    private final String defaultLocale;
    private final String apiVersion;
    private final String schemaVersion;
    private final String fixtureVersion;
    private final String algorithmVersion;
    private final String policyVersion;
    private final LocalDate sourceCatalogCheckedAt;

    public SystemInformationService(
            JdbcTemplate jdbcTemplate,
            AiDeploymentReadiness aiDeploymentReadiness,
            @Value("${spring.application.name}") String serviceName,
            @Value("${spring.application.version:0.0.1-SNAPSHOT}") String applicationVersion,
            @Value("${app.guardrails.synthetic-data-only:true}") boolean syntheticDataOnly,
            @Value("${app.guardrails.external-actions-enabled:false}") boolean externalActionsEnabled,
            @Value("${app.guardrails.network-mode:AIR_GAPPED_DEMO}") String networkMode,
            @Value("${app.guardrails.external-egress-enabled:false}") boolean externalEgressEnabled,
            @Value("${app.guardrails.remote-model-enabled:false}") boolean remoteModelEnabled,
            @Value("${app.guardrails.synthetic-provider-only:true}") boolean syntheticProviderOnly,
            @Value("${app.demo.session-ttl-seconds:7200}") long sessionTtlSeconds,
            @Value("${app.demo.default-locale:ko-KR}") String defaultLocale,
            @Value("${app.versions.api:v1}") String apiVersion,
            @Value("${app.versions.schema}") String schemaVersion,
            @Value("${app.versions.fixture:fin-mgmt-ab-v2.0.0}") String fixtureVersion,
            @Value("${app.versions.algorithm:baseline-rules-v2.0.0}") String algorithmVersion,
            @Value("${app.versions.policy:context-policy-v1.0.0}") String policyVersion,
            @Value("${app.versions.source-catalog-checked-at:2026-08-14}") LocalDate sourceCatalogCheckedAt
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiDeploymentReadiness = aiDeploymentReadiness;
        this.serviceName = serviceName;
        this.applicationVersion = applicationVersion;
        this.syntheticDataOnly = syntheticDataOnly;
        this.externalActionsEnabled = externalActionsEnabled;
        this.networkMode = networkMode;
        this.externalEgressEnabled = externalEgressEnabled;
        this.remoteModelEnabled = remoteModelEnabled;
        this.syntheticProviderOnly = syntheticProviderOnly;
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.defaultLocale = defaultLocale;
        this.apiVersion = apiVersion;
        this.schemaVersion = schemaVersion;
        this.fixtureVersion = fixtureVersion;
        this.algorithmVersion = algorithmVersion;
        this.policyVersion = policyVersion;
        this.sourceCatalogCheckedAt = sourceCatalogCheckedAt;
    }

    public SystemHealthResponse health() {
        return new SystemHealthResponse("UP", serviceName, syntheticDataOnly, externalActionsEnabled);
    }

    public SystemReadinessResponse readiness() {
        Map<String, String> checks = new LinkedHashMap<>();
        boolean databaseReady;
        boolean flywayReady;
        try {
            databaseReady = Integer.valueOf(1).equals(jdbcTemplate.queryForObject("select 1", Integer.class));
            String latestSuccessfulVersion = jdbcTemplate.queryForObject(
                    """
                    select version
                      from flyway_schema_history
                     where success = true and version is not null
                     order by installed_rank desc
                     limit 1
                    """,
                    String.class
            );
            Integer failedMigrationCount = jdbcTemplate.queryForObject(
                    "select count(*) from flyway_schema_history where success = false",
                    Integer.class
            );
            flywayReady = schemaVersion.equals(latestSuccessfulVersion)
                    && Integer.valueOf(0).equals(failedMigrationCount);
        } catch (DataAccessException exception) {
            databaseReady = false;
            flywayReady = false;
        }

        boolean fixtureReady = false;
        boolean policyReady = false;
        boolean detectionPolicyReady = false;
        boolean safeGuardrails = syntheticDataOnly
                && !externalActionsEnabled
                && "AIR_GAPPED_DEMO".equals(networkMode)
                && !externalEgressEnabled
                && !remoteModelEnabled
                && syntheticProviderOnly;
        if (databaseReady && flywayReady) {
            try {
                Integer fixtureCount = jdbcTemplate.queryForObject("""
                        select count(*) from demo_fixture_catalog
                         where scenario_id = 'FIN_MGMT_AB_001' and fixture_version = ? and enabled = true
                           and expected_connection_count = 2 and expected_account_count = 4
                           and expected_transaction_count = 42
                           and expected_baseline_count = 3 and expected_trend_count = 12
                           and expected_interaction_count = 8 and expected_signal_count = 3
                        """, Integer.class, fixtureVersion);
                Integer policyCount = jdbcTemplate.queryForObject("""
                        select count(*) from protection_action_catalog
                         where execution_type = 'GUIDANCE_ONLY' and checked_at is not null
                        """, Integer.class);
                Integer activeDetectionPolicyCount = jdbcTemplate.queryForObject(
                        "select count(*) from detection_policy_version where status = 'ACTIVE'",
                        Integer.class);
                fixtureReady = Integer.valueOf(1).equals(fixtureCount);
                policyReady = policyCount != null && policyCount >= 2 && !policyVersion.isBlank();
                detectionPolicyReady = Integer.valueOf(1).equals(activeDetectionPolicyCount);
            } catch (DataAccessException exception) {
                fixtureReady = false;
                policyReady = false;
                detectionPolicyReady = false;
            }
        }
        checks.put("database", upOrDown(databaseReady));
        checks.put("flyway", upOrDown(flywayReady));
        checks.put("syntheticFixtures", upOrDown(fixtureReady));
        checks.put("policyCatalog", upOrDown(policyReady));
        checks.put("detectionPolicy", upOrDown(detectionPolicyReady));
        checks.put("safeGuardrails", upOrDown(safeGuardrails));
        AiDeploymentReadiness.Result aiReadiness = aiDeploymentReadiness.verify();
        checks.put("aiRetrieval", aiReadiness.status());

        boolean ready = databaseReady && flywayReady && fixtureReady && policyReady
                && detectionPolicyReady && safeGuardrails && aiReadiness.ready();
        return new SystemReadinessResponse(ready, ready ? "READY" : "NOT_READY", checks);
    }

    public PublicConfigResponse publicConfig() {
        return new PublicConfigResponse(
                apiVersion,
                "SYNTHETIC_ONLY",
                syntheticDataOnly,
                externalActionsEnabled,
                networkMode,
                externalEgressEnabled,
                remoteModelEnabled,
                syntheticProviderOnly,
                SUPPORTED_SCENARIO_IDS,
                defaultLocale,
                sessionTtlSeconds,
                new PublicConfigResponse.FeatureFlags(false, true, false)
        );
    }

    public SystemVersionsResponse versions() {
        return new SystemVersionsResponse(
                applicationVersion,
                apiVersion,
                schemaVersion,
                fixtureVersion,
                algorithmVersion,
                policyVersion,
                sourceCatalogCheckedAt
        );
    }

    private String upOrDown(boolean ready) {
        return ready ? "UP" : "DOWN";
    }

}
