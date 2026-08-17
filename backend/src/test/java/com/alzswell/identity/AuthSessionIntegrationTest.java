package com.alzswell.identity;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "app.features.customer-profile-api-enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthSessionIntegrationTest {
    private static final String LOGIN_BODY = """
            {"loginId":"synthetic-customer","password":"local-synthetic-customer-password"}
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void loginMePermissionsRefreshAndLogoutFormAClosedSessionLoop() throws Exception {
        JsonNode login = body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString());
        String access = login.at("/data/accessToken").asText();
        String refresh = login.at("/data/refreshToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value("SYN_CUSTOMER_FIN_MGMT_001"))
                .andExpect(jsonPath("$.data.roles[0]").value("CUSTOMER"));
        mockMvc.perform(get("/api/v1/auth/me/permissions").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("CUSTOMER_PROFILE_READ"))
                .andExpect(jsonPath("$.data.permissions[1]").value("CUSTOMER_PROFILE_WRITE"));
        mockMvc.perform(get("/api/v1/customers/SYN_CUSTOMER_FIN_MGMT_001")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        JsonNode rotated = body(mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String rotatedAccess = rotated.at("/data/accessToken").asText();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/token/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidCredentialsWithoutRevealingWhichFieldFailed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                        {"loginId":"synthetic-customer","password":"incorrect-password"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void authCorsAllowsOnlyTheCustomerOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:4173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private JsonNode body(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
