package com.alzswell.trustedcontact;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;import org.testcontainers.junit.jupiter.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class TrustedContactIntegrationTest {
    private static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mockMvc;@Autowired ObjectMapper mapper;
    @Test @WithMockUser(username=CUSTOMER,authorities={"CONSENT_WRITE","TRUSTED_CONTACT_READ","TRUSTED_CONTACT_WRITE"})
    void managesDesignationWithoutAuthorityOrExternalContact()throws Exception{
        MvcResult consent=mockMvc.perform(post("/api/v1/customers/{id}/consents",CUSTOMER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purposeCode\":\"TRUSTED_CONTACT_DISCLOSURE\",\"scopes\":[\"CONTACT_MINIMUM\"],\"expiresAt\":\"2099-12-31T00:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn();
        String consentId=mapper.readTree(consent.getResponse().getContentAsByteArray()).path("data").path("consentId").asText();
        MvcResult created=mockMvc.perform(post("/api/v1/customers/{id}/trusted-contacts",CUSTOMER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"consentId\":\""+consentId+"\",\"displayName\":\"가족 1\",\"relationshipCode\":\"FAMILY\",\"maskedContact\":\"010-****-1234\",\"recipientAccepted\":true,\"scopes\":[\"ALERT_REASON_SUMMARY\"],\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.authorizedToAct").value(false))
                .andExpect(jsonPath("$.data.externalContactEnabled").value(false)).andReturn();
        JsonNode data=mapper.readTree(created.getResponse().getContentAsByteArray()).path("data");
        String id=data.path("contactId").asText();long version=data.path("version").asLong();
        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts",CUSTOMER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.externalContactExecuted").value(false));
        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts/{id}",CUSTOMER,id)).andExpect(status().isOk());
        MvcResult updated=mockMvc.perform(patch("/api/v1/customers/{customer}/trusted-contacts/{id}",CUSTOMER,id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":"+version+",\"scopes\":[\"CONTACT_REQUEST_STATUS\"],\"expiresAt\":\"2098-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scopes[0]").value("CONTACT_REQUEST_STATUS")).andReturn();
        long next=mapper.readTree(updated.getResponse().getContentAsByteArray()).path("data").path("version").asLong();
        mockMvc.perform(delete("/api/v1/customers/{customer}/trusted-contacts/{id}",CUSTOMER,id)
                        .param("expectedVersion",Long.toString(next)).param("reason","고객 철회"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
    }
    @Test @WithMockUser(username="OTHER",authorities="TRUSTED_CONTACT_READ")
    void preventsCrossCustomerRead()throws Exception{mockMvc.perform(get("/api/v1/customers/{id}/trusted-contacts",CUSTOMER)).andExpect(status().isForbidden());}
}
