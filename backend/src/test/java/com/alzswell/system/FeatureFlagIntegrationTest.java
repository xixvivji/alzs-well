package com.alzswell.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
@WithMockUser(username = "flag-admin", authorities = {"FEATURE_FLAG_READ", "FEATURE_FLAG_WRITE"})
class FeatureFlagIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void recordsApprovedDesiredValueWithoutChangingRuntime() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feature-flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.items[0].externalActionExecuted").value(false));

        String body = """
                {"enabled":true,"expectedVersion":0,"approvalReference":"APPROVAL-2026-001",
                 "changeReason":"사설 통합 검증을 위한 승인된 변경입니다."}
                """;
        mockMvc.perform(put("/api/v1/admin/feature-flags/CUSTOMER_PROFILE_API_ENABLED")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.desiredEnabled").value(true))
                .andExpect(jsonPath("$.data.runtimeEnabled").value(false))
                .andExpect(jsonPath("$.data.appliedToRuntime").value(false))
                .andExpect(jsonPath("$.data.restartRequired").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false));

        mockMvc.perform(put("/api/v1/admin/feature-flags/CUSTOMER_PROFILE_API_ENABLED")
                        .contentType(APPLICATION_JSON).content(body.replace("\"expectedVersion\":0", "\"expectedVersion\":1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from feature_flag_change_event where flag_key='CUSTOMER_PROFILE_API_ENABLED'",
                Integer.class)).isEqualTo(1);

        UUID eventId = jdbcTemplate.queryForObject(
                "select event_id from feature_flag_change_event where flag_key='CUSTOMER_PROFILE_API_ENABLED'",
                UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update feature_flag_change_event set change_reason=change_reason where event_id=?", eventId))
                .hasMessageContaining("append-only");
    }

    @Test
    void rejectsImmutableGuardrailAndStaleVersion() throws Exception {
        mockMvc.perform(put("/api/v1/admin/feature-flags/EXTERNAL_EGRESS_ENABLED")
                        .contentType(APPLICATION_JSON).content("""
                        {"enabled":true,"expectedVersion":0,"approvalReference":"APPROVAL-2026-002",
                         "changeReason":"외부 송신을 요청하지만 안전상 거부되어야 합니다."}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_FLAG_IMMUTABLE"));
        mockMvc.perform(put("/api/v1/admin/feature-flags/LOCAL_AUTH_API_ENABLED")
                        .contentType(APPLICATION_JSON).content("""
                        {"enabled":true,"expectedVersion":99,"approvalReference":"APPROVAL-2026-003",
                         "changeReason":"오래된 버전 요청은 충돌해야 합니다."}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_FLAG_VERSION_CONFLICT"));
    }

    @Test
    @WithMockUser(username = "customer", authorities = "DETECTION_READ")
    void requiresFeatureFlagPermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feature-flags")).andExpect(status().isForbidden());
    }
}
