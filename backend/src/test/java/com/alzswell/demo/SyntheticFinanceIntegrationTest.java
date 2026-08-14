package com.alzswell.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.common.security.DemoCapabilityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SyntheticFinanceIntegrationTest {

    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";

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
    void exposesSixRunScopedSyntheticFinanceApis() throws Exception {
        DemoTestClient.Session session = createAndIngest("finance-all-0001");

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/connections/consent-summary",
                        session.sessionId(), CUSTOMER), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].sourceProvider").value("SYNTHETIC_PROVIDER"))
                .andExpect(jsonPath("$.data.consentSummary.revocable").value(true))
                .andExpect(jsonPath("$.data.provenance.syntheticData").value(true));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/accounts",
                        session.sessionId(), CUSTOMER), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].currentBalance.amount").value("9250000"))
                .andExpect(jsonPath("$.data.items[0].maskedAccountNumber").value("***-***-1234"));

        JsonNode firstPage = client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/accounts/{a}/transactions",
                        session.sessionId(), "SYN_ACCOUNT_HANA_001")
                .param("direction", "OUT").param("limit", "2"), session));
        String cursor = firstPage.at("/data/nextCursor").asText();
        JsonNode secondPage = client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/accounts/{a}/transactions",
                        session.sessionId(), "SYN_ACCOUNT_HANA_001")
                .param("direction", "OUT").param("limit", "2").param("cursor", cursor), session));
        assertThat(firstPage.at("/data/items").size()).isEqualTo(2);
        assertThat(secondPage.at("/data/items").size()).isEqualTo(2);
        assertThat(secondPage.at("/data/items/0/transactionId").asText())
                .isNotEqualTo(firstPage.at("/data/items/0/transactionId").asText());

        JsonNode ledger = client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/accounts/{a}/transactions",
                        session.sessionId(), "SYN_ACCOUNT_SHINHAN_001")
                .param("from", "2025-08-01").param("to", "2026-07-31").param("limit", "100"), session));
        assertThat(ledger.at("/data/items").size()).isEqualTo(33);

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/baselines",
                        session.sessionId(), CUSTOMER), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].reasonCodes.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].currentValue").value("3"));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/financial-summary",
                        session.sessionId(), CUSTOMER), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assets.total.amount").value("48250000"))
                .andExpect(jsonPath("$.data.twelveMonthTrend.length()").value(12))
                .andExpect(jsonPath("$.data.changeSummary.reasonCodes.length()").value(3));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/protection-actions", session.sessionId()), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].executionType").value("GUIDANCE_ONLY"));
    }

    @Test
    void blocksCrossSessionTokensAndRejectsIncompleteCurrentRun() throws Exception {
        DemoTestClient.Session first = createAndIngest("finance-first-0001");
        DemoTestClient.Session second = createAndIngest("finance-second-0001");

        mockMvc.perform(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/accounts",
                        second.sessionId(), CUSTOMER)
                        .header(DemoCapabilityService.REQUEST_HEADER, first.customerCapability())
                        .header(DemoCapabilityService.RUN_HEADER, second.demoRunId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_NOT_FOUND"));

        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/OTHER/accounts", first.sessionId()), first))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_NOT_FOUND"));

        jdbcTemplate.update(
                "delete from synthetic_account where demo_session_id = ? and demo_run_id = ? and account_id = ?",
                first.sessionId(), first.demoRunId(), "SYN_ACCOUNT_KAKAO_001"
        );
        mockMvc.perform(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/accounts",
                        first.sessionId(), CUSTOMER), first))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYNTHETIC_FIXTURE_NOT_READY"));
    }

    @Test
    void resetCreatesANewRunWithTheSameImmutableT0Snapshot() throws Exception {
        DemoTestClient.Session beforeSession = createAndIngest("finance-reset-0001");
        List<JsonNode> before = readFinancialSnapshot(beforeSession);

        JsonNode reset = client.read(client.customer(post(
                        "/api/v1/demo/sessions/{s}/reset", beforeSession.sessionId())
                .header("Idempotency-Key", "finance-reset-key-0001"), beforeSession));
        DemoTestClient.Session afterSession = beforeSession.withRun(
                UUID.fromString(reset.at("/data/demoRunId").asText())
        );
        List<JsonNode> after = readFinancialSnapshot(afterSession);

        assertThat(afterSession.demoRunId()).isNotEqualTo(beforeSession.demoRunId());
        assertThat(after).isEqualTo(before);
        assertThat(after).allSatisfy(response ->
                assertThat(response.at("/provenance/snapshotHash").asText()).startsWith("sha256:"));
        Integer oldRunRows = jdbcTemplate.queryForObject(
                "select count(*) from synthetic_transaction where demo_session_id = ? and demo_run_id = ?",
                Integer.class, beforeSession.sessionId(), beforeSession.demoRunId()
        );
        assertThat(oldRunRows).isEqualTo(42);
    }

    private List<JsonNode> readFinancialSnapshot(DemoTestClient.Session session) throws Exception {
        return List.of(
                client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/connections/consent-summary",
                        session.sessionId(), CUSTOMER), session)).get("data"),
                client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/accounts",
                        session.sessionId(), CUSTOMER), session)).get("data"),
                client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/accounts/SYN_ACCOUNT_HANA_001/transactions",
                        session.sessionId()), session)).get("data"),
                client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/baselines",
                        session.sessionId(), CUSTOMER), session)).get("data"),
                client.read(client.customer(get(
                        "/api/v1/demo/sessions/{s}/customers/{c}/financial-summary",
                        session.sessionId(), CUSTOMER), session)).get("data")
        );
    }

    private DemoTestClient.Session createAndIngest(String key) throws Exception {
        return client.ingest(client.create(), key + "-ingest");
    }
}
