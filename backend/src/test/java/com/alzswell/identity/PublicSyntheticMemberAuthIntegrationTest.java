package com.alzswell.identity;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.fixture.application.SyntheticFixtureGenerationService;
import com.alzswell.fixture.application.SyntheticFixtureProfile;
import com.alzswell.fixture.application.SyntheticMemberProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.features.customer-profile-api-enabled=true",
        "app.features.local-auth-api-enabled=true",
        "app.features.public-synthetic-member-auth-enabled=true",
        "app.deployment.public-exposure=false",
        "app.auth.public-synthetic-members-only=true"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicSyntheticMemberAuthIntegrationTest {
    private static final String PASSWORD = "local-synthetic-customer-password";
    private static final String PASSWORD_HASH =
            "$2y$12$Bu7SxonBbyIlnLnrupD/.eEWz3ZVBoC8bDvguOq9iJlsOAN8pGxBm";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SyntheticFixtureGenerationService fixtureService;
    @Autowired SyntheticMemberProvisioningService provisioningService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeAll
    void provisionPublicMembers() {
        var run = fixtureService.generate(
                SyntheticFixtureProfile.PUBLIC, "synthetic-v3.0.0", 20_260_901L, 50, false);
        provisioningService.provision(run.runId(), PASSWORD_HASH);
    }

    @Test
    void allowsOnlyProvisionedPublicMembersAndRestoresTheirOwnedIdentity() throws Exception {
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"loginId\":\"demo001\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCEEDED"))
                .andReturn().getResponse().getContentAsString());
        String access = login.at("/data/accessToken").asText();

        JsonNode me = objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("demo001"))
                .andExpect(jsonPath("$.data.customerId").value(org.hamcrest.Matchers.startsWith("SYN_V3_PUBLIC_")))
                .andReturn().getResponse().getContentAsString());
        String ownCustomerId = me.at("/data/customerId").asText();

        mockMvc.perform(get("/api/v1/customers/" + ownCustomerId)
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        String otherCustomerId = jdbcTemplate.queryForObject(
                "select customer_id from auth_principal where login_id='demo002'", String.class);
        mockMvc.perform(get("/api/v1/customers/" + otherCustomerId)
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTheLegacySharedSyntheticAccountInPublicMode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(
                        "{\"loginId\":\"synthetic-customer\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void separatesPublicCustomerStaffAndAdminRoles() throws Exception {
        String staffAccess = login("staff001");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + staffAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("PROTECTION_STAFF"));
        mockMvc.perform(get("/api/v1/admin/rules").header("Authorization", "Bearer " + staffAccess))
                .andExpect(status().isForbidden());

        String adminAccess = login("admin001");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("DETECTION_ADMIN"));
        mockMvc.perform(get("/api/v1/admin/rules").header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isOk());

        String customerAccess = login("demo001");
        mockMvc.perform(get("/api/v1/admin/rules").header("Authorization", "Bearer " + customerAccess))
                .andExpect(status().isForbidden());
    }

    private String login(String loginId) throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return response.at("/data/accessToken").asText();
    }
}
