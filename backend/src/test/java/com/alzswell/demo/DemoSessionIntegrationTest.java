package com.alzswell.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.common.security.DemoCapabilityService;
import com.alzswell.demo.application.DemoAuditWriter;
import com.alzswell.demo.application.DemoSessionCleanupService;
import com.alzswell.demo.application.DemoSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
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
class DemoSessionIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DemoSessionCleanupService cleanupService;
    @Autowired DemoAuditWriter auditWriter;

    private DemoTestClient client;

    @BeforeEach
    void setUp() {
        client = new DemoTestClient(mockMvc, objectMapper);
    }

    @Test
    void createsNonIdempotentSessionsAndReplaysIngestAndResetWithinRunScope() throws Exception {
        DemoTestClient.Session first = client.create();
        DemoTestClient.Session second = client.create();
        assertThat(second.sessionId()).isNotEqualTo(first.sessionId());
        assertThat(first.customerCapability()).isNotBlank().isNotEqualTo(first.staffCapability());

        DemoTestClient.Session ingested = client.ingest(first, "ingest-fin-mgmt-0001");
        JsonNode repeatedIngest = client.read(client.customer(post(
                        "/api/v1/demo/sessions/{sessionId}/scenarios/FIN_MGMT_AB_001/ingest",
                        first.sessionId()
                ).header("Idempotency-Key", "ingest-fin-mgmt-0001"), first, false));
        assertThat(repeatedIngest.at("/data/demoRunId").asText())
                .isEqualTo(ingested.demoRunId().toString());
        assertThat(repeatedIngest.at("/data/reasonCodes").toString())
                .contains("MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION");

        MvcResult firstResetResult = mockMvc.perform(client.customer(post(
                        "/api/v1/demo/sessions/{sessionId}/reset", first.sessionId())
                        .header("Idempotency-Key", "reset-a-to-b-0001")
                        .header(DemoCapabilityService.RUN_HEADER, ingested.demoRunId()), ingested, false))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstReset = objectMapper.readTree(firstResetResult.getResponse().getContentAsByteArray());
        UUID nextRunId = UUID.fromString(firstReset.at("/data/demoRunId").asText());
        assertThat(nextRunId).isNotEqualTo(ingested.demoRunId());
        assertThat(firstReset.at("/data/previousDemoRunId").asText())
                .isEqualTo(ingested.demoRunId().toString());

        JsonNode repeatedReset = client.read(client.customer(post(
                        "/api/v1/demo/sessions/{sessionId}/reset", first.sessionId())
                        .header("Idempotency-Key", "reset-a-to-b-0001")
                        .header(DemoCapabilityService.RUN_HEADER, ingested.demoRunId()), ingested, false));
        assertThat(repeatedReset.at("/data/demoRunId").asText()).isEqualTo(nextRunId.toString());
        assertThat(repeatedReset.at("/data/resetVersion").asInt()).isEqualTo(1);

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{sessionId}", first.sessionId()), first, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenarioId").value("FIN_MGMT_AB_001"))
                .andExpect(jsonPath("$.data.scenarioSeed").value(first.scenarioSeed()))
                .andExpect(jsonPath("$.data.resetVersion").value(1))
                .andExpect(jsonPath("$.data.demoRunId").value(nextRunId.toString()));

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(*) from decision_audit where demo_session_id = ?",
                Integer.class,
                first.sessionId()
        );
        assertThat(auditCount).isEqualTo(4);
    }

    @Test
    void resetBeforeIngestKeepsRunIdentifiersPrivateUntilFixtureExists() throws Exception {
        DemoTestClient.Session session = client.create();
        JsonNode reset = client.read(client.customer(post(
                        "/api/v1/demo/sessions/{sessionId}/reset", session.sessionId())
                        .header("Idempotency-Key", "reset-empty-0001"), session, false));
        assertThat(reset.at("/data/previousDemoRunId").isNull()).isTrue();
        assertThat(reset.at("/data/demoRunId").isNull()).isTrue();
        assertThat(reset.at("/data/scenarioId").isNull()).isTrue();

        DemoTestClient.Session ingested = client.ingest(session, "ingest-after-reset-0001");
        assertThat(ingested.demoRunId()).isNotNull();
    }

    @Test
    void decisionAuditHashCanBeRecomputedFromThePersistedTimestamp() throws Exception {
        DemoTestClient.Session session = client.create();
        OffsetDateTime nanosecondTimestamp = OffsetDateTime.parse("2026-08-27T12:34:56.123456789+09:00");
        String eventType = "TIMESTAMP_CANONICALIZATION_TEST";

        auditWriter.write(session.sessionId(), eventType, Map.of(), nanosecondTimestamp);

        Map<String, Object> event = jdbcTemplate.queryForMap("""
                select audit_id,demo_session_id,demo_run_id,trace_id,event_type,actor_type,actor_id,
                       target_type,target_id,before_state,after_state,policy_version,algorithm_version,
                       schema_version,event_payload::text,previous_event_hash,event_hash
                  from decision_audit where demo_session_id=? and event_type=?
                """, session.sessionId(), eventType);
        OffsetDateTime persisted = jdbcTemplate.queryForObject("""
                select occurred_at from decision_audit where demo_session_id=? and event_type=?
                """, (rs, row) -> rs.getObject("occurred_at", OffsetDateTime.class),
                session.sessionId(), eventType);
        assertThat(persisted.getNano() % 1_000).isZero();
        assertThat(persisted).isEqualTo(OffsetDateTime.parse("2026-08-27T03:34:56.123456Z"));

        String recalculated = sha256(String.join("\n",
                String.valueOf(event.get("previous_event_hash")),
                event.get("audit_id").toString(),
                event.get("demo_session_id").toString(),
                event.get("demo_run_id") == null ? "" : event.get("demo_run_id").toString(),
                event.get("trace_id").toString(),
                event.get("event_type").toString(),
                event.get("actor_type").toString(),
                event.get("actor_id") == null ? "" : event.get("actor_id").toString(),
                event.get("target_type").toString(),
                event.get("target_id").toString(),
                event.get("before_state") == null ? "" : event.get("before_state").toString(),
                event.get("after_state") == null ? "" : event.get("after_state").toString(),
                event.get("policy_version").toString(),
                event.get("algorithm_version").toString(),
                event.get("schema_version").toString(),
                event.get("event_payload").toString(),
                persisted.toInstant().toString()));
        assertThat(event.get("event_hash")).isEqualTo(recalculated);
    }

    @Test
    void rejectsMissingMutationKeyUnsupportedScenarioAndStaleRun() throws Exception {
        DemoTestClient.Session session = client.create();
        mockMvc.perform(client.customer(post(
                        "/api/v1/demo/sessions/{sessionId}/scenarios/FIN_MGMT_AB_001/ingest",
                        session.sessionId()), session, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        mockMvc.perform(client.customer(post(
                        "/api/v1/demo/sessions/{sessionId}/scenarios/UNKNOWN/ingest",
                        session.sessionId()).header("Idempotency-Key", "ingest-unknown-0001"), session, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEMO_SCENARIO_NOT_SUPPORTED"));

        DemoTestClient.Session ingested = client.ingest(session, "ingest-valid-0001");
        mockMvc.perform(client.customer(get(
                                "/api/v1/demo/sessions/{sessionId}/customers/SYN_CUSTOMER_FIN_MGMT_001/accounts",
                                session.sessionId())
                        .header(DemoCapabilityService.RUN_HEADER, UUID.randomUUID()), ingested, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEMO_RUN_STALE"));
    }

    @Test
    void hidesMissingExpiredAndCrossSessionCapabilitiesAndBlocksRoleEscalation() throws Exception {
        DemoTestClient.Session first = client.create();
        DemoTestClient.Session second = client.create();

        mockMvc.perform(get("/api/v1/demo/sessions/{sessionId}", first.sessionId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/demo/sessions/{sessionId}", first.sessionId())
                        .header(DemoCapabilityService.REQUEST_HEADER, second.customerCapability()))
                .andExpect(status().isNotFound());

        DemoTestClient.Session ingested = client.ingest(first, "ingest-role-0001");
        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{sessionId}/staff/cases", first.sessionId()), ingested))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_CAPABILITY_SCOPE_FORBIDDEN"));

        jdbcTemplate.update(
                """
                update demo_session
                   set created_at = now() - interval '2 hours',
                       expires_at = now() - interval '1 minute'
                 where session_id = ?
                """,
                first.sessionId()
        );
        mockMvc.perform(get("/api/v1/demo/sessions/{sessionId}", first.sessionId())
                        .header(DemoCapabilityService.REQUEST_HEADER, first.customerCapability()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_NOT_FOUND"));
    }

    @Test
    void rejectsEncodedOrAmbiguousPathsBeforeRoleClassification() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "encoded-path-ingest-0001");
        mockMvc.perform(client.customer(post(
                        "/api/v1/demo/sessions/{sessionId}/alerts/ALERT_FIN_MGMT_001/context",
                        session.sessionId())
                .header("Idempotency-Key", "encoded-path-context-0001")
                .contentType(APPLICATION_JSON)
                .content("""
                        {"responseCode":"UNABLE_TO_CONFIRM","demoBranchCode":"FIN_MGMT_B_NO_CONTEXT"}
                        """), session))
                .andExpect(status().isOk());

        String encodedCasesPath = "/api/v1/demo/sessions/" + session.sessionId()
                + "/%63ases/CASE_FIN_MGMT_001";
        mockMvc.perform(client.customer(get(URI.create(encodedCasesPath)), session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_NOT_FOUND"));

        String encodedSessionsPath = "/api/v1/demo/%73essions/" + session.sessionId();
        mockMvc.perform(get(URI.create(encodedSessionsPath))
                        .header(DemoCapabilityService.REQUEST_HEADER, session.customerCapability()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_NOT_FOUND"));
    }

    @Test
    void corsAllowsOnlyConfiguredDevelopmentOrigins() throws Exception {
        DemoTestClient.Session session = client.create();

        mockMvc.perform(get("/api/v1/demo/sessions/{sessionId}", session.sessionId())
                        .header("Origin", "https://untrusted.example")
                        .header(DemoCapabilityService.REQUEST_HEADER, session.customerCapability()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/demo/sessions/{sessionId}", session.sessionId())
                        .header("Origin", "http://localhost:5173")
                        .header(DemoCapabilityService.REQUEST_HEADER, session.customerCapability()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
                        .isEqualTo("http://localhost:5173"));

        mockMvc.perform(get("/api/v1/demo/sessions/{sessionId}", session.sessionId())
                        .header("Origin", "http://localhost:4173")
                        .header(DemoCapabilityService.REQUEST_HEADER, session.customerCapability()))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/demo/staff/sessions/{sessionId}/capability", session.sessionId())
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/demo/staff/sessions/{sessionId}/capability", session.sessionId())
                        .header("Origin", "http://localhost:4173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4173"));

        mockMvc.perform(post("/api/v1/demo/sessions")
                        .header("Origin", "http://localhost:4173"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/demo/sessions/{sessionId}/cases/{caseId}",
                        session.sessionId(), DemoSessionService.CASE_ID)
                        .header("Origin", "http://localhost:4173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4173"));

        mockMvc.perform(options("/api/v1/demo/sessions/{sessionId}/cases/{caseId}",
                        session.sessionId(), DemoSessionService.CASE_ID)
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsOnlyTheFixedSyntheticScenarioAndNeverReturnsCapabilitiesInJson() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/v1/demo/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capabilities").doesNotExist())
                .andExpect(jsonPath("$.data.demoRunId").isEmpty())
                .andReturn();
        assertThat(create.getResponse().getHeader(DemoCapabilityService.CUSTOMER_RESPONSE_HEADER)).isNotBlank();
        assertThat(create.getResponse().getHeader(DemoCapabilityService.STAFF_RESPONSE_HEADER)).isNull();

        UUID sessionId = UUID.fromString(objectMapper.readTree(create.getResponse().getContentAsByteArray())
                .at("/data/sessionId").asText());
        mockMvc.perform(post("/api/v1/demo/staff/sessions/{sessionId}/capability", sessionId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/demo/staff/sessions/{sessionId}/capability", sessionId)
                        .with(httpBasic(DemoTestClient.STAFF_USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized());
        MvcResult staffIssuance = mockMvc.perform(post(
                        "/api/v1/demo/staff/sessions/{sessionId}/capability", sessionId)
                        .with(httpBasic(DemoTestClient.STAFF_USERNAME, DemoTestClient.STAFF_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(staffIssuance.getResponse().getHeader(DemoCapabilityService.STAFF_RESPONSE_HEADER)).isNotBlank();

        mockMvc.perform(get("/api/v1/demo/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].scenarioId").value("FIN_MGMT_AB_001"))
                .andExpect(jsonPath("$.data.items[0].syntheticData").value(true));
    }

    @Test
    void discardsOwnedDemoSessionWhilePreservingTheImmutableAuditChain() throws Exception {
        DemoTestClient.Session session = client.create();

        mockMvc.perform(client.customer(delete("/api/v1/demo/sessions/{sessionId}", session.sessionId()), session, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_DISCARDED"))
                .andExpect(jsonPath("$.data.sessionId").value(session.sessionId().toString()))
                .andExpect(jsonPath("$.data.syntheticDataDeleted").value(true))
                .andExpect(jsonPath("$.data.externalActionCreated").value(false));

        Integer remainingSessions = jdbcTemplate.queryForObject(
                "select count(*) from demo_session where session_id = ?", Integer.class, session.sessionId()
        );
        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(*) from decision_audit where demo_session_id = ?", Integer.class, session.sessionId()
        );
        assertThat(remainingSessions).isZero();
        assertThat(auditCount).isEqualTo(3);

        mockMvc.perform(get("/api/v1/demo/sessions/{sessionId}", session.sessionId())
                        .header(DemoCapabilityService.REQUEST_HEADER, session.customerCapability()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_NOT_FOUND"));
    }

    @Test
    void purgesExpiredSyntheticSessionsWhilePreservingTheImmutableAuditChain() throws Exception {
        DemoTestClient.Session session = client.ingest(client.create(), "cleanup-ingest-0001");
        jdbcTemplate.update(
                """
                update demo_session
                   set created_at = now() - interval '2 hours',
                       expires_at = now() - interval '1 minute'
                 where session_id = ?
                """,
                session.sessionId()
        );

        assertThat(cleanupService.cleanupExpiredSessions()).isGreaterThanOrEqualTo(1);

        Integer remainingSessions = jdbcTemplate.queryForObject(
                "select count(*) from demo_session where session_id = ?", Integer.class, session.sessionId()
        );
        Integer purgeAuditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from decision_audit
                 where demo_session_id = ? and event_type = 'DEMO_SESSION_EXPIRED_PURGED'
                """,
                Integer.class,
                session.sessionId()
        );
        assertThat(remainingSessions).isZero();
        assertThat(purgeAuditCount).isEqualTo(1);
    }

    private String sha256(String value) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
