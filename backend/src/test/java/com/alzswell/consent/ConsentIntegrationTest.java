package com.alzswell.consent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class ConsentIntegrationTest {
    private static final String CUSTOMER_ID="SYN_CUSTOMER_FIN_MGMT_001";
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mockMvc; @Autowired ObjectMapper objectMapper;

    @Test @WithMockUser(username=CUSTOMER_ID,authorities={"CONSENT_READ","CONSENT_WRITE","DISCLOSURE_EVALUATE"})
    void grantsEvaluatesAuditsAndWithdrawsConsentWithoutExternalDisclosure()throws Exception{
        MvcResult created=mockMvc.perform(post("/api/v1/customers/{customerId}/consents",CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"purposeCode":"PROTECTION_GUIDANCE","scopes":["BASELINE_SIGNAL","PROTECTION_CASE"],
                         "expiresAt":"2099-12-31T00:00:00Z"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("GRANTED"))
                .andExpect(jsonPath("$.data.revocable").value(true)).andReturn();
        JsonNode data=objectMapper.readTree(created.getResponse().getContentAsByteArray()).path("data");
        String consentId=data.path("consentId").asText(); long version=data.path("version").asLong();

        mockMvc.perform(get("/api/v1/customers/{customerId}/consents",CUSTOMER_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/customers/{customerId}/consents/{consentId}",CUSTOMER_ID,consentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scopes.length()").value(2));
        mockMvc.perform(post("/api/v1/customers/{customerId}/disclosure-evaluations",CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"consentId":"%s","purposeCode":"PROTECTION_GUIDANCE",
                         "requestedScopes":["PROTECTION_CASE"]}
                        """.formatted(consentId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("ALLOW_MINIMUM_SCOPE"))
                .andExpect(jsonPath("$.data.externalDisclosureRequested").value(false))
                .andExpect(jsonPath("$.data.externalDisclosureCreated").value(false));
        mockMvc.perform(post("/api/v1/customers/{customerId}/consents/{consentId}/withdraw",CUSTOMER_ID,consentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":"+version+",\"reason\":\"고객 요청\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.revocable").value(false));
        mockMvc.perform(get("/api/v1/customers/{customerId}/consents/{consentId}/history",CUSTOMER_ID,consentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[1].eventType").value("WITHDRAWN"));
        mockMvc.perform(get("/api/v1/customers/{customerId}/consents",CUSTOMER_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test @WithMockUser(username="OTHER",authorities="CONSENT_READ")
    void preventsCrossCustomerRead()throws Exception{
        mockMvc.perform(get("/api/v1/customers/{customerId}/consents",CUSTOMER_ID))
                .andExpect(status().isForbidden());
    }

    @Test void requiresAuthentication()throws Exception{
        mockMvc.perform(get("/api/v1/customers/{customerId}/consents",CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
    }
}
