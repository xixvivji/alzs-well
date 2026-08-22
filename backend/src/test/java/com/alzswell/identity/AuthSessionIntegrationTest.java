package com.alzswell.identity;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.StreamSupport;
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
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void fullFeatureOpenApiPublishesMetadataForAllImplementedOperations() throws Exception {
        JsonNode specification = body(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<JsonNode> operations = StreamSupport.stream(specification.path("paths").spliterator(), false)
                .flatMap(path -> List.of("get", "post", "put", "patch", "delete").stream()
                        .filter(path::has).map(path::path))
                .toList();
        assertThat(operations).hasSize(176).allSatisfy(operation -> {
            assertThat(operation.path("summary").asText()).isNotBlank();
            assertThat(operation.path("description").asText()).isNotBlank();
            assertThat(operation.path("x-alzs-authority-mode").asText()).isNotBlank();
            assertThat(operation.path("x-alzs-required-authorities").isArray()).isTrue();
            assertThat(operation.path("x-alzs-external-action").asText()).isEqualTo("NEVER");
        });
    }

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
                .andExpect(jsonPath("$.data.permissions").value(hasItems(
                        "CUSTOMER_PROFILE_READ", "CUSTOMER_PROFILE_WRITE",
                        "FINANCIAL_CONNECTION_READ", "DETECTION_READ", "DETECTION_CALCULATE",
                        "ALERT_READ", "ALERT_RESPOND", "RECURRING_PAYMENT_READ",
                        "RECURRING_PAYMENT_WRITE", "ACCOUNT_READ", "ACCOUNT_WRITE",
                        "TRANSACTION_READ", "TRANSACTION_WRITE", "FINANCIAL_OVERVIEW_READ")));
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

        // 이미 사용한 refresh token 재사용은 탈취 신호이므로 새 access token을 포함한 family 전체를 폐기한다.
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isUnauthorized());
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
    void logoutAllRevokesEveryActiveSessionForThePrincipal() throws Exception {
        String firstAccess = login().at("/data/accessToken").asText();
        String secondAccess = login().at("/data/accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_LOGOUT_ALL_SUCCEEDED"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + firstAccess))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void limitsActiveSessionsAndRevokesTheOldestSession() throws Exception {
        String oldestAccess = login().at("/data/accessToken").asText();
        for (int index = 0; index < 5; index++) login();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldestAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitsRepeatedFailuresWithoutStoringTheRawLoginId() throws Exception {
        String loginId = "missing-account-for-rate-limit";
        String body = "{\"loginId\":\"" + loginId + "\",\"password\":\"incorrect-password\"}";
        for (int index = 0; index < 10; index++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_RATE_LIMITED"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
        String loginHash = com.alzswell.identity.application.AuthSessionService.hash(loginId);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from auth_login_event where login_id_hash=? and outcome='FAILED'
                """, Integer.class, loginHash)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from auth_login_event where login_id_hash=? and outcome='RATE_LIMITED'
                """, Integer.class, loginHash)).isEqualTo(2);
    }

    @Test
    void refreshNeverExtendsBeyondTheAbsoluteSessionExpiry() throws Exception {
        JsonNode issued = login();
        String refresh = issued.at("/data/refreshToken").asText();
        jdbcTemplate.update("""
                update auth_session set access_expires_at = now() + interval '5 minutes',
                    refresh_expires_at = now() + interval '5 minutes',
                    absolute_expires_at = now() + interval '5 minutes'
                 where refresh_token_hash = ?
                """, com.alzswell.identity.application.AuthSessionService.hash(refresh));
        jdbcTemplate.update("""
                update auth_refresh_token set expires_at = now() + interval '5 minutes'
                 where token_hash = ?
                """, com.alzswell.identity.application.AuthSessionService.hash(refresh));

        JsonNode rotated = body(mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        java.time.OffsetDateTime accessExpiry = java.time.OffsetDateTime.parse(
                rotated.at("/data/accessExpiresAt").asText());
        java.time.OffsetDateTime refreshExpiry = java.time.OffsetDateTime.parse(
                rotated.at("/data/refreshExpiresAt").asText());
        assertFalse(accessExpiry.isAfter(refreshExpiry));
        assertTrue(refreshExpiry.isBefore(java.time.OffsetDateTime.now().plusMinutes(6)));
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

    @Test
    void staffCorsAllowsOnlyTheStaffOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/staff/cases")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/staff/cases")
                        .header("Origin", "http://localhost:4173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4173"));
    }

    private JsonNode body(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private JsonNode login() throws Exception {
        return body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
