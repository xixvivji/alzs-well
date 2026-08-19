package com.alzswell.detection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DetectionIntegrationTest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final String BASELINE_ID = "93000000-0000-0000-0000-000000000002";
    private static final String SIGNAL_ID = "94000000-0000-0000-0000-000000000002";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"DETECTION_READ", "DETECTION_CALCULATE"})
    void exposesBaselineFeaturesSignalsAndImmutableEvidence() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/baselines", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CUSTOMER_BASELINES_RETRIEVED"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].algorithmVersion").value("baseline-rules-v2.0.0"));

        mockMvc.perform(get("/api/v1/customers/{customerId}/baselines/{baselineId}",
                        CUSTOMER_ID, BASELINE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseline.featureCode").value("DUPLICATE_TRANSFER"))
                .andExpect(jsonPath("$.data.baselinePeriod.from").value("2025-11-01"))
                .andExpect(jsonPath("$.data.snapshotHash").value("sha256:baseline-duplicate-transfer-v1"));

        mockMvc.perform(get("/api/v1/customers/{customerId}/baselines/{baselineId}/features",
                        CUSTOMER_ID, BASELINE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].featureCode").value("SAME_PAYEE_AMOUNT_TRANSFERS"));

        mockMvc.perform(get("/api/v1/customers/{customerId}/signals", CUSTOMER_ID)
                        .queryParam("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].signalType").value("BEHAVIOR_CHANGE"));

        mockMvc.perform(get("/api/v1/signals/{signalId}", SIGNAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signal.reasonCode").value("DUPLICATE_TRANSFER"));

        mockMvc.perform(get("/api/v1/signals/{signalId}/evidence", SIGNAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].amount").value("500000"))
                .andExpect(jsonPath("$.data.items[0].integrityHash").isNotEmpty());
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"DETECTION_READ", "DETECTION_CALCULATE"})
    void recordsDeterministicCalculationWithoutExternalExecution() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{customerId}/baseline-calculations", CUSTOMER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));

        mockMvc.perform(post("/api/v1/customers/{customerId}/baseline-calculations", CUSTOMER_ID)
                        .header("Idempotency-Key", "baseline-calc-test-0001"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("BASELINE_CALCULATION_COMPLETED"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.baselinesEvaluated").value(3))
                .andExpect(jsonPath("$.data.signalsEvaluated").value(3))
                .andExpect(jsonPath("$.data.reusedCurrentSnapshot").value(true))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(false))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false));

        mockMvc.perform(post("/api/v1/customers/{customerId}/baseline-calculations", CUSTOMER_ID)
                        .header("Idempotency-Key", "baseline-calc-test-0001"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from baseline_calculation_job where customer_id = ?", Integer.class, CUSTOMER_ID);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "ANOTHER_CUSTOMER", authorities = {"DETECTION_READ", "DETECTION_CALCULATE"})
    void blocksCrossCustomerAccessAndHidesSignalOwnership() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/baselines", CUSTOMER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/signals/{signalId}", SIGNAL_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DETECTION_SIGNAL_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = "DETECTION_READ")
    void validatesFiltersAndRequiresCalculatePermission() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/signals", CUSTOMER_ID)
                        .queryParam("severity", "CRITICAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));

        mockMvc.perform(post("/api/v1/customers/{customerId}/baseline-calculations", CUSTOMER_ID)
                        .header("Idempotency-Key", "baseline-calc-test-0002"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/baselines", CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
    }
}
