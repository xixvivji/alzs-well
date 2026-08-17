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
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

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
                  )
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(23);
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
                .andExpect(jsonPath("$.data.checks.safeGuardrails").value("UP"));
    }

    @Test
    @Transactional
    void readinessReturnsServiceUnavailableWhenThePolicyCatalogIsMissing() throws Exception {
        jdbcTemplate.update("delete from protection_action_catalog");

        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYSTEM_NOT_READY"))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks.policyCatalog").value("DOWN"));
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
                .andExpect(jsonPath("$.data.schemaVersion").value("14"))
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
    void openApiPublishesExactlyTheP0ContractAsReadOnlyDocumentation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andReturn();

        JsonNode specification = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(specification.path("paths").size()).isEqualTo(30);
        long operationCount = StreamSupport.stream(specification.path("paths").spliterator(), false)
                .mapToLong(path -> List.of("get", "post", "put", "patch", "delete").stream()
                        .filter(path::has)
                        .count())
                .sum();
        assertThat(operationCount).isEqualTo(33);

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
