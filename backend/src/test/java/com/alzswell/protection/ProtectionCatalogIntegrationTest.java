package com.alzswell.protection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProtectionCatalogIntegrationTest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {
            "PROTECTION_ACTION_READ", "PROTECTION_ACTION_EVALUATE", "PROTECTION_ENROLLMENT_READ"})
    void exposesGuidanceOnlyCatalogEvaluationAndSyntheticEnrollmentSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/protection-actions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].externalExecutionAvailable").value(false));
        mockMvc.perform(get("/api/v1/protection-actions/SAFE_BLOCK_INFO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.applicationEndpointProvided").value(false))
                .andExpect(jsonPath("$.data.citationPassageIds[0]").isNotEmpty());
        mockMvc.perform(post("/api/v1/protection-actions/SAFE_BLOCK_INFO/eligibility-evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID
                                + "\",\"reasonCode\":\"DUPLICATE_TRANSFER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("GUIDANCE_ELIGIBLE"))
                .andExpect(jsonPath("$.data.externalExecutionAllowed").value(false))
                .andExpect(jsonPath("$.data.applicationCreated").value(false));
        mockMvc.perform(get("/api/v1/customers/{customerId}/protection-enrollments", CUSTOMER_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false))
                .andExpect(jsonPath("$.data.items[0].providerMode").value("SYNTHETIC_PROVIDER"))
                .andExpect(jsonPath("$.data.items[0].readOnly").value(true));
    }

    @Test @WithMockUser(username = "OTHER_CUSTOMER", authorities = "PROTECTION_ENROLLMENT_READ")
    void preventsReadingAnotherCustomersEnrollment() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/protection-enrollments", CUSTOMER_ID))
                .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "PROTECTION_ACTION_READ")
    void hidesUnknownAction() throws Exception {
        mockMvc.perform(get("/api/v1/protection-actions/UNKNOWN_ACTION"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROTECTION_ACTION_NOT_FOUND"));
    }
}
