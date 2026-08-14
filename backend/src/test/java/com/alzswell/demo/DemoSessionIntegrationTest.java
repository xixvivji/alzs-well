package com.alzswell.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.common.security.DemoCapabilityService;
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
class DemoSessionIntegrationTest {

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
        assertThat(auditCount).isEqualTo(3);
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
    void listsOnlyTheFixedSyntheticScenarioAndNeverReturnsCapabilitiesInJson() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/v1/demo/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capabilities").doesNotExist())
                .andExpect(jsonPath("$.data.demoRunId").isEmpty())
                .andReturn();
        assertThat(create.getResponse().getHeader(DemoCapabilityService.CUSTOMER_RESPONSE_HEADER)).isNotBlank();
        assertThat(create.getResponse().getHeader(DemoCapabilityService.STAFF_RESPONSE_HEADER)).isNotBlank();

        mockMvc.perform(get("/api/v1/demo/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].scenarioId").value("FIN_MGMT_AB_001"))
                .andExpect(jsonPath("$.data.items[0].syntheticData").value(true));
    }
}
