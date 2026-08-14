package com.alzswell.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

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
