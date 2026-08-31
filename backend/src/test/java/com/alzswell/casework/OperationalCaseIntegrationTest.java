package com.alzswell.casework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
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
    private static final UUID STAFF_PRINCIPAL_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000099");
    private static final UUID OTHER_STAFF_PRINCIPAL_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000098");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetWorkflow() {
        jdbcTemplate.execute("""
                truncate table operational_case_override_event, operational_alert_appeal,
                    staff_access_decision_audit_event, staff_access_grant_event, staff_access_grant,
                    operational_case_follow_up_event, operational_case_follow_up,
                    operational_case_note, operational_case_activity,
                    operational_case_review_event, operational_guidance_plan,
                    operational_protection_case
                """);
        jdbcTemplate.update("truncate operational_alert_context_event, operational_alert_audit_event");
        jdbcTemplate.update("""
                update operational_alert
                   set state = 'AWAITING_CONTEXT', alert_version = 1, deferred_until = null,
                       updated_at = created_at
                """);
    }

    @Test
    void createsCaseFromBankReviewThenAssignsReviewsAndApprovesGuidance() throws Exception {
        String staffAccessToken = loginProtectionStaff();
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

        mockMvc.perform(get("/api/v1/staff/cases").header("Authorization", "Bearer " + staffAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.items[0].taskStatus").value("PENDING"));
        mockMvc.perform(get("/api/v1/staff/cases/{caseId}", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerResponseCode").value("NOT_SURE"));
        mockMvc.perform(get("/api/v1/staff/cases/{caseId}/evidence", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].evidenceId").exists())
                .andExpect(jsonPath("$.data.syntheticData").value(true));

        mockMvc.perform(put("/api/v1/staff/cases/{caseId}/assignment", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken).contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", "case-assignment-0001")
                        .content("{\"assignedTeam\":\"SAFE_TEAM_01\",\"assignedTo\":\""
                                + STAFF_PRINCIPAL_ID + "\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));
        mockMvc.perform(put("/api/v1/staff/cases/{caseId}/assignment", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-assignment-0001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"assignedTeam\":\"SAFE_TEAM_01\",\"assignedTo\":\""
                                + STAFF_PRINCIPAL_ID + "\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/notes", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-note-sensitive-0001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"noteText\":\"계좌번호 123456789012를 확인함\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_case_note where case_id=?", Integer.class, caseId)).isZero();

        String noteBody = "{\"noteText\":\"고객 응답과 합성 근거를 확인했습니다.\"}";
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/notes", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-note-0001")
                        .contentType(APPLICATION_JSON).content(noteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(false));
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/notes", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-note-0001")
                        .contentType(APPLICATION_JSON).content(noteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));
        mockMvc.perform(get("/api/v1/staff/cases/{caseId}/notes", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        String reviewBody = "{\"actionCode\":\"START_REVIEW\",\"note\":\"합성 사건 검토 시작\",\"expectedVersion\":2}";
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/reviews", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-review-0001")
                        .contentType(APPLICATION_JSON).content(reviewBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.version").value(3));
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/reviews", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-review-0001")
                        .contentType(APPLICATION_JSON).content(reviewBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));

        String scheduledAt = OffsetDateTime.now().plusDays(2).toString();
        String followUpBody = "{\"followUpType\":\"CUSTOMER_RECHECK\",\"scheduledAt\":\""
                + scheduledAt + "\",\"purpose\":\"고객 상태 내부 재확인\",\"expectedCaseVersion\":3}";
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/follow-ups", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-follow-up-0001")
                        .contentType(APPLICATION_JSON).content(followUpBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.externalContactExecuted").value(false));
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/follow-ups", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-follow-up-0001")
                        .contentType(APPLICATION_JSON).content(followUpBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));
        UUID followUpId = jdbcTemplate.queryForObject(
                "select follow_up_id from operational_case_follow_up where case_id = ?", UUID.class, caseId);
        mockMvc.perform(patch("/api/v1/staff/follow-ups/{followUpId}", followUpId)
                        .header("Authorization", "Bearer " + staffAccessToken).contentType(APPLICATION_JSON)
                        .content("{\"actionCode\":\"COMPLETE\",\"outcome\":\"내부 재확인 완료\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mockMvc.perform(get("/api/v1/staff/cases/{caseId}/follow-ups", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .queryParam("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/guidance-plans", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-guidance-0001").contentType(APPLICATION_JSON)
                        .content("{\"selectedActionCodes\":[\"FDS_REVIEW\",\"BRANCH_CONSULTATION\"],\"expectedVersion\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caseVersion").value(5))
                .andExpect(jsonPath("$.data.delivered").value(false))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false));
        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/guidance-plans", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-guidance-0001").contentType(APPLICATION_JSON)
                        .content("{\"selectedActionCodes\":[\"FDS_REVIEW\",\"BRANCH_CONSULTATION\"],\"expectedVersion\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caseVersion").value(5));

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/reviews", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken)
                        .header("Idempotency-Key", "case-review-0002")
                        .contentType(APPLICATION_JSON)
                        .content("{\"actionCode\":\"COMPLETE_REVIEW\",\"note\":\"검토 기록 완료\",\"expectedVersion\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false));

        mockMvc.perform(get("/api/v1/staff/cases/{caseId}/timeline", caseId)
                        .header("Authorization", "Bearer " + staffAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(9))
                .andExpect(jsonPath("$.data.items[0].eventType").exists());

        String expectedActor = STAFF_PRINCIPAL_ID.toString();
        assertThat(jdbcTemplate.queryForObject(
                "select actor_subject from operational_case_activity where case_id = ?",
                String.class, caseId)).isEqualTo(expectedActor);
        assertThat(jdbcTemplate.queryForList(
                "select reviewer_subject from operational_case_review_event where case_id = ?",
                String.class, caseId)).containsOnly(expectedActor);
        assertThat(jdbcTemplate.queryForObject(
                "select approved_by from operational_guidance_plan where case_id = ?",
                String.class, caseId)).isEqualTo(expectedActor);
        assertThat(jdbcTemplate.queryForObject(
                "select created_by from operational_case_note where case_id = ?",
                String.class, caseId)).isEqualTo(expectedActor);
        assertThat(jdbcTemplate.queryForObject(
                "select created_by from operational_case_follow_up where case_id = ?",
                String.class, caseId)).isEqualTo(expectedActor);
        assertThat(jdbcTemplate.queryForList(
                "select actor_subject from operational_case_follow_up_event where case_id = ?",
                String.class, caseId)).containsOnly(expectedActor);

        UUID reviewEventId=jdbcTemplate.queryForObject(
                "select review_event_id from operational_case_review_event where case_id=? limit 1",
                UUID.class,caseId);
        assertThatThrownBy(()->jdbcTemplate.update(
                "update operational_case_review_event set created_at=created_at where review_event_id=?",
                reviewEventId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update(
                "delete from operational_case_review_event where review_event_id=?",reviewEventId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void rejectsUnauthenticatedAndUnauthorizedStaffAccess() throws Exception {
        mockMvc.perform(get("/api/v1/staff/cases")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff/cases").with(staff("ALERT_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditsFinalDenialWhenGrantedStaffMutatesACaseAssignedToAnotherStaffMember() throws Exception {
        String assignedStaffToken = loginProtectionStaff();
        String otherStaffToken = loginProtectionStaff(
                OTHER_STAFF_PRINCIPAL_ID, "synthetic-protection-staff-other", "c", "d");
        UUID alertId = jdbcTemplate.queryForObject(
                "select alert_id from operational_alert order by alert_id limit 1", UUID.class);
        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", alertId)
                        .with(user(CUSTOMER_ID).authorities(
                                new SimpleGrantedAuthority("ALERT_RESPOND"),
                                new SimpleGrantedAuthority("ALERT_READ")))
                        .header("Idempotency-Key", "case-denial-context-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"responseCode\":\"NOT_SURE\",\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        UUID caseId = java.util.Objects.requireNonNull(jdbcTemplate.queryForObject(
                "select case_id from operational_protection_case where alert_id=?", UUID.class, alertId));
        mockMvc.perform(put("/api/v1/staff/cases/{caseId}/assignment", caseId)
                        .header("Authorization", "Bearer " + assignedStaffToken)
                        .header("Idempotency-Key", "case-denial-assignment-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"assignedTeam\":\"SAFE_TEAM_01\",\"assignedTo\":\""
                                + STAFF_PRINCIPAL_ID + "\",\"expectedVersion\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/staff/cases/{caseId}/notes", caseId)
                        .header("Authorization", "Bearer " + otherStaffToken)
                        .header("Idempotency-Key", "case-denial-note-001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"noteText\":\"다른 담당자가 변경을 시도한 합성 기록입니다.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from staff_access_decision_audit_event
                 where staff_principal_id=? and resource_type='CASE' and resource_id=?
                   and allowed=false and decision_code='DENY_CASE_NOT_ASSIGNED'
                """, Integer.class, OTHER_STAFF_PRINCIPAL_ID, caseId.toString())).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_case_note where case_id=?", Integer.class, caseId)).isZero();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
    staff(String authority) {
        return user("protection-staff").authorities(new SimpleGrantedAuthority(authority));
    }

    private String loginProtectionStaff() throws Exception {
        return loginProtectionStaff(
                STAFF_PRINCIPAL_ID, "synthetic-protection-staff", "a", "b");
    }

    private String loginProtectionStaff(
            UUID principalId, String loginId, String idempotencySeed, String requestSeed
    ) throws Exception {
        jdbcTemplate.update("""
                insert into auth_principal (
                    principal_id, login_id, customer_id, display_name, password_hash,
                    status, created_at, updated_at
                )
                select ?, ?, customer_id, '합성 보호업무 담당자',
                       password_hash, 'ACTIVE', now(), now()
                  from auth_principal where login_id = 'synthetic-customer'
                on conflict (principal_id) do update set status = 'ACTIVE',
                    password_hash = excluded.password_hash, updated_at = now()
                """, principalId, loginId);
        jdbcTemplate.update("""
                insert into auth_principal_role (principal_id, role_code)
                values (?, 'PROTECTION_STAFF') on conflict do nothing
                """, principalId);
        jdbcTemplate.update("""
                insert into staff_access_grant(grant_id,staff_principal_id,customer_id,purpose_code,scopes,
                    status,granted_by,granted_at,expires_at,idempotency_key_hash,request_hash,row_version)
                values(?,?,?,'PROTECTION_CASE_MANAGEMENT',array['CASE_READ','CASE_ASSIGN','CASE_REVIEW','CASE_GUIDANCE',
                    'CASE_NOTE','CASE_FOLLOW_UP'],'ACTIVE',?,now(),now()+interval '1 day',repeat(?,64),repeat(?,64),1)
                """, UUID.randomUUID(), principalId, CUSTOMER_ID, principalId, idempotencySeed, requestSeed);
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId
                                + "\",\"password\":\"local-synthetic-customer-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }
}
