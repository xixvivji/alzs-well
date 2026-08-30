package com.alzswell.casework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class AppealOverrideIntegrationTest {
 private static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
 private static final UUID STAFF=UUID.fromString("91000000-0000-0000-0000-000000000098");
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
 @Autowired MockMvc mockMvc; @Autowired ObjectMapper objectMapper; @Autowired JdbcTemplate jdbc;

 @BeforeEach void reset(){
  jdbc.execute("""
   truncate table operational_case_override_event,operational_alert_appeal,
    staff_access_decision_audit_event,staff_access_grant_event,staff_access_grant,
    operational_case_follow_up_event,operational_case_follow_up,operational_case_note,
    operational_case_activity,operational_case_review_event,operational_guidance_plan,
    operational_protection_case,operational_alert_context_event,operational_alert_audit_event
   """);
  jdbc.update("update operational_alert set state='AWAITING_CONTEXT',alert_version=1,deferred_until=null,updated_at=created_at");
 }

 @Test void customerAppealCreatesHumanReviewCaseAndReplays() throws Exception {
  UUID alert=alertId();
  String body="{\"reasonCode\":\"REQUEST_HUMAN_REVIEW\",\"statement\":\"사람의 재검토를 요청합니다.\",\"expectedVersion\":1}";
  var customer=user(CUSTOMER).authorities(new SimpleGrantedAuthority("ALERT_APPEAL"));
  mockMvc.perform(post("/api/v1/alerts/{id}/appeals",alert).with(customer).header("Idempotency-Key","alert-appeal-0001").contentType(APPLICATION_JSON).content(body))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.data.currentState").value("BANK_REVIEW")).andExpect(jsonPath("$.data.alertVersion").value(2)).andExpect(jsonPath("$.data.financialActionExecuted").value(false));
  mockMvc.perform(post("/api/v1/alerts/{id}/appeals",alert).with(customer).header("Idempotency-Key","alert-appeal-0001").contentType(APPLICATION_JSON).content(body))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.data.idempotencyReplayed").value(true));
  mockMvc.perform(post("/api/v1/alerts/{id}/appeals",alert).with(customer).header("Idempotency-Key","alert-appeal-0001").contentType(APPLICATION_JSON)
   .content("{\"reasonCode\":\"MISSING_CONTEXT\",\"statement\":\"다른 요청\",\"expectedVersion\":1}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALERT_APPEAL_IDEMPOTENCY_CONFLICT"));
  assertThat(jdbc.queryForObject("select count(*) from operational_alert_appeal where alert_id=?",Integer.class,alert)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from operational_protection_case where alert_id=?",Integer.class,alert)).isEqualTo(1);
  UUID appealId=jdbc.queryForObject("select appeal_id from operational_alert_appeal where alert_id=?",UUID.class,alert);
  assertThatThrownBy(()->jdbc.update("delete from operational_alert_appeal where appeal_id=?",appealId)).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
 }

 @Test void appealRejectsSensitiveTextAndAnotherCustomer() throws Exception {
  UUID alert=alertId();
  mockMvc.perform(post("/api/v1/alerts/{id}/appeals",alert).with(user(CUSTOMER).authorities(new SimpleGrantedAuthority("ALERT_APPEAL")))
   .header("Idempotency-Key","alert-appeal-sensitive-1").contentType(APPLICATION_JSON)
   .content("{\"reasonCode\":\"MISSING_CONTEXT\",\"statement\":\"계좌번호 123456789012 확인\",\"expectedVersion\":1}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  mockMvc.perform(post("/api/v1/alerts/{id}/appeals",alert).with(user("OTHER").authorities(new SimpleGrantedAuthority("ALERT_APPEAL")))
   .header("Idempotency-Key","alert-appeal-other-001").contentType(APPLICATION_JSON)
   .content("{\"reasonCode\":\"MISSING_CONTEXT\",\"statement\":\"재검토 요청\",\"expectedVersion\":1}"))
   .andExpect(status().isNotFound());
  assertThat(jdbc.queryForObject("select count(*) from operational_alert_appeal",Integer.class)).isZero();
 }

 @Test void assignedStaffCanRecordStructuredOverrideWithoutAction() throws Exception {
  String token=loginStaff(); UUID alert=alertId();
  mockMvc.perform(post("/api/v1/alerts/{id}/appeals",alert).with(user(CUSTOMER).authorities(new SimpleGrantedAuthority("ALERT_APPEAL")))
   .header("Idempotency-Key","override-case-appeal-1").contentType(APPLICATION_JSON)
   .content("{\"reasonCode\":\"REQUEST_HUMAN_REVIEW\",\"statement\":\"재검토 요청\",\"expectedVersion\":1}"))
   .andExpect(status().isCreated());
  UUID caseId=jdbc.queryForObject("select case_id from operational_protection_case where alert_id=?",UUID.class,alert);
  mockMvc.perform(put("/api/v1/staff/cases/{id}/assignment",caseId).header("Authorization","Bearer "+token).header("Idempotency-Key","override-assign-001").contentType(APPLICATION_JSON)
   .content("{\"assignedTeam\":\"SAFE_TEAM_01\",\"assignedTo\":\""+STAFF+"\",\"expectedVersion\":1}"))
   .andExpect(status().isOk());
  mockMvc.perform(post("/api/v1/staff/cases/{id}/reviews",caseId).header("Authorization","Bearer "+token).header("Idempotency-Key","override-review-001").contentType(APPLICATION_JSON)
   .content("{\"actionCode\":\"START_REVIEW\",\"note\":\"검토 시작\",\"expectedVersion\":2}"))
   .andExpect(status().isOk());
  mockMvc.perform(post("/api/v1/staff/cases/{id}/guidance-plans",caseId).header("Authorization","Bearer "+token).header("Idempotency-Key","override-guidance-1").contentType(APPLICATION_JSON)
   .content("{\"selectedActionCodes\":[\"BRANCH_CONSULTATION\"],\"expectedVersion\":3}"))
   .andExpect(status().isCreated());
  String body="{\"reasonCode\":\"MISSING_CONTEXT_REVIEW\",\"rationale\":\"추가 생활맥락 확인이 필요합니다.\",\"expectedVersion\":4}";
  mockMvc.perform(post("/api/v1/staff/cases/{id}/overrides",caseId).header("Authorization","Bearer "+token).header("Idempotency-Key","case-override-0001").contentType(APPLICATION_JSON).content(body))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.data.currentStatus").value("IN_REVIEW")).andExpect(jsonPath("$.data.version").value(5)).andExpect(jsonPath("$.data.financialActionExecuted").value(false));
  mockMvc.perform(post("/api/v1/staff/cases/{id}/overrides",caseId).header("Authorization","Bearer "+token).header("Idempotency-Key","case-override-0001").contentType(APPLICATION_JSON).content(body))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.data.idempotencyReplayed").value(true));
  mockMvc.perform(post("/api/v1/staff/cases/{id}/overrides",caseId).header("Authorization","Bearer "+token).header("Idempotency-Key","case-override-0001").contentType(APPLICATION_JSON)
   .content("{\"reasonCode\":\"FALSE_POSITIVE_REVIEW\",\"rationale\":\"다른 재검토 요청\",\"expectedVersion\":4}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STAFF_CASE_OVERRIDE_IDEMPOTENCY_CONFLICT"));
  mockMvc.perform(post("/api/v1/staff/cases/{id}/overrides",caseId).header("Authorization","Bearer "+token).header("Idempotency-Key","case-override-sensitive-1").contentType(APPLICATION_JSON)
   .content("{\"reasonCode\":\"POLICY_EXCEPTION_REVIEW\",\"rationale\":\"계좌번호 123456789012 확인\",\"expectedVersion\":5}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  UUID eventId=jdbc.queryForObject("select override_event_id from operational_case_override_event where case_id=?",UUID.class,caseId);
  assertThatThrownBy(()->jdbc.update("update operational_case_override_event set created_at=created_at where override_event_id=?",eventId)).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
 }

 private UUID alertId(){return jdbc.queryForObject("select alert_id from operational_alert where customer_id=? order by alert_id limit 1",UUID.class,CUSTOMER);}
 private String loginStaff() throws Exception {
  jdbc.update("""
   insert into auth_principal(principal_id,login_id,customer_id,display_name,password_hash,status,created_at,updated_at)
   select ?,'synthetic-override-staff',customer_id,'합성 재검토 담당자',password_hash,'ACTIVE',now(),now()
   from auth_principal where login_id='synthetic-customer'
   on conflict(principal_id) do update set status='ACTIVE',password_hash=excluded.password_hash,updated_at=now()
   """,STAFF);
  jdbc.update("insert into auth_principal_role(principal_id,role_code) values(?,'PROTECTION_STAFF') on conflict do nothing",STAFF);
  jdbc.update("""
   insert into staff_access_grant(grant_id,staff_principal_id,customer_id,purpose_code,scopes,status,granted_by,granted_at,expires_at,idempotency_key_hash,request_hash,row_version)
   values(?,?,?,'PROTECTION_CASE_MANAGEMENT',array['CASE_READ','CASE_ASSIGN','CASE_REVIEW','CASE_GUIDANCE','CASE_OVERRIDE'],'ACTIVE',?,now(),now()+interval '1 day',repeat('c',64),repeat('d',64),1)
   """,UUID.randomUUID(),STAFF,CUSTOMER,STAFF);
  String response=mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("{\"loginId\":\"synthetic-override-staff\",\"password\":\"local-synthetic-customer-password\"}"))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  return objectMapper.readTree(response).at("/data/accessToken").asText();
 }
}
