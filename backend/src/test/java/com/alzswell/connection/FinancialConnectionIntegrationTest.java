package com.alzswell.connection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FinancialConnectionIntegrationTest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = "FINANCIAL_CONNECTION_READ")
    void returnsSyntheticInstitutionCatalogAndCustomerConnections() throws Exception {
        mockMvc.perform(get("/api/v1/financial-institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].providerMode").value("SYNTHETIC_PROVIDER"));

        mockMvc.perform(get("/api/v1/financial-institutions/SYNTHETIC_BANK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.institution.displayName").value("안심은행"))
                .andExpect(jsonPath("$.data.supportedScopes.length()").value(2))
                .andExpect(jsonPath("$.data.supportedScopes[0].readOnly").value(true));

        mockMvc.perform(get("/api/v1/customers/{customerId}/connections", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].customerId").value(CUSTOMER_ID));

        mockMvc.perform(get("/api/v1/customers/{customerId}/connections/{connectionId}", CUSTOMER_ID,
                        "92000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connection.institution.institutionId").value("SYNTHETIC_BANK"))
                .andExpect(jsonPath("$.data.consentScopes.length()").value(2))
                .andExpect(jsonPath("$.data.consentScopes[0].consentStatus").value("CONSENTED"));
    }

    @Test
    void requiresAuthenticationForInstitutionCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/financial-institutions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ANOTHER_CUSTOMER", authorities = "FINANCIAL_CONNECTION_READ")
    void blocksCrossCustomerConnectionAccess() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/connections", CUSTOMER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = "FINANCIAL_CONNECTION_READ")
    void hidesUnknownInstitutionAndConnection() throws Exception {
        mockMvc.perform(get("/api/v1/financial-institutions/UNKNOWN_BANK"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONNECTION_INSTITUTION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/customers/{customerId}/connections/{connectionId}", CUSTOMER_ID,
                        "92000000-0000-0000-0000-000000000099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONNECTION_NOT_FOUND"));
    }
}
