package com.alzswell.casework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OperationalCaseIntegrationTest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetWorkflow() {
        jdbcTemplate.update("delete from operational_guidance_plan");
        jdbcTemplate.update("delete from operational_case_review_event");
        jdbcTemplate.update("delete from operational_protection_case");
        jdbcTemplate.update("delete from operational_alert_context_event");
        jdbcTemplate.update("delete from operational_alert_audit_event");
        jdbcTemplate.update("""
                update operational_alert
                   set state = 'AWAITING_CONTEXT', alert_version = 1, deferred_until = null,
                       updated_at = created_at
                """);
    }

    @Test
    void createsCaseFromBankReviewThenAssignsReviewsAndApprovesGuidance() throws Exception {
        UUID alertId = jdbcTemplate.queryForObject(
                "select alert_id from operational_alert order by alert_id limit 1", UUID.class);
        assertThat(alertId).isNotNull();
        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", alertId)
                        .with(user(CUSTOMER_ID).authorities(
                                new SimpleGrantedAuthority("ALERT_RESPOND"),
                                new SimpleGrantedAuthority("ALERT_READ")))
                        .header("Idempotency-Key", "case-create-context-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"responseCode\":\"NOT_SURE\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentState").value("BANK_REVIEW"));

        UUID caseId = jdbcTemplate.queryForObject(
                "select case_id from operational_protection_case where alert_id = ?", UUID.class, alertId);
        assertThat(caseId).isNotNull();

        mockMvc.perform(get("/api/v1/staff/cases").with(staff("STAFF_CASE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.items[0].taskStatus").value("PENDING"));
        mockMvc.perform(get("/api/v1/staff/cases/{caseId}", caseId).with(staff("STAFF_CASE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerResponseCode").value("NOT_SURE"));

        mockMvc.perform(put("/api/v1/staff/cases/{caseId}/assignment", caseId)
                        .with(staff("STAFF_CASE_ASSIGN")).contentType(APPLICATION_JSON)
                        .content("{\"assignedTeam\":\"SAFE_TEAM_01\",\"assignedTo\":\"STAFF_01\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        String reviewBody = "{\"actionCode\":\"START_REVIEW\",\"note\":\"합성 사건 검토 시작\",\"expectedVersion\":2}";
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/reviews", caseId)
                        .with(staff("STAFF_CASE_REVIEW")).header("Idempotency-Key", "case-review-0001")
                        .contentType(APPLICATION_JSON).content(reviewBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.version").value(3));
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/reviews", caseId)
                        .with(staff("STAFF_CASE_REVIEW")).header("Idempotency-Key", "case-review-0001")
                        .contentType(APPLICATION_JSON).content(reviewBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/guidance-plans", caseId)
                        .with(staff("STAFF_GUIDANCE_APPROVE")).contentType(APPLICATION_JSON)
                        .content("{\"selectedActionCodes\":[\"FDS_REVIEW\",\"BRANCH_CONSULTATION\"],\"expectedVersion\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caseVersion").value(4))
                .andExpect(jsonPath("$.data.delivered").value(false))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false));

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/reviews", caseId)
                        .with(staff("STAFF_CASE_REVIEW")).header("Idempotency-Key", "case-review-0002")
                        .contentType(APPLICATION_JSON)
                        .content("{\"actionCode\":\"COMPLETE_REVIEW\",\"note\":\"검토 기록 완료\",\"expectedVersion\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false));
    }

    @Test
    void rejectsUnauthenticatedAndUnauthorizedStaffAccess() throws Exception {
        mockMvc.perform(get("/api/v1/staff/cases")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff/cases").with(staff("ALERT_READ")))
                .andExpect(status().isForbidden());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
    staff(String authority) {
        return user("protection-staff").authorities(new SimpleGrantedAuthority(authority));
    }
}
