package com.alzswell.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class P0WorkflowIntegrationTest {

    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final String ALERT = "ALERT_FIN_MGMT_001";
    private static final String CASE = "CASE_FIN_MGMT_001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private DemoTestClient client;

    @BeforeEach
    void setUp() {
        client = new DemoTestClient(mockMvc, objectMapper);
    }

    @Test
    void closesBranchAOnlyWithAllFourVerifiedStructuralEvidenceItems() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p0-a-ingest-0001");

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/alerts",
                        session.sessionId(), CUSTOMER), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ALERT_LIST_RETRIEVED"))
                .andExpect(jsonPath("$.data.items[0].state").value("AWAITING_CONTEXT"))
                .andExpect(jsonPath("$.data.items[0].incidentVersion").value(1))
                .andExpect(jsonPath("$.data.items[0].reasonCodes.length()").value(3));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/alerts/{a}", session.sessionId(), ALERT), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.t0AlertEvidence.immutable").value(true))
                .andExpect(jsonPath("$.data.t0AlertEvidence.signals[0].currentValue").value(3))
                .andExpect(jsonPath("$.data.t0AlertEvidence.signals[1].currentValue").value(2))
                .andExpect(jsonPath("$.data.t0AlertEvidence.signals[2].currentValue").value(7))
                .andExpect(jsonPath("$.data.t1ContextEvidence").isEmpty());

        String request = """
                {
                  "responseCode":"KNOWN_AND_INTENTIONAL",
                  "demoBranchCode":"FIN_MGMT_A_NORMAL_CONTEXT"
                }
                """;
        JsonNode applied = read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/context", session.sessionId(), ALERT)
                .header("Idempotency-Key", "p0-context-a-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(applied.at("/code").asText()).isEqualTo("ALERT_CONTEXT_APPLIED");
        assertThat(applied.at("/data/currentState").asText()).isEqualTo("CLOSED_NORMAL");
        assertThat(applied.at("/data/t1ContextEvidence/contextEvidenceRefs").size()).isEqualTo(4);
        assertThat(applied.at("/data/t1ContextEvidence/structuralEvidenceMatched").asBoolean()).isTrue();
        assertThat(applied.at("/data/trustedContactGate/gateEvaluated").asBoolean()).isFalse();

        JsonNode replayed = read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/context", session.sessionId(), ALERT)
                .header("Idempotency-Key", "p0-context-a-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(replayed.at("/data/command/idempotencyReplayed").asBoolean()).isTrue();
        Integer caseCount = jdbcTemplate.queryForObject(
                "select count(*) from protection_case where demo_session_id = ? and demo_run_id = ?",
                Integer.class, session.sessionId(), session.demoRunId()
        );
        assertThat(caseCount).isZero();
    }

    @Test
    void defersCustomerConfirmationWithVersionedAuditThenAllowsFinalContext() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p0-defer-ingest-0001");
        String deferredUntil = OffsetDateTime.now().plusDays(1).withNano(0).toString();
        String expectedDeferredUtc = OffsetDateTime.parse(deferredUntil).toInstant().toString();
        String request = "{\"expectedVersion\":1,\"deferredUntil\":\"%s\"}"
                .formatted(deferredUntil);

        JsonNode deferred = read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/defer", session.sessionId(), ALERT)
                .header("Idempotency-Key", "p0-defer-confirmation-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(deferred.at("/code").asText()).isEqualTo("ALERT_CONFIRMATION_DEFERRED");
        assertThat(deferred.at("/data/currentState").asText()).isEqualTo("DEFERRED");
        assertThat(deferred.at("/data/incidentVersion").asLong()).isEqualTo(2);
        assertThat(deferred.at("/data/deferredUntil").asText()).isEqualTo(expectedDeferredUtc);
        assertThat(deferred.at("/data/nextAction/type").asText()).isEqualTo("RECHECK_LATER");

        JsonNode replay = read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/defer", session.sessionId(), ALERT)
                .header("Idempotency-Key", "p0-defer-confirmation-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(replay.at("/data/command/idempotencyReplayed").asBoolean()).isTrue();

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/alerts/{a}", session.sessionId(), ALERT), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("DEFERRED"))
                .andExpect(jsonPath("$.data.incidentVersion").value(2))
                .andExpect(jsonPath("$.data.deferredUntil").value(expectedDeferredUtc));
        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/audit", session.sessionId(), ALERT), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.eventType == 'CUSTOMER_CONFIRMATION_DEFERRED')]").exists());

        JsonNode completed = read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/context", session.sessionId(), ALERT)
                .header("Idempotency-Key", "p0-deferred-context-0001")
                .contentType(APPLICATION_JSON).content("""
                        {"responseCode":"KNOWN_AND_INTENTIONAL",\
                         "demoBranchCode":"FIN_MGMT_A_NORMAL_CONTEXT"}
                        """), session));
        assertThat(completed.at("/data/previousState").asText()).isEqualTo("DEFERRED");
        assertThat(completed.at("/data/currentState").asText()).isEqualTo("CLOSED_NORMAL");
        assertThat(completed.at("/data/incidentVersion").asLong()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from alert_deferral_event
                 where demo_session_id=? and demo_run_id=? and alert_id=?
                """, Integer.class, session.sessionId(), session.demoRunId(), ALERT)).isOne();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update alert_deferral_event set deferred_until=deferred_until+interval '1 hour'
                 where demo_session_id=? and demo_run_id=? and alert_id=?
                """, session.sessionId(), session.demoRunId(), ALERT))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("append-only");

        mockMvc.perform(client.customer(delete(
                        "/api/v1/demo/sessions/{s}", session.sessionId()), session, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_DISCARDED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from alert_deferral_event where demo_session_id=?",
                Integer.class, session.sessionId())).isZero();
    }

    @Test
    void escalatesBranchBThenStopsGuidanceApprovalBeforeDeliveryOrExternalExecution() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p0-b-ingest-0001");
        JsonNode escalated = applyBranchB(session, "p0-context-b-0001");
        assertThat(escalated.at("/code").asText()).isEqualTo("ALERT_ESCALATED_TO_BANK_REVIEW");
        assertThat(escalated.at("/data/currentState").asText()).isEqualTo("PENDING_BANK_REVIEW");
        assertThat(escalated.at("/data/t1ContextEvidence/contextEvidenceIds").size()).isZero();
        assertThat(escalated.at("/data/trustedContactGate/resultCode").asText())
                .isEqualTo("BLOCKED_BY_CONSENT");
        assertThat(escalated.at("/data/trustedContactGate/dispatchAttempted").asBoolean()).isFalse();

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/staff/cases", session.sessionId())
                .param("state", "PENDING_BANK_REVIEW").param("reviewPriority", "HIGH"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].caseId").value(CASE))
                .andExpect(jsonPath("$.data.items[0].caseVersion").value(1))
                .andExpect(jsonPath("$.data.items[0].trustedContactGate.resultCode")
                        .value("BLOCKED_BY_CONSENT"));

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}", session.sessionId(), CASE), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consultationDraft.generatedBy").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.consultationDraft.fallbackUsed").value(true))
                .andExpect(jsonPath("$.data.capabilities.externalMessage").value(false))
                .andExpect(jsonPath("$.data.capabilities.transactionHold").value(false));

        String reviewRequest = """
                {
                  "action":"START_REVIEW",
                  "caseVersion":1,
                  "note":"고객 응답과 합성 근거 거래를 확인합니다.",
                  "followUpAt":null
                }
                """;
        JsonNode reviewed = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "p0-case-review-0001")
                .contentType(APPLICATION_JSON).content(reviewRequest), session));
        assertThat(reviewed.at("/data/currentState").asText()).isEqualTo("IN_BANK_REVIEW");
        assertThat(reviewed.at("/data/caseVersion").asLong()).isEqualTo(2);
        assertThat(reviewed.at("/data/externalExecutionCreated").asBoolean()).isFalse();

        JsonNode reviewReplay = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "p0-case-review-0001")
                .contentType(APPLICATION_JSON).content(reviewRequest), session));
        assertThat(reviewReplay.at("/data/command/idempotencyReplayed").asBoolean()).isTrue();

        String guidanceRequest = """
                {
                  "caseVersion":2,
                  "decision":"APPROVE_GUIDANCE_PLAN",
                  "selectedActionCodes":["SAFE_BLOCK_INFO","BANK_CONSULTATION"],
                  "staffNote":"공식 적용조건을 확인한 뒤 고객에게 안내할 계획입니다."
                }
                """;
        JsonNode guidance = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/guidance-plan", session.sessionId(), CASE)
                .header("Idempotency-Key", "p0-guidance-plan-0001")
                .contentType(APPLICATION_JSON).content(guidanceRequest), session));
        assertThat(guidance.at("/code").asText()).isEqualTo("GUIDANCE_PLAN_APPROVED");
        assertThat(guidance.at("/data/currentState").asText()).isEqualTo("GUIDANCE_PLAN_APPROVED");
        assertThat(guidance.at("/data/guidanceDelivered").asBoolean()).isFalse();
        assertThat(guidance.at("/data/externalExecutionCreated").asBoolean()).isFalse();
        assertThat(guidance.at("/data/deliveredAt").isNull()).isTrue();

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/audit",
                        session.sessionId(), ALERT), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(6));
    }

    @Test
    void closesFalsePositiveOnlyAfterStaffReviewWithDocumentedReason() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p0-false-positive-ingest-0001");
        applyBranchB(session, "p0-false-positive-context-0001");

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}", session.sessionId(), CASE), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowedActions[2].action").value("CLOSE_FALSE_POSITIVE"))
                .andExpect(jsonPath("$.data.allowedActions[2].enabled").value(false))
                .andExpect(jsonPath("$.data.allowedActions[2].disabledReasonCode")
                        .value("REVIEW_NOT_STARTED"));

        read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "p0-false-positive-start-0001")
                .contentType(APPLICATION_JSON).content("""
                        {
                          "action":"START_REVIEW",
                          "caseVersion":1,
                          "note":"고객 응답과 합성 근거를 확인합니다.",
                          "followUpAt":null
                        }
                        """), session));

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}", session.sessionId(), CASE), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowedActions[2].action").value("CLOSE_FALSE_POSITIVE"))
                .andExpect(jsonPath("$.data.allowedActions[2].enabled").value(true));

        JsonNode closed = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "p0-false-positive-close-0001")
                .contentType(APPLICATION_JSON).content("""
                        {
                          "action":"CLOSE_FALSE_POSITIVE",
                          "caseVersion":2,
                          "note":"거래 처리 지연과 실제 이체 내역을 대조해 정상 활동임을 확인했습니다.",
                          "followUpAt":null
                        }
                        """), session));
        assertThat(closed.at("/data/currentState").asText()).isEqualTo("CLOSED_FALSE_POSITIVE");
        assertThat(closed.at("/data/caseVersion").asLong()).isEqualTo(3);
        assertThat(closed.at("/data/externalExecutionCreated").asBoolean()).isFalse();
    }

    @Test
    void preventsUnsafeDowngradeAndRejectsCaseVersionAndIdempotencyConflicts() throws Exception {
        DemoTestClient.Session unsafe = client.ingest(client.create(), "p0-unsafe-ingest-0001");
        jdbcTemplate.update(
                """
                update synthetic_signal set observed_count = 2
                 where demo_session_id = ? and demo_run_id = ? and alert_id = ?
                   and reason_code = 'MISSED_RECURRING'
                """,
                unsafe.sessionId(), unsafe.demoRunId(), ALERT
        );
        String normalRequest = """
                {
                  "responseCode":"KNOWN_AND_INTENTIONAL",
                  "demoBranchCode":"FIN_MGMT_A_NORMAL_CONTEXT"
                }
                """;
        JsonNode safeEscalation = read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/context", unsafe.sessionId(), ALERT)
                .header("Idempotency-Key", "p0-unsafe-context-0001")
                .contentType(APPLICATION_JSON).content(normalRequest), unsafe));
        assertThat(safeEscalation.at("/data/currentState").asText()).isEqualTo("PENDING_BANK_REVIEW");
        assertThat(safeEscalation.at("/data/t1ContextEvidence/structuralEvidenceMatched").asBoolean()).isFalse();

        DemoTestClient.Session conflicts = client.ingest(client.create(), "p0-conflict-ingest-0001");
        applyBranchB(conflicts, "p0-idempotency-conflict-0001");
        String differentBody = """
                {
                  "responseCode":"KNOWN_AND_INTENTIONAL",
                  "demoBranchCode":"FIN_MGMT_A_NORMAL_CONTEXT"
                }
                """;
        mockMvc.perform(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/context", conflicts.sessionId(), ALERT)
                .header("Idempotency-Key", "p0-idempotency-conflict-0001")
                .contentType(APPLICATION_JSON).content(differentBody), conflicts))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        String staleCaseVersion = """
                {
                  "action":"START_REVIEW",
                  "caseVersion":2,
                  "note":"합성 근거를 확인합니다.",
                  "followUpAt":null
                }
                """;
        mockMvc.perform(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", conflicts.sessionId(), CASE)
                .header("Idempotency-Key", "p0-case-version-0001")
                .contentType(APPLICATION_JSON).content(staleCaseVersion), conflicts))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_VERSION_CONFLICT"));
    }

    @Test
    void keepsWorkflowRowsIsolatedByDemoRunAndRejectsThePreviousRunHeader() throws Exception {
        DemoTestClient.Session previous = client.ingest(client.create(), "p0-run-ingest-0001");
        JsonNode reset = client.read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/reset", previous.sessionId())
                .header("Idempotency-Key", "p0-run-reset-0001"), previous));
        DemoTestClient.Session current = previous.withRun(
                UUID.fromString(reset.at("/data/demoRunId").asText())
        );

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/alerts",
                        previous.sessionId(), CUSTOMER), previous))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEMO_RUN_STALE"));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/alerts",
                        current.sessionId(), CUSTOMER), current))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demoRunId").value(current.demoRunId().toString()));

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/staff/cases",
                        current.sessionId()), current))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CASE_QUEUE_RETRIEVED"));

        Integer isolatedRuns = jdbcTemplate.queryForObject(
                "select count(distinct demo_run_id) from alert_incident where demo_session_id = ?",
                Integer.class, current.sessionId()
        );
        assertThat(isolatedRuns).isEqualTo(2);
    }

    @Test
    void rejectsInvalidCaseQueueAndAuditPaginationInputs() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p0-invalid-query-ingest-0001");
        applyBranchB(session, "p0-invalid-query-context-0001");

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/staff/cases", session.sessionId())
                .param("state", "UNKNOWN"), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/staff/cases", session.sessionId())
                .param("reviewPriority", "URGENT"), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/staff/cases", session.sessionId())
                .param("cursor", "MjAyNi0wOC0xNFQwMDowMDowMFp8"), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/audit", session.sessionId(), ALERT)
                .param("limit", "101"), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void generatesOnlyAStaffReviewedDeterministicCopilotDraftWithoutExternalEgress() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p1-copilot-ingest-0001");
        applyBranchB(session, "p1-copilot-context-0001");
        String request = """
                {"draftType":"CONSULTATION_NOTE"}
                """;

        mockMvc.perform(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/copilot-drafts", session.sessionId(), CASE)
                .contentType(APPLICATION_JSON).content(request), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COPILOT_DRAFT_GENERATED"))
                .andExpect(jsonPath("$.data.draft.generatedBy").value("DETERMINISTIC_TEMPLATE"))
                .andExpect(jsonPath("$.data.draft.modelInvoked").value(false))
                .andExpect(jsonPath("$.data.draft.externalEgressAttempted").value(false))
                .andExpect(jsonPath("$.data.draft.retrievalMode").value("NONE"))
                .andExpect(jsonPath("$.data.draft.citations.length()").value(0))
                .andExpect(jsonPath("$.data.safety.containsDirectIdentifiers").value(false))
                .andExpect(jsonPath("$.data.safety.externalActionCreated").value(false))
                .andExpect(jsonPath("$.data.safety.humanReviewRequired").value(true));

        mockMvc.perform(client.customer(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/copilot-drafts", session.sessionId(), CASE)
                .contentType(APPLICATION_JSON).content(request), session))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsAStaffOnlyEvidenceBundleWithoutExternalFetch() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p1-evidence-ingest-0001");
        applyBranchB(session, "p1-evidence-context-0001");

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}/evidence", session.sessionId(), CASE), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CASE_EVIDENCE_RETRIEVED"))
                .andExpect(jsonPath("$.data.immutableT0").value(true))
                .andExpect(jsonPath("$.data.signals.length()").value(3))
                .andExpect(jsonPath("$.data.transactions.length()").value(4))
                .andExpect(jsonPath("$.data.officialSources.length()").value(2))
                .andExpect(jsonPath("$.data.provenance.syntheticData").value(true))
                .andExpect(jsonPath("$.data.provenance.externalFetchPerformed").value(false));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}/evidence", session.sessionId(), CASE), session))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsAStaffOnlyCaseTimelineWithImmutableAuditEvents() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p1-timeline-ingest-0001");
        applyBranchB(session, "p1-timeline-context-0001");

        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}/timeline", session.sessionId(), CASE), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CASE_TIMELINE_RETRIEVED"))
                .andExpect(jsonPath("$.data.currentState").value("PENDING_BANK_REVIEW"))
                .andExpect(jsonPath("$.data.phases.length()").value(2))
                .andExpect(jsonPath("$.data.phases[0].phase").value("T0_ALERT"))
                .andExpect(jsonPath("$.data.phases[1].phase").value("T1_CONTEXT"))
                .andExpect(jsonPath("$.data.auditTrail.length()").value(4))
                .andExpect(jsonPath("$.data.externalActionCreated").value(false));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}/timeline", session.sessionId(), CASE), session))
                .andExpect(status().isForbidden());
    }

    @Test
    void addsAnAppendOnlyStaffNoteWithIdempotencyAndNoExternalDelivery() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p1-note-ingest-0001");
        applyBranchB(session, "p1-note-context-0001");
        String request = """
                {"caseVersion":1,"note":"고객 응답과 합성 근거를 추가 확인합니다."}
                """;

        JsonNode created = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/notes", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-case-note-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(created.at("/code").asText()).isEqualTo("CASE_NOTE_ADDED");
        assertThat(created.at("/data/caseVersion").asLong()).isEqualTo(2);
        assertThat(created.at("/data/customerVisible").asBoolean()).isFalse();
        assertThat(created.at("/data/externalDeliveryCreated").asBoolean()).isFalse();

        JsonNode replay = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/notes", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-case-note-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(replay.at("/data/command/idempotencyReplayed").asBoolean()).isTrue();

        JsonNode list = read(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}/notes", session.sessionId(), CASE), session));
        assertThat(list.at("/code").asText()).isEqualTo("CASE_NOTES_RETRIEVED");
        assertThat(list.at("/data/items").size()).isEqualTo(1);
        assertThat(list.at("/data/items[0].isVisibleToCustomer").asBoolean()).isFalse();

        mockMvc.perform(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/notes", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-case-note-sensitive-0001")
                .contentType(APPLICATION_JSON).content("""
                        {"caseVersion":2,"note":"연락처 customer@example.com 을 내부 메모에 남깁니다."}
                        """), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        mockMvc.perform(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/notes", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-case-note-obfuscated-pii-0001")
                .contentType(APPLICATION_JSON).content("""
                        {"caseVersion":2,"note":"주 민 등 록 번 호 900101-1234567"}
                        """), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        mockMvc.perform(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/notes", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-case-note-hidden-char-0001")
                .contentType(APPLICATION_JSON).content("""
                        {"caseVersion":2,"note":"customer\\u200B@example.com"}
                        """), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        UUID noteId = UUID.fromString(created.at("/data/noteId").asText());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update case_note set note_text = '변조' where note_id = ?", noteId
        )).isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("append-only");

        mockMvc.perform(client.customer(delete(
                        "/api/v1/demo/sessions/{s}", session.sessionId()), session, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_DISCARDED"));
        Integer remainingNotes = jdbcTemplate.queryForObject(
                "select count(*) from case_note where demo_session_id = ?", Integer.class, session.sessionId()
        );
        assertThat(remainingNotes).isZero();
    }

    @Test
    void schedulesAnInternalFollowUpWithoutSendingAnyMessage() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "p1-follow-up-ingest-0001");
        applyBranchB(session, "p1-follow-up-context-0001");
        String reviewRequest = """
                {"action":"START_REVIEW","caseVersion":1,"note":"합성 근거를 확인합니다.","followUpAt":null}
                """;
        read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-follow-up-review-0001")
                .contentType(APPLICATION_JSON).content(reviewRequest), session));

        String request = """
                {
                  "caseVersion":2,
                  "scheduledAt":"%s",
                  "reason":"정기납부 처리 상태를 다시 확인합니다."
                }
                """.replace("%s",OffsetDateTime.now().plusDays(5).toString());
        JsonNode created = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/follow-ups", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-follow-up-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(created.at("/code").asText()).isEqualTo("FOLLOW_UP_SCHEDULED");
        assertThat(created.at("/data/currentState").asText()).isEqualTo("FOLLOW_UP_REQUIRED");
        assertThat(created.at("/data/caseVersion").asLong()).isEqualTo(3);
        assertThat(created.at("/data/deliveryAttempted").asBoolean()).isFalse();
        assertThat(created.at("/data/externalDeliveryCreated").asBoolean()).isFalse();

        JsonNode replay = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/follow-ups", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-follow-up-0001")
                .contentType(APPLICATION_JSON).content(request), session));
        assertThat(replay.at("/data/command/idempotencyReplayed").asBoolean()).isTrue();

        UUID followUpId = UUID.fromString(created.at("/data/followUpId").asText());
        JsonNode followUps = read(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}/follow-ups", session.sessionId(), CASE), session));
        assertThat(followUps.at("/code").asText()).isEqualTo("CASE_FOLLOW_UPS_RETRIEVED");
        assertThat(followUps.at("/data/items").size()).isEqualTo(1);

        String updateRequest = """
                {
                  "expectedCaseVersion":3,
                  "status":"COMPLETED",
                  "resultNote":"고객 전화 안내 없이 내부에서 상태를 확인했습니다."
                }
                """;
        JsonNode updated = read(client.staff(patch(
                        "/api/v1/demo/sessions/{s}/staff/follow-ups/{followUpId}", session.sessionId(), followUpId)
                .header("Idempotency-Key", "p1-follow-up-update-0001")
                .contentType(APPLICATION_JSON).content(updateRequest), session));
        assertThat(updated.at("/code").asText()).isEqualTo("FOLLOW_UP_UPDATED");
        assertThat(updated.at("/data/status").asText()).isEqualTo("COMPLETED");
        assertThat(updated.at("/data/caseVersion").asLong()).isEqualTo(4);
        assertThat(updated.at("/data/completedAt").isMissingNode()).isFalse();

        JsonNode updateReplay = read(client.staff(patch(
                        "/api/v1/demo/sessions/{s}/staff/follow-ups/{followUpId}", session.sessionId(), followUpId)
                .header("Idempotency-Key", "p1-follow-up-update-0001")
                .contentType(APPLICATION_JSON).content(updateRequest), session));
        assertThat(updateReplay.at("/data/command/idempotencyReplayed").asBoolean()).isTrue();

        var commandScope = jdbcTemplate.queryForMap(
                """
                select w.capability_hash, w.capability_role, w.http_method, s.staff_capability_hash
                  from workflow_command_result w
                  join demo_session s on s.session_id = w.demo_session_id
                 where w.demo_session_id = ? and w.demo_run_id = ?
                   and w.operation_path like '%/staff/follow-ups/%'
                """,
                session.sessionId(), session.demoRunId()
        );
        assertThat(commandScope.get("capability_hash")).isEqualTo(commandScope.get("staff_capability_hash"));
        assertThat(commandScope.get("capability_role")).isEqualTo("DEMO_STAFF");
        assertThat(commandScope.get("http_method")).isEqualTo("PATCH");

        mockMvc.perform(client.staff(patch(
                        "/api/v1/demo/sessions/{s}/staff/follow-ups/{followUpId}", session.sessionId(), followUpId)
                .header("Idempotency-Key", "p1-follow-up-client-time-0001")
                .contentType(APPLICATION_JSON).content("""
                        {
                          "expectedCaseVersion":4,
                          "status":"COMPLETED",
                          "resultNote":"클라이언트 완료시각은 허용하지 않습니다.",
                          "completedAt":"2026-08-21T10:00:00+09:00"
                        }
                        """), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        String nextRequest = """
                {
                  "expectedCaseVersion":4,
                  "scheduledAt":"%s",
                  "reason":"두 번째 내부 확인 일정을 등록합니다."
                }
                """.replace("%s",OffsetDateTime.now().plusDays(6).toString());
        JsonNode next = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/follow-ups", session.sessionId(), CASE)
                .header("Idempotency-Key", "p1-follow-up-0002")
                .contentType(APPLICATION_JSON).content(nextRequest), session));
        assertThat(next.at("/data/caseVersion").asLong()).isEqualTo(5);

        mockMvc.perform(client.staff(patch(
                        "/api/v1/demo/sessions/{s}/staff/follow-ups/{followUpId}", session.sessionId(), followUpId)
                .header("Idempotency-Key", "p1-follow-up-old-task-0001")
                .contentType(APPLICATION_JSON).content("""
                        {
                          "expectedCaseVersion":5,
                          "status":"CANCELLED",
                          "resultNote":"이전 완료 일정은 다시 변경할 수 없습니다."
                        }
                        """), session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        UUID nextFollowUpId = UUID.fromString(next.at("/data/followUpId").asText());
        String nextStatus = jdbcTemplate.queryForObject(
                "select status from follow_up_task where follow_up_id = ?",
                String.class,
                nextFollowUpId
        );
        assertThat(nextStatus).isEqualTo("SCHEDULED");
    }

    @Test
    void reviewActionsKeepExactlyOneScheduledFollowUpAndCompleteItWhenReviewResumes() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "review-follow-up-ingest-0001");
        applyBranchB(session, "review-follow-up-context-0001");

        read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "review-follow-up-start-0001")
                .contentType(APPLICATION_JSON).content("""
                        {
                          "action":"START_REVIEW",
                          "caseVersion":1,
                          "note":"합성 근거를 검토합니다.",
                          "followUpAt":null
                        }
                        """), session));

        JsonNode scheduled = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "review-follow-up-schedule-0001")
                .contentType(APPLICATION_JSON).content("""
                        {
                          "action":"REQUIRE_FOLLOW_UP",
                          "caseVersion":2,
                          "note":"내부에서 처리 상태를 다시 확인합니다.",
                          "followUpAt":"%s"
                        }
                        """.replace("%s",OffsetDateTime.now().plusDays(4).toString())), session));
        UUID followUpId = UUID.fromString(scheduled.at("/data/followUpId").asText());
        assertThat(scheduled.at("/data/currentState").asText()).isEqualTo("FOLLOW_UP_REQUIRED");
        mockMvc.perform(client.staff(get(
                        "/api/v1/demo/sessions/{s}/cases/{c}", session.sessionId(), CASE), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowedActions[2].action").value("CLOSE_FALSE_POSITIVE"))
                .andExpect(jsonPath("$.data.allowedActions[2].enabled").value(true));

        JsonNode resumed = read(client.staff(post(
                        "/api/v1/demo/sessions/{s}/cases/{c}/review", session.sessionId(), CASE)
                .header("Idempotency-Key", "review-follow-up-resume-0001")
                .contentType(APPLICATION_JSON).content("""
                        {
                          "action":"RESUME_REVIEW",
                          "caseVersion":3,
                          "note":"내부 확인을 마쳐 검토를 재개합니다.",
                          "followUpAt":null
                        }
                        """), session));
        assertThat(resumed.at("/data/followUpId").asText()).isEqualTo(followUpId.toString());
        assertThat(resumed.at("/data/currentState").asText()).isEqualTo("IN_BANK_REVIEW");
        assertThat(resumed.at("/data/caseVersion").asLong()).isEqualTo(4);

        var followUp = jdbcTemplate.queryForMap(
                """
                select status, result_note, completed_at
                  from follow_up_task where follow_up_id = ?
                """,
                followUpId
        );
        assertThat(followUp.get("status")).isEqualTo("COMPLETED");
        assertThat(followUp.get("result_note")).isEqualTo("내부 확인을 마쳐 검토를 재개합니다.");
        assertThat(followUp.get("completed_at")).isNotNull();

        Integer scheduledCount = jdbcTemplate.queryForObject(
                """
                select count(*) from follow_up_task
                 where demo_session_id = ? and demo_run_id = ? and case_id = ? and status = 'SCHEDULED'
                """,
                Integer.class,
                session.sessionId(), session.demoRunId(), CASE
        );
        assertThat(scheduledCount).isZero();
    }

    private JsonNode applyBranchB(DemoTestClient.Session session, String key) throws Exception {
        String request = """
                {
                  "responseCode":"UNABLE_TO_CONFIRM",
                  "demoBranchCode":"FIN_MGMT_B_NO_CONTEXT"
                }
                """;
        return read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/alerts/{a}/context", session.sessionId(), ALERT)
                .header("Idempotency-Key", key)
                .contentType(APPLICATION_JSON).content(request), session));
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
