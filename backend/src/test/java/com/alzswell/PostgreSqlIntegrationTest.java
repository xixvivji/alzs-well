package com.alzswell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.common.security.DemoCapabilityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine")
            .withInitScript("create-runtime-role.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flywayCreatesTheFoundationTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                      'demo_session',
                      'decision_audit',
                      'demo_idempotency_record',
                      'synthetic_consent',
                      'synthetic_connection',
                      'synthetic_connection_scope',
                      'synthetic_account',
                      'synthetic_transaction',
                      'synthetic_baseline',
                      'synthetic_baseline_reason',
                      'synthetic_financial_profile',
                      'synthetic_asset_trend',
                      'synthetic_interaction_event',
                      'synthetic_signal',
                      'demo_run',
                      'demo_fixture_catalog',
                      'protection_action_catalog'
                      ,'case_note'
                      ,'follow_up_task'
                      ,'customer_profile'
                      ,'customer_preferences'
                      ,'customer_accessibility_settings'
                      ,'customer_data_inventory'
                      ,'auth_principal'
                      ,'auth_role'
                      ,'auth_permission'
                      ,'auth_principal_role'
                      ,'auth_role_permission'
                      ,'auth_session'
                      ,'auth_session_event'
                      ,'financial_institution'
                      ,'financial_institution_scope'
                      ,'customer_connection'
                      ,'customer_connection_scope'
                      ,'customer_baseline_snapshot'
                      ,'customer_baseline_feature_snapshot'
                      ,'customer_detection_signal'
                      ,'customer_signal_evidence_snapshot'
                      ,'baseline_calculation_job'
                      ,'synthetic_detection_dataset'
                      ,'synthetic_detection_run'
                      ,'operational_alert'
                      ,'operational_alert_context_event'
                      ,'operational_alert_audit_event'
                      ,'detection_run_promotion'
                      ,'operational_protection_case'
                      ,'operational_case_review_event'
                      ,'operational_guidance_plan'
                      ,'operational_case_activity'
                      ,'operational_case_note'
                      ,'operational_case_follow_up'
                      ,'operational_case_follow_up_event'
                      ,'customer_inbox_message'
                      ,'customer_notification_preference'
                      ,'knowledge_document'
                      ,'knowledge_document_version'
                      ,'knowledge_passage'
                      ,'knowledge_document_governance'
                      ,'knowledge_governance_event'
                      ,'knowledge_access_audit_event'
                      ,'customer_protection_enrollment'
                      ,'customer_consent'
                      ,'customer_consent_scope'
                      ,'customer_consent_event'
                      ,'trusted_contact'
                      ,'trusted_contact_scope'
                      ,'trusted_contact_event'
                      ,'consent_access_audit_event'
                      ,'detection_policy_version'
                      ,'detection_policy_event'
                      ,'customer_deposit_holding_snapshot'
                      ,'customer_loan_holding_detail_snapshot'
                      ,'loan_repayment_schedule_snapshot'
                      ,'customer_investment_account_snapshot'
                      ,'investment_position_snapshot'
                      ,'deposit_product_snapshot'
                      ,'deposit_product_rate_snapshot'
                      ,'deposit_maturity_option_snapshot'
                      ,'loan_product_snapshot'
                      ,'market_instrument_snapshot'
                      ,'market_quote_snapshot'
                      ,'market_price_point_snapshot'
                      ,'investment_order_snapshot'
                      ,'customer_watchlist_state'
                      ,'customer_watchlist_item'
                      ,'customer_watchlist_event'
                      ,'customer_pension_holding_snapshot'
                      ,'pension_projection_snapshot'
                      ,'customer_trust_holding_snapshot'
                      ,'operational_alert_appeal'
                      ,'operational_case_override_event'
                      ,'operational_feature_flag'
                      ,'feature_flag_change_event'
                      ,'compliance_retention_policy'
                      ,'customer_privacy_request'
                      ,'customer_privacy_request_event'
                      ,'audit_export_request'
                      ,'audit_export_request_event'
                      ,'financial_intent'
                      ,'financial_intent_revision'
                      ,'financial_intent_event'
                      ,'financial_intent_command'
                      ,'staff_access_grant'
                      ,'staff_access_grant_event'
                      ,'staff_access_purpose_scope'
                      ,'staff_access_purpose_role'
                      ,'staff_access_decision_audit_event'
                      ,'customer_mutation_command'
                      ,'recurring_payment'
                      ,'recurring_payment_occurrence'
                      ,'recurring_payment_reminder_event'
                      ,'customer_account_snapshot'
                      ,'customer_account_balance_snapshot'
                      ,'customer_account_restriction_snapshot'
                      ,'customer_account_statement_snapshot'
                      ,'account_display_setting'
                      ,'account_display_setting_event'
                      ,'financial_counterparty_snapshot'
                      ,'account_recurring_counterparty_snapshot'
                      ,'customer_account_group_snapshot'
                      ,'customer_account_group_member_snapshot'
                      ,'financial_transaction_snapshot'
                      ,'transaction_enrichment_snapshot'
                      ,'customer_transaction_preference'
                      ,'customer_transaction_preference_event'
                      ,'customer_liability_snapshot'
                      ,'customer_asset_calendar_snapshot'
                      ,'customer_beneficiary_snapshot'
                      ,'customer_transfer_limit_snapshot'
                      ,'customer_transfer_template'
                      ,'customer_transfer_template_event'
                      ,'customer_card_snapshot'
                      ,'card_transaction_snapshot'
                      ,'card_statement_snapshot'
                  )
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(134);
    }

    @Test
    void healthApiReturnsTheSameTraceIdInTheHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/v1/system/health")
                        .header("X-Trace-Id", "integration-trace-0001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "integration-trace-0001"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SYSTEM_HEALTHY"))
                .andExpect(jsonPath("$.data.syntheticDataOnly").value(true))
                .andExpect(jsonPath("$.data.externalActionsEnabled").value(false))
                .andExpect(jsonPath("$.traceId").value("integration-trace-0001"));
    }

    @Test
    void runtimeRoleCannotRewriteImmutableHistory() {
        if (!runtimeRoleExists()) return;
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','staff_access_grant_event','INSERT')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','staff_access_grant_event','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','staff_access_grant','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','staff_access_grant','status','UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','staff_access_grant','customer_id','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','staff_access_purpose_scope','INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','staff_access_purpose_role','INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_mutation_command','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','customer_mutation_command','result_payload','UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','financial_intent_revision','DELETE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','knowledge_passage','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','knowledge_access_audit_event','INSERT')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','knowledge_access_audit_event','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','auth_session_event','INSERT')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','auth_session_event','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_transfer_template','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','customer_transfer_template','status','UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','customer_transfer_template','template_name','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_transfer_template_event','INSERT')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_transfer_template_event','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','recurring_payment','INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','recurring_payment','reminder_enabled','UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','recurring_payment','expected_amount','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','recurring_payment_occurrence','INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','recurring_payment_reminder_event','INSERT')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','recurring_payment_reminder_event','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_account_snapshot','INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_account_statement_snapshot','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','account_display_setting','alias','UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','account_display_setting','customer_id','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','financial_counterparty_snapshot','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','account_display_setting_event','INSERT')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','account_display_setting_event','DELETE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','financial_transaction_snapshot','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','customer_transaction_preference','note_text','UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_app','customer_transaction_preference','customer_id','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_transaction_preference_event','DELETE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_liability_snapshot','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_asset_calendar_snapshot','DELETE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_beneficiary_snapshot','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_transfer_limit_snapshot','DELETE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','customer_card_snapshot','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','card_transaction_snapshot','DELETE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','card_statement_snapshot','UPDATE')",
                Boolean.class)).isFalse();
    }

    @Test
    void aiIngestorCanOnlyWriteDerivedKnowledgeSchema() {
        assertThat(jdbcTemplate.queryForObject(
                "select has_schema_privilege('alzswell_ai_ingestor','ai_knowledge','USAGE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_ai_ingestor','ai_knowledge.ingestion_run','INSERT')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_ai_ingestor','ai_knowledge.ingestion_run','status','UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_column_privilege('alzswell_ai_ingestor','ai_knowledge.ingestion_run','document_id','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_ai_ingestor','ai_knowledge.chunk','DELETE')",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_ai_ingestor','knowledge_document','INSERT')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_ai_ingestor','knowledge_passage','UPDATE')",
                Boolean.class)).isFalse();
    }

    @Test
    @Transactional
    void futureTablesDoNotAutomaticallyGrantRuntimeUpdateOrDelete() {
        if (!runtimeRoleExists()) return;
        jdbcTemplate.execute("create table v40_default_privilege_probe(id integer primary key)");
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','v40_default_privilege_probe','UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select has_table_privilege('alzswell_app','v40_default_privilege_probe','DELETE')",
                Boolean.class)).isFalse();
    }

    private boolean runtimeRoleExists() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from pg_roles where rolname='alzswell_app')", Boolean.class));
    }

    @Test
    void protectsGrantIdentityAndCompletedIdempotencyResponseAtDatabaseLevel() {
        UUID principalId = UUID.fromString("97000000-0000-0000-0000-000000000001");
        jdbcTemplate.update("""
                insert into auth_principal(principal_id,login_id,customer_id,display_name,password_hash,status,
                    created_at,updated_at)
                select ?,'db-guard-staff',customer_id,'DB 보호 직원',password_hash,'ACTIVE',now(),now()
                  from auth_principal where login_id='synthetic-customer'
                """, principalId);
        jdbcTemplate.update("insert into auth_principal_role(principal_id,role_code) values(?,'DETECTION_ADMIN')",
                principalId);
        UUID grantId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into staff_access_grant(grant_id,staff_principal_id,customer_id,purpose_code,scopes,status,
                    granted_by,granted_at,expires_at,idempotency_key_hash,request_hash,row_version)
                values(?,?,?,'ALERT_MANAGEMENT',array['ALERT_READ'],'ACTIVE',?,now(),now()+interval '1 day',
                    repeat('a',64),repeat('b',64),1)
                """, grantId, principalId, "SYN_CUSTOMER_FIN_MGMT_001", principalId);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update staff_access_grant set expires_at=expires_at+interval '1 day' where grant_id=?", grantId))
                .isInstanceOf(DataAccessException.class)
                .satisfies(exception -> assertThat(((DataAccessException) exception).getMostSpecificCause().getMessage())
                        .contains("identity is immutable"));

        jdbcTemplate.update("""
                insert into customer_mutation_command(command_scope,idempotency_key_hash,request_hash,created_at)
                values('DB_GUARD_TEST',repeat('c',64),repeat('d',64),now())
                """);
        jdbcTemplate.update("""
                update customer_mutation_command set result_payload='{"result":"first"}'::jsonb,completed_at=now()
                 where command_scope='DB_GUARD_TEST'
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update customer_mutation_command set result_payload='{"result":"rewritten"}'::jsonb
                 where command_scope='DB_GUARD_TEST'
                """))
                .isInstanceOf(DataAccessException.class)
                .satisfies(exception -> assertThat(((DataAccessException) exception).getMostSpecificCause().getMessage())
                        .contains("immutable after completion"));
        jdbcTemplate.update("delete from customer_mutation_command where command_scope='DB_GUARD_TEST'");
        jdbcTemplate.update("delete from staff_access_grant where grant_id=?", grantId);
        jdbcTemplate.update("delete from auth_principal_role where principal_id=?", principalId);
        jdbcTemplate.update("delete from auth_principal where principal_id=?", principalId);
    }

    @Test
    void readinessApiChecksDatabaseFlywayFixturesAndPolicyCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SYSTEM_READY"))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.checks.database").value("UP"))
                .andExpect(jsonPath("$.data.checks.flyway").value("UP"))
                .andExpect(jsonPath("$.data.checks.syntheticFixtures").value("UP"))
                .andExpect(jsonPath("$.data.checks.policyCatalog").value("UP"))
                .andExpect(jsonPath("$.data.checks.detectionPolicy").value("UP"))
                .andExpect(jsonPath("$.data.checks.safeGuardrails").value("UP"));
    }

    @Test
    @Transactional
    void readinessRejectsDatabaseWithoutTheRequiredLatestMigration() throws Exception {
        jdbcTemplate.update("delete from flyway_schema_history where version = '58'");

        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYSTEM_NOT_READY"))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks.database").value("UP"))
                .andExpect(jsonPath("$.data.checks.flyway").value("DOWN"));
    }

    @Test
    @Transactional
    void readinessRejectsDatabaseWithAFailedMigration() throws Exception {
        jdbcTemplate.update("""
                insert into flyway_schema_history (
                    installed_rank, version, description, type, script, checksum,
                    installed_by, execution_time, success
                )
                select coalesce(max(installed_rank), 0) + 1, '37', 'simulated failure',
                       'SQL', 'V37__simulated_failure.sql', null, current_user, 0, false
                  from flyway_schema_history
                """);

        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYSTEM_NOT_READY"))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks.database").value("UP"))
                .andExpect(jsonPath("$.data.checks.flyway").value("DOWN"));
    }

    @Test
    @Transactional
    void readinessReturnsServiceUnavailableWhenThePolicyCatalogIsMissing() throws Exception {
        jdbcTemplate.update("delete from customer_protection_enrollment");
        jdbcTemplate.update("delete from protection_action_catalog");

        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYSTEM_NOT_READY"))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks.policyCatalog").value("DOWN"));
    }

    @Test
    @Transactional
    void readinessRejectsDatabaseWithoutExactlyOneActiveDetectionPolicy() throws Exception {
        jdbcTemplate.update("update detection_policy_version set status='RETIRED' where status='ACTIVE'");

        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks.detectionPolicy").value("DOWN"));
    }

    @Test
    void publicConfigExposesAirGappedSyntheticOnlyGuardrails() throws Exception {
        mockMvc.perform(get("/api/v1/system/public-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PUBLIC_CONFIG_RETRIEVED"))
                .andExpect(jsonPath("$.data.networkMode").value("AIR_GAPPED_DEMO"))
                .andExpect(jsonPath("$.data.externalEgressEnabled").value(false))
                .andExpect(jsonPath("$.data.remoteModelEnabled").value(false))
                .andExpect(jsonPath("$.data.syntheticProviderOnly").value(true))
                .andExpect(jsonPath("$.data.supportedScenarioIds[0]").value("FIN_MGMT_AB_001"))
                .andExpect(jsonPath("$.data.demoSessionTtlSeconds").value(7200));
    }

    @Test
    void versionsApiReturnsTheImplementedSchemaAndPolicyVersions() throws Exception {
        mockMvc.perform(get("/api/v1/system/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SYSTEM_VERSIONS_RETRIEVED"))
                .andExpect(jsonPath("$.data.schemaVersion").value("58"))
                .andExpect(jsonPath("$.data.fixtureVersion").value("fin-mgmt-ab-v2.0.0"))
                .andExpect(jsonPath("$.data.algorithmVersion").value("baseline-rules-v2.0.0"))
                .andExpect(jsonPath("$.data.policyVersion").value("context-policy-v1.0.0"));
    }

    @Test
    void corsAllowsTheIdempotencyAndTraceHeadersForDemoCommands() throws Exception {
        mockMvc.perform(options("/api/v1/demo/sessions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "Idempotency-Key,X-Trace-Id,X-Demo-Capability,X-Demo-Run-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsString("Idempotency-Key")
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsString("X-Demo-Capability")
                ));
    }

    @Test
    void corsKeepsCustomerOriginsOutOfStaffOnlyApis() throws Exception {
        mockMvc.perform(options("/api/v1/admin/rules")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options("/api/v1/customers/SYN_CUSTOMER_FIN_MGMT_001/staff-access-grants")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options("/api/v1/admin/rules")
                        .header("Origin", "http://localhost:4173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4173"));
    }

    @Test
    void corsAllowsCustomerOriginsToReadCustomerSignals() throws Exception {
        mockMvc.perform(options("/api/v1/signals/{signalId}", UUID.randomUUID())
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void openApiPublishesTheEnabledTypedApiContractAsReadOnlyDocumentation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andReturn();

        JsonNode specification = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(specification.path("paths").size()).isEqualTo(198);
        long operationCount = StreamSupport.stream(specification.path("paths").spliterator(), false)
                .mapToLong(path -> List.of("get", "post", "put", "patch", "delete").stream()
                        .filter(path::has)
                        .count())
                .sum();
        assertThat(operationCount).isEqualTo(213);

        assertThat(specification.path("components").path("securitySchemes").has("BearerAuth")).isTrue();
        List<JsonNode> operations = StreamSupport.stream(specification.path("paths").spliterator(), false)
                .flatMap(path -> List.of("get", "post", "put", "patch", "delete").stream()
                        .filter(path::has)
                        .map(path::path))
                .toList();
        assertThat(operations).allSatisfy(operation -> {
            assertThat(operation.path("summary").asText()).isNotBlank();
            assertThat(operation.path("description").asText()).isNotBlank();
            assertThat(operation.path("x-alzs-authority-mode").asText()).isNotBlank();
            assertThat(operation.path("x-alzs-required-authorities").isArray()).isTrue();
            assertThat(operation.path("x-alzs-data-classification").asText()).isEqualTo("SYNTHETIC_ONLY");
            assertThat(operation.path("x-alzs-runtime-boundary").asText()).isNotBlank();
            assertThat(operation.path("x-alzs-external-action").asText()).isEqualTo("NEVER");
            assertThat(operation.path("responses").path("400").path("content")
                    .path("application/json").path("example").path("traceId").asText()).isNotBlank();
        });

        JsonNode inboxRead = specification.path("paths")
                .path("/api/v1/customers/{customerId}/inbox/{messageId}/read").path("post");
        assertThat(inboxRead.path("security").toString()).contains("BearerAuth");
        assertThat(inboxRead.path("x-alzs-required-authorities").toString()).contains("INBOX_WRITE");
        assertThat(inboxRead.path("responses").has("401")).isTrue();
        assertThat(inboxRead.path("responses").has("403")).isTrue();
        assertThat(inboxRead.path("responses").has("409")).isTrue();

        JsonNode recurringWrite = specification.path("paths")
                .path("/api/v1/recurring-payments/{recurringPaymentId}/reminder-settings").path("put");
        assertThat(recurringWrite.path("x-alzs-required-authorities").toString())
                .contains("RECURRING_PAYMENT_WRITE");

        JsonNode accountRead = specification.path("paths")
                .path("/api/v1/accounts/{accountId}/balance").path("get");
        assertThat(accountRead.path("x-alzs-required-authorities").toString()).contains("ACCOUNT_READ");

        JsonNode accountWrite = specification.path("paths")
                .path("/api/v1/accounts/{accountId}/display-settings").path("patch");
        assertThat(accountWrite.path("x-alzs-required-authorities").toString()).contains("ACCOUNT_WRITE");

        JsonNode transactionWrite = specification.path("paths")
                .path("/api/v1/transactions/{transactionId}/note").path("put");
        assertThat(transactionWrite.path("x-alzs-required-authorities").toString()).contains("TRANSACTION_WRITE");

        JsonNode overviewRead = specification.path("paths")
                .path("/api/v1/customers/{customerId}/financial-summary").path("get");
        assertThat(overviewRead.path("x-alzs-required-authorities").toString())
                .contains("FINANCIAL_OVERVIEW_READ");

        JsonNode transferTemplateCreate = specification.path("paths")
                .path("/api/v1/customers/{customerId}/transfer-templates").path("post");
        assertThat(transferTemplateCreate.path("x-alzs-required-authorities").toString())
                .contains("TRANSFER_TEMPLATE_WRITE");
        assertThat(transferTemplateCreate.path("x-alzs-runtime-boundary").asText())
                .isEqualTo("INTERNAL_OWNED");
        assertThat(StreamSupport.stream(transferTemplateCreate.path("parameters").spliterator(), false)
                .anyMatch(parameter -> parameter.path("name").asText().equals("Idempotency-Key")
                        && parameter.path("required").asBoolean())).isTrue();

        JsonNode alertParameters = specification.path("paths")
                .path("/api/v1/demo/sessions/{sessionId}/customers/{customerId}/alerts")
                .path("get")
                .path("parameters");
        List<String> parameterNames = StreamSupport.stream(alertParameters.spliterator(), false)
                .map(parameter -> parameter.path("name").asText())
                .toList();
        assertThat(parameterNames).contains("X-Demo-Capability", "X-Demo-Run-Id");

        JsonNode createHeaders = specification.path("paths")
                .path("/api/v1/demo/sessions")
                .path("post")
                .path("responses")
                .path("201")
                .path("headers");
        assertThat(createHeaders.has("X-Demo-Customer-Capability")).isTrue();
        assertThat(createHeaders.has("X-Demo-Staff-Capability")).isFalse();

        JsonNode staffIssuance = specification.path("paths")
                .path("/api/v1/demo/staff/sessions/{sessionId}/capability")
                .path("post");
        assertThat(staffIssuance.path("security").toString()).contains("DemoStaffBootstrap");
        assertThat(staffIssuance.path("responses").path("200").path("headers")
                .has("X-Demo-Staff-Capability")).isTrue();

        JsonNode followUpPatchParameters = specification.path("paths")
                .path("/api/v1/demo/sessions/{sessionId}/staff/follow-ups/{followUpId}")
                .path("patch")
                .path("parameters");
        List<String> followUpPatchParameterNames = StreamSupport.stream(
                        followUpPatchParameters.spliterator(), false)
                .map(parameter -> parameter.path("name").asText())
                .toList();
        assertThat(followUpPatchParameterNames).contains("Idempotency-Key");

        assertThat(specification.path("paths")
                .path("/api/v1/customers/{customerId}/staff-access-grants").path("post")
                .path("x-alzs-required-authorities").toString()).contains("STAFF_ACCESS_GRANT_WRITE");
        assertThat(specification.path("paths").path("/api/v1/admin/rules").path("post")
                .path("x-alzs-required-authorities").toString()).contains("DETECTION_POLICY_WRITE");
        assertThat(specification.path("paths")
                .path("/api/v1/staff/customers/{customerId}/financial-intent-summary").path("get")
                .path("x-alzs-required-authorities").toString()).contains("FINANCIAL_INTENT_SHARED_READ");

        assertThat(specification.path("paths")
                .path("/api/v1/customers/{customerId}/baseline-calculations").path("post")
                .path("x-alzs-required-authorities").toString()).contains("DETECTION_CALCULATE");
        assertThat(specification.path("paths")
                .path("/api/v1/customers/{customerId}/signals").path("get")
                .path("x-alzs-required-authorities").toString()).contains("DETECTION_READ");
        assertThat(specification.path("paths")
                .path("/api/v1/customers/{customerId}/detection-runs").path("post")
                .path("x-alzs-required-authorities").toString()).contains("DETECTION_RUN_CREATE");

        JsonNode demoRunHeader = StreamSupport.stream(alertParameters.spliterator(), false)
                .filter(parameter -> "X-Demo-Run-Id".equals(parameter.path("name").asText()))
                .findFirst().orElseThrow();
        assertThat(demoRunHeader.path("required").asBoolean()).isTrue();

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void decisionAuditIsHashChainedAppendOnlyAndNotCascadeDeletedWithSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/demo/sessions"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createBody = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        UUID sessionId = UUID.fromString(createBody.at("/data/sessionId").asText());
        String customerCapability = created.getResponse()
                .getHeader(DemoCapabilityService.CUSTOMER_RESPONSE_HEADER);

        mockMvc.perform(post(
                        "/api/v1/demo/sessions/{sessionId}/scenarios/FIN_MGMT_AB_001/ingest",
                        sessionId
                )
                        .header(DemoCapabilityService.REQUEST_HEADER, customerCapability)
                        .header("Idempotency-Key", "audit-chain-ingest-0001"))
                .andExpect(status().isCreated());

        List<AuditHash> hashes = jdbcTemplate.query(
                """
                select audit_id, previous_event_hash, event_hash
                  from decision_audit
                 where demo_session_id = ?
                 order by audit_sequence
                """,
                (resultSet, rowNumber) -> new AuditHash(
                        resultSet.getObject("audit_id", UUID.class),
                        resultSet.getString("previous_event_hash"),
                        resultSet.getString("event_hash")
                ),
                sessionId
        );
        assertThat(hashes).hasSize(2);
        assertThat(hashes.getFirst().previousHash()).isNull();
        assertThat(hashes.getFirst().eventHash()).startsWith("sha256:");
        assertThat(hashes.get(1).previousHash()).isEqualTo(hashes.getFirst().eventHash());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update decision_audit set event_type = 'TAMPERED' where audit_id = ?",
                hashes.getFirst().auditId()
        )).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        jdbcTemplate.update("delete from demo_session where session_id = ?", sessionId);
        Integer preserved = jdbcTemplate.queryForObject(
                "select count(*) from decision_audit where demo_session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(preserved).isEqualTo(2);
    }

    private record AuditHash(UUID auditId, String previousHash, String eventHash) {
    }
}
