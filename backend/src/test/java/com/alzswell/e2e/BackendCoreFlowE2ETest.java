package com.alzswell.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BackendCoreFlowE2ETest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final UUID STAFF_PRINCIPAL_ID = UUID.fromString("92000000-0000-0000-0000-000000000099");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareStaffAccess() {
        jdbcTemplate.execute("truncate staff_access_decision_audit_event, staff_access_grant_event, staff_access_grant");
        jdbcTemplate.update("""
                insert into auth_principal(principal_id,login_id,customer_id,display_name,password_hash,status,created_at,updated_at)
                select ?,'e2e-protection-staff',customer_id,'E2E 보호업무 담당자',password_hash,'ACTIVE',now(),now()
                  from auth_principal where login_id='synthetic-customer'
                on conflict(principal_id) do update set status='ACTIVE',updated_at=now()
                """, STAFF_PRINCIPAL_ID);
        jdbcTemplate.update("insert into auth_principal_role(principal_id,role_code) values(?,'PROTECTION_STAFF') on conflict do nothing",
                STAFF_PRINCIPAL_ID);
        jdbcTemplate.update("""
                insert into staff_access_grant(grant_id,staff_principal_id,customer_id,purpose_code,scopes,status,
                    granted_by,granted_at,expires_at,idempotency_key_hash,request_hash,row_version)
                values(?,?,?,'PROTECTION_CASE_MANAGEMENT',array['CASE_READ','CASE_ASSIGN','CASE_REVIEW','CASE_GUIDANCE'],
                    'ACTIVE',?,now(),now()+interval '1 day',repeat('c',64),repeat('d',64),1)
                """, UUID.randomUUID(), STAFF_PRINCIPAL_ID, CUSTOMER_ID, STAFF_PRINCIPAL_ID);
    }

    @Test
    void connectsSyntheticDataToReviewedProtectionCaseWithoutExternalExecution() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{customerId}/baseline-calculations", CUSTOMER_ID)
                        .with(customer())
                        .header("Idempotency-Key", "core-flow-baseline-001"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reusedCurrentSnapshot").value(true))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false));

        UUID datasetId = createAndIngestDataset();
        UUID detectionRunId = runDetection(datasetId);
        JsonNode promotion = promote(detectionRunId);
        UUID alertId = UUID.fromString(promotion.path("alertIds").get(0).asText());

        mockMvc.perform(get("/api/v1/alerts/{alertId}", alertId).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alert.state").value("AWAITING_CONTEXT"))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false));

        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", alertId)
                        .with(customer())
                        .header("Idempotency-Key", "core-flow-context-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"responseCode\":\"NOT_SURE\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentState").value("BANK_REVIEW"))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false))
                .andExpect(jsonPath("$.data.externalNotificationSent").value(false));

        UUID caseId = jdbcTemplate.queryForObject(
                "select case_id from operational_protection_case where alert_id=?", UUID.class, alertId);
        assertThat(caseId).isNotNull();

        mockMvc.perform(put("/api/v1/staff/cases/{caseId}/assignment", caseId)
                        .with(staff())
                        .header("Idempotency-Key", "core-flow-assignment-001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"assignedTeam":"SAFE_TEAM_E2E","assignedTo":"__STAFF_ID__",
                                 "expectedVersion":1}
                                """.replace("__STAFF_ID__", STAFF_PRINCIPAL_ID.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/reviews", caseId)
                        .with(staff())
                        .header("Idempotency-Key", "core-flow-review-001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"actionCode":"START_REVIEW","note":"합성 E2E 사건 검토 시작",
                                 "expectedVersion":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.version").value(3));

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/guidance-plans", caseId)
                        .with(staff())
                        .header("Idempotency-Key", "core-flow-guidance-001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"selectedActionCodes":["FDS_REVIEW","BRANCH_CONSULTATION"],
                                 "expectedVersion":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caseVersion").value(4))
                .andExpect(jsonPath("$.data.delivered").value(false))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false));

        verifyClosedAuditLoop(detectionRunId, alertId, caseId);
    }

    @Test
    void retriesConflictsAndFailedTransitionsRemainAtomicAndTraceable() throws Exception {
        String traceId = "core-recovery-trace-001";
        UUID datasetId = createAndIngestDataset();
        String runBody = "{\"datasetId\":\"" + datasetId + "\"}";

        MvcResult firstRun = mockMvc.perform(post(
                        "/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .with(detectionAdmin())
                        .header("Idempotency-Key", "core-recovery-detection-001")
                        .header("X-Trace-Id", traceId)
                        .contentType(APPLICATION_JSON)
                        .content(runBody))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(false))
                .andReturn();
        UUID runId = UUID.fromString(objectMapper.readTree(firstRun.getResponse().getContentAsByteArray())
                .at("/data/detectionRunId").asText());

        mockMvc.perform(post("/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .with(detectionAdmin())
                        .header("Idempotency-Key", "core-recovery-detection-001")
                        .header("X-Trace-Id", traceId)
                        .contentType(APPLICATION_JSON)
                        .content(runBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.detectionRunId").value(runId.toString()))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));
        mockMvc.perform(post("/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .with(detectionAdmin())
                        .header("Idempotency-Key", "core-recovery-detection-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"datasetId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DETECTION_IDEMPOTENCY_CONFLICT"));

        MvcResult firstPromotion = mockMvc.perform(post(
                        "/api/v1/detection-runs/{detectionRunId}/promotion", runId)
                        .with(detectionAdmin())
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(false))
                .andReturn();
        JsonNode promotion = objectMapper.readTree(firstPromotion.getResponse().getContentAsByteArray())
                .path("data");
        UUID promotionId = UUID.fromString(promotion.path("promotionId").asText());
        UUID successfulAlertId = UUID.fromString(promotion.path("alertIds").get(0).asText());
        UUID failedAlertId = UUID.fromString(promotion.path("alertIds").get(1).asText());

        mockMvc.perform(post("/api/v1/detection-runs/{detectionRunId}/promotion", runId)
                        .with(detectionAdmin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.promotionId").value(promotionId.toString()))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));

        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", failedAlertId)
                        .with(customer())
                        .header("Idempotency-Key", "core-recovery-invalid-version-001")
                        .header("X-Trace-Id", traceId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"responseCode\":\"NOT_SURE\",\"expectedVersion\":99}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.code").value("ALERT_STATE_CONFLICT"));
        assertFailedTransitionLeftNoPartialState(failedAlertId);

        mockMvc.perform(get("/api/v1/alerts/{alertId}", failedAlertId)
                        .with(user("ANOTHER_CUSTOMER")
                                .authorities(new SimpleGrantedAuthority("ALERT_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));

        String responseBody = "{\"responseCode\":\"NOT_SURE\",\"expectedVersion\":1}";
        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", successfulAlertId)
                        .with(customer())
                        .header("Idempotency-Key", "core-recovery-context-001")
                        .header("X-Trace-Id", traceId)
                        .contentType(APPLICATION_JSON)
                        .content(responseBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentState").value("BANK_REVIEW"))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(false));
        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", successfulAlertId)
                        .with(customer())
                        .header("Idempotency-Key", "core-recovery-context-001")
                        .contentType(APPLICATION_JSON)
                        .content(responseBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));
        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", successfulAlertId)
                        .with(customer())
                        .header("Idempotency-Key", "core-recovery-context-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"responseCode\":\"EXPECTED_CHANGE\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_IDEMPOTENCY_CONFLICT"));
        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", successfulAlertId)
                        .with(customer())
                        .header("Idempotency-Key", "core-recovery-stale-version-001")
                        .contentType(APPLICATION_JSON)
                        .content(responseBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_STATE_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from synthetic_detection_run where detection_run_id=?",
                Integer.class, runId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from detection_run_promotion where detection_run_id=?",
                Integer.class, runId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from customer_detection_signal where source_detection_run_id=?
                """, Integer.class, runId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from operational_alert a
                  join customer_detection_signal s on s.signal_id=a.signal_id
                 where s.source_detection_run_id=?
                """, Integer.class, runId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_alert_context_event where alert_id=?",
                Integer.class, successfulAlertId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_protection_case where alert_id=?",
                Integer.class, successfulAlertId)).isEqualTo(1);
    }

    private UUID createAndIngestDataset() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/synthetic-datasets")
                        .with(detectionAdmin())
                        .contentType(APPLICATION_JSON)
                        .content(datasetJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.syntheticData").value(true))
                .andReturn();
        UUID datasetId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/dataset/datasetId").asText());

        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/validate", datasetId)
                        .with(detectionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALIDATED"));
        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/ingest", datasetId)
                        .with(detectionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INGESTED"))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false));
        return datasetId;
    }

    private UUID runDetection(UUID datasetId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .with(detectionAdmin())
                        .header("Idempotency-Key", "core-flow-detection-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"datasetId\":\"" + datasetId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.signalCount").value(3))
                .andExpect(jsonPath("$.data.advisoryAiUsed").value(false))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/detectionRunId").asText());
    }

    private JsonNode promote(UUID detectionRunId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/detection-runs/{detectionRunId}/promotion", detectionRunId)
                        .with(detectionAdmin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.promotedSignalCount").value(3))
                .andExpect(jsonPath("$.data.promotedAlertCount").value(3))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false))
                .andExpect(jsonPath("$.data.externalNotificationSent").value(false))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private void verifyClosedAuditLoop(UUID runId, UUID alertId, UUID caseId) throws Exception {
        mockMvc.perform(get("/api/v1/alerts/{alertId}/audit", alertId).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].eventType").value("ALERT_CREATED"))
                .andExpect(jsonPath("$.data.items[1].eventType").value("CONTEXT_RESPONDED"));
        mockMvc.perform(get("/api/v1/staff/cases/{caseId}/timeline", caseId).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(6));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from synthetic_detection_run where detection_run_id=?",
                Integer.class, runId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from detection_run_promotion where detection_run_id=?",
                Integer.class, runId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_protection_case where alert_id=?",
                Integer.class, alertId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from operational_guidance_plan
                 where case_id=? and delivered=false and external_execution_created=false
                """, Integer.class, caseId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from operational_alert_audit_event
                 where alert_id=? and event_type in ('ALERT_CREATED','CONTEXT_RESPONDED')
                """, Integer.class, alertId)).isEqualTo(2);
    }

    private void assertFailedTransitionLeftNoPartialState(UUID alertId) {
        assertThat(jdbcTemplate.queryForObject(
                "select state from operational_alert where alert_id=?", String.class, alertId))
                .isEqualTo("AWAITING_CONTEXT");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_alert_context_event where alert_id=?",
                Integer.class, alertId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_protection_case where alert_id=?",
                Integer.class, alertId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_alert_audit_event where alert_id=?",
                Integer.class, alertId)).isEqualTo(1);
    }

    private RequestPostProcessor customer() {
        return user(CUSTOMER_ID).authorities(
                new SimpleGrantedAuthority("DETECTION_READ"),
                new SimpleGrantedAuthority("DETECTION_CALCULATE"),
                new SimpleGrantedAuthority("ALERT_READ"),
                new SimpleGrantedAuthority("ALERT_RESPOND"));
    }

    private RequestPostProcessor detectionAdmin() {
        return user("e2e-detection-admin").authorities(
                new SimpleGrantedAuthority("SYNTHETIC_DATASET_ADMIN"),
                new SimpleGrantedAuthority("DETECTION_RUN_CREATE"),
                new SimpleGrantedAuthority("DETECTION_RUN_READ"),
                new SimpleGrantedAuthority("DETECTION_PROMOTE"),
                new SimpleGrantedAuthority("DETECTION_PROMOTION_READ"));
    }

    private RequestPostProcessor staff() {
        var token = new UsernamePasswordAuthenticationToken(
                new AuthenticatedPrincipal(STAFF_PRINCIPAL_ID, CUSTOMER_ID), null, java.util.List.of(
                new SimpleGrantedAuthority("STAFF_CASE_READ"),
                new SimpleGrantedAuthority("STAFF_CASE_ASSIGN"),
                new SimpleGrantedAuthority("STAFF_CASE_REVIEW"),
                new SimpleGrantedAuthority("STAFF_GUIDANCE_APPROVE")));
        token.setDetails(new AuthenticatedSession(UUID.fromString("92000000-0000-0000-0000-000000000098")));
        return authentication(token);
    }

    private String datasetJson() {
        return """
                {
                  "datasetName":"백엔드 핵심 흐름 E2E",
                  "customerId":"SYN_CUSTOMER_FIN_MGMT_001",
                  "observations":[
                    {"featureCode":"MISSED_RECURRING_PAYMENT","baselineValue":0,
                     "currentValue":1,"unit":"COUNT",
                     "evidence":[{"evidenceType":"TRANSACTION","sourceReference":"E2E-MISSED-001",
                     "occurredAt":"2026-08-10T00:00:00Z","description":"예정 거래 누락"}]},
                    {"featureCode":"DUPLICATE_TRANSFER","baselineValue":0,
                     "currentValue":2,"unit":"COUNT",
                     "evidence":[{"evidenceType":"TRANSACTION","sourceReference":"E2E-DUP-001",
                     "occurredAt":"2026-08-12T01:00:00Z","amount":500000,"currency":"KRW",
                     "description":"첫 송금"},{"evidenceType":"TRANSACTION",
                     "sourceReference":"E2E-DUP-002","occurredAt":"2026-08-12T01:02:00Z",
                     "amount":500000,"currency":"KRW","description":"두 번째 송금"}]},
                    {"featureCode":"REPEATED_CONFIRMATION","baselineValue":1,
                     "currentValue":5,"unit":"COUNT",
                     "evidence":[{"evidenceType":"INTERACTION","sourceReference":"E2E-CONFIRM-001",
                     "occurredAt":"2026-08-13T03:00:00Z","description":"반복 확인"}]}
                  ]
                }
                """;
    }
}
