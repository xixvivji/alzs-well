package com.alzswell.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "app.features.customer-profile-api-enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CustomerProfileIntegrationTest {

    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void openApiPublishesTypedCustomerResponseSchemas() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode specification = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode responseSchema = specification.path("paths")
                .path("/api/v1/customers/{customerId}")
                .path("get")
                .path("responses")
                .path("200")
                .path("content")
                .path("*/*")
                .path("schema");

        String envelopeName = responseSchema.path("$ref").asText().replace("#/components/schemas/", "");
        assertThat(envelopeName).isNotBlank();

        String dataReference = specification.path("components")
                .path("schemas")
                .path(envelopeName)
                .path("properties")
                .path("data")
                .path("$ref")
                .asText();
        assertThat(dataReference).endsWith("/CustomerSummary");

        JsonNode customerProperties = specification.path("components")
                .path("schemas")
                .path("CustomerSummary")
                .path("properties");
        assertThat(customerProperties.has("customerId")).isTrue();
        assertThat(customerProperties.has("displayName")).isTrue();
        assertThat(customerProperties.has("version")).isTrue();
        assertThat(customerProperties.has("updatedAt")).isTrue();
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"CUSTOMER_PROFILE_READ", "CUSTOMER_PROFILE_WRITE"})
    void persistsProfilePreferencesAndAccessibilityWithOptimisticVersions() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("이용자 001"))
                .andExpect(jsonPath("$.data.version").value(0));

        mockMvc.perform(patch("/api/v1/customers/{customerId}/display-profile", CUSTOMER_ID)
                        .header("Idempotency-Key", "customer-display-0001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"displayName":"안심 고객"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("안심 고객"))
                .andExpect(jsonPath("$.data.version").value(1));
        mockMvc.perform(patch("/api/v1/customers/{customerId}/display-profile", CUSTOMER_ID)
                        .header("Idempotency-Key", "customer-display-0001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"displayName\":\"안심 고객\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("안심 고객"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(patch("/api/v1/customers/{customerId}/preferences", CUSTOMER_ID)
                        .header("Idempotency-Key", "customer-preferences-0001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"inAppNotificationEnabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsNotificationEnabled").value(false))
                .andExpect(jsonPath("$.data.inAppNotificationEnabled").value(false))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/v1/customers/{customerId}/accessibility-settings", CUSTOMER_ID)
                        .header("Idempotency-Key", "customer-accessibility-0001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":0,
                                  "largeFont":true,
                                  "highContrast":true,
                                  "speechGuidance":true,
                                  "oneHandMode":false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highContrast").value(true))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/v1/customers/{customerId}/data-summary", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.institutions").value(2))
                .andExpect(jsonPath("$.data.lastSyncAt").isEmpty())
                .andExpect(jsonPath("$.data.dataFreshness.accounts").value("FIXED_SNAPSHOT"));
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"CUSTOMER_PROFILE_READ", "CUSTOMER_PROFILE_WRITE"})
    void rejectsStaleVersionAndEmptyPatch() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/{customerId}/display-profile", CUSTOMER_ID)
                        .header("Idempotency-Key", "customer-display-stale-0001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":99,"displayName":"충돌"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_VERSION_CONFLICT"));

        mockMvc.perform(patch("/api/v1/customers/{customerId}/preferences", CUSTOMER_ID)
                        .header("Idempotency-Key", "customer-preferences-empty-0001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    @WithMockUser(username = "ANOTHER_CUSTOMER", authorities = "CUSTOMER_PROFILE_READ")
    void blocksCrossCustomerAccess() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
    }
}
