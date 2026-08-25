package com.alzswell.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alzswell.common.security.AuditActor;
import com.alzswell.consent.api.ConsentRequests.GrantCommand;
import com.alzswell.consent.api.ConsentResponses.Consent;
import com.alzswell.consent.application.ConsentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
    @Autowired MockMvc mockMvc; @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate; @Autowired ConsentService consentService;

    @BeforeEach
    void resetConsents() {
        jdbcTemplate.execute("""
                truncate table consent_access_audit_event, trusted_contact_event,
                    trusted_contact_scope, trusted_contact, customer_consent_event,
                    customer_consent_scope, customer_consent
                """);
    }

    @Test @WithMockUser(username=CUSTOMER_ID,authorities={"CONSENT_READ","CONSENT_WRITE","DISCLOSURE_EVALUATE"})
    void grantsEvaluatesAuditsAndWithdrawsConsentWithoutExternalDisclosure()throws Exception{
        MvcResult created=mockMvc.perform(post("/api/v1/customers/{customerId}/consents",CUSTOMER_ID)
                        .header("Idempotency-Key","consent-test-001")
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
                        """.replace("%s",consentId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("ALLOW_MINIMUM_SCOPE"))
                .andExpect(jsonPath("$.data.externalDisclosureRequested").value(false))
                .andExpect(jsonPath("$.data.externalDisclosureCreated").value(false));
        mockMvc.perform(post("/api/v1/customers/{customerId}/consents/{consentId}/withdraw",CUSTOMER_ID,consentId)
                        .header("Idempotency-Key","consent-withdraw-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":"+version+",\"reason\":\"고객 요청\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.revocable").value(false));
        mockMvc.perform(get("/api/v1/customers/{customerId}/consents/{consentId}/history",CUSTOMER_ID,consentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[1].eventType").value("WITHDRAWN"));
        mockMvc.perform(get("/api/v1/customers/{customerId}/consents",CUSTOMER_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));

        var evaluationAudit=jdbcTemplate.queryForMap("""
                select actor_customer_id,actor_type,policy_version,decision,
                       detail->>'purposeCode' as purpose_code,
                       detail->'requestedScopes' as requested_scopes
                  from consent_access_audit_event
                 where consent_id=? and event_type='DISCLOSURE_EVALUATED'
                """,UUID.fromString(consentId));
        assertThat(evaluationAudit.get("actor_customer_id")).isEqualTo(CUSTOMER_ID);
        assertThat(evaluationAudit.get("actor_type")).isEqualTo("CUSTOMER");
        assertThat(evaluationAudit.get("policy_version")).isEqualTo("disclosure-policy-v1.1.0");
        assertThat(evaluationAudit.get("decision")).isEqualTo("ALLOW_MINIMUM_SCOPE");
        assertThat(evaluationAudit.get("purpose_code")).isEqualTo("PROTECTION_GUIDANCE");
        assertThat(evaluationAudit.get("requested_scopes").toString()).contains("PROTECTION_CASE");

        UUID consentEventId=jdbcTemplate.queryForObject(
                "select event_id from customer_consent_event where consent_id=? limit 1",
                UUID.class,UUID.fromString(consentId));
        UUID accessAuditId=jdbcTemplate.queryForObject(
                "select evaluation_id from consent_access_audit_event where consent_id=? limit 1",
                UUID.class,UUID.fromString(consentId));
        assertThatThrownBy(()->jdbcTemplate.update(
                "update customer_consent_event set occurred_at=occurred_at where event_id=?",
                consentEventId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update(
                "delete from customer_consent_event where event_id=?",consentEventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update(
                "update consent_access_audit_event set occurred_at=occurred_at where evaluation_id=?",
                accessAuditId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update(
                "delete from consent_access_audit_event where evaluation_id=?",accessAuditId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    @WithMockUser(username=CUSTOMER_ID,authorities="CONSENT_WRITE")
    void replaysSameIdempotencyKeyAndRejectsDifferentConsentRequest() throws Exception {
        String body="""
                {"purposeCode":"PROTECTION_GUIDANCE","scopes":["BASELINE_SIGNAL","PROTECTION_CASE"],
                 "expiresAt":"2099-12-31T00:00:00Z"}
                """;
        MvcResult first=mockMvc.perform(post("/api/v1/customers/{customerId}/consents",CUSTOMER_ID)
                        .header("Idempotency-Key","consent-replay-001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String consentId=objectMapper.readTree(first.getResponse().getContentAsByteArray())
                .at("/data/consentId").asText();

        mockMvc.perform(post("/api/v1/customers/{customerId}/consents",CUSTOMER_ID)
                        .header("Idempotency-Key","consent-replay-001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.consentId").value(consentId));

        mockMvc.perform(post("/api/v1/customers/{customerId}/consents",CUSTOMER_ID)
                        .header("Idempotency-Key","consent-replay-001")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"purposeCode":"PROTECTION_GUIDANCE","scopes":["BASELINE_SIGNAL"],
                                 "expiresAt":"2099-12-31T00:00:00Z"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_IDEMPOTENCY_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from customer_consent where customer_id=?",Integer.class,CUSTOMER_ID))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from customer_consent_event where consent_id=?",Integer.class,
                UUID.fromString(consentId))).isEqualTo(1);
    }

    @Test
    void concurrentSameIdempotencyKeyCreatesExactlyOneConsent() throws Exception {
        int callerCount=8;
        CountDownLatch ready=new CountDownLatch(callerCount);
        CountDownLatch start=new CountDownLatch(1);
        GrantCommand command=new GrantCommand("FINANCIAL_ANALYSIS",
                List.of("ACCOUNT_SUMMARY","TRANSACTION_SUMMARY"),
                OffsetDateTime.parse("2099-12-31T00:00:00Z"));
        AuditActor actor=new AuditActor(null,CUSTOMER_ID,null,"CUSTOMER");
        List<Future<Consent>> futures=new ArrayList<>();

        try(ExecutorService executor=Executors.newFixedThreadPool(callerCount)){
            for(int index=0;index<callerCount;index++){
                futures.add(executor.submit(()->{
                    ready.countDown();
                    if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("동시 시작 준비 시간 초과");
                    return consentService.grant(CUSTOMER_ID,command,"consent-concurrent-001",actor);
                }));
            }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<UUID> consentIds=new HashSet<>();
            for(Future<Consent> future:futures){
                consentIds.add(future.get(20,TimeUnit.SECONDS).consentId());
            }
            assertThat(consentIds).hasSize(1);
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from customer_consent where customer_id=?",Integer.class,CUSTOMER_ID))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from customer_consent_event",Integer.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(username=CUSTOMER_ID,authorities="CONSENT_WRITE")
    void rejectsScopeThatDoesNotBelongToTheConsentPurpose() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{customerId}/consents",CUSTOMER_ID)
                        .header("Idempotency-Key","consent-matrix-001")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"purposeCode":"PROTECTION_GUIDANCE","scopes":["ACCOUNT_SUMMARY"],
                                 "expiresAt":"2099-12-31T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_SCOPE_NOT_ALLOWED"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from customer_consent",Integer.class))
                .isZero();
    }

    @Test
    @WithMockUser(username=CUSTOMER_ID,authorities="CONSENT_WRITE")
    void databaseRejectsInvalidScopeAndConsentPurposeMutation() throws Exception {
        MvcResult created=mockMvc.perform(post("/api/v1/customers/{customerId}/consents",CUSTOMER_ID)
                        .header("Idempotency-Key","consent-db-matrix-001")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"purposeCode":"FINANCIAL_ANALYSIS","scopes":["ACCOUNT_SUMMARY"],
                                 "expiresAt":"2099-12-31T00:00:00Z"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        UUID consentId=UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/consentId").asText());

        assertThatThrownBy(()->jdbcTemplate.update(
                "insert into customer_consent_scope(consent_id,scope_code) values(?,'PROTECTION_CASE')",
                consentId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("is not allowed for purpose");
        assertThatThrownBy(()->jdbcTemplate.update(
                "update customer_consent set purpose_code='PROTECTION_GUIDANCE' where consent_id=?",
                consentId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("purpose_code is immutable");
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
