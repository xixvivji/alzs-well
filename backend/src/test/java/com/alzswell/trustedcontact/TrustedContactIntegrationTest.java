package com.alzswell.trustedcontact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.common.security.AuditActor;
import com.alzswell.trustedcontact.api.TrustedContactRequests.CreateCommand;
import com.alzswell.trustedcontact.api.TrustedContactResponses.Contact;
import com.alzswell.trustedcontact.application.TrustedContactService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class TrustedContactIntegrationTest {
    private static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
    private static final String VALID_MASK="010-****-1234";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TrustedContactService trustedContactService;

    @BeforeEach
    void resetTrustedContacts() {
        jdbcTemplate.execute("""
                truncate table consent_access_audit_event, trusted_contact_event,
                    trusted_contact_scope, trusted_contact, customer_consent_event,
                    customer_consent_scope, customer_consent
                """);
    }

    @Test
    @WithMockUser(username=CUSTOMER,authorities={
            "CONSENT_WRITE","TRUSTED_CONTACT_READ","TRUSTED_CONTACT_WRITE"})
    void managesDesignationWithoutAuthorityOrExternalContact()throws Exception{
        UUID consentId=createConsent("trusted-consent-001");
        MvcResult created=createContact(consentId,"trusted-contact-001","가족 1",VALID_MASK);
        JsonNode data=mapper.readTree(created.getResponse().getContentAsByteArray()).path("data");
        String id=data.path("contactId").asText();
        long version=data.path("version").asLong();

        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts",CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.externalContactExecuted").value(false));
        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts/{id}",CUSTOMER,id))
                .andExpect(status().isOk());

        MvcResult updated=mockMvc.perform(patch(
                        "/api/v1/customers/{customer}/trusted-contacts/{id}",CUSTOMER,id)
                        .header("Idempotency-Key","trusted-contact-update-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":"+version
                                +",\"scopes\":[\"CONTACT_REQUEST_STATUS\"],"
                                +"\"expiresAt\":\"2098-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopes[0]").value("CONTACT_REQUEST_STATUS"))
                .andReturn();
        long next=mapper.readTree(updated.getResponse().getContentAsByteArray())
                .at("/data/version").asLong();
        mockMvc.perform(post("/api/v1/customers/{customer}/trusted-contacts/{id}/revoke",CUSTOMER,id)
                        .header("Idempotency-Key","trusted-contact-revoke-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":"+next+",\"reason\":\"고객 철회\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    @WithMockUser(username=CUSTOMER,authorities={"CONSENT_WRITE","TRUSTED_CONTACT_WRITE"})
    void replaysSameIdempotencyKeyAndRejectsDifferentContactRequest() throws Exception {
        UUID consentId=createConsent("trusted-replay-consent-001");
        MvcResult first=createContact(consentId,"trusted-contact-replay-001","가족 1",VALID_MASK);
        String contactId=mapper.readTree(first.getResponse().getContentAsByteArray())
                .at("/data/contactId").asText();

        MvcResult replay=createContact(
                consentId,"trusted-contact-replay-001","가족 1",VALID_MASK);
        assertThat(mapper.readTree(replay.getResponse().getContentAsByteArray())
                .at("/data/contactId").asText()).isEqualTo(contactId);
        mockMvc.perform(post("/api/v1/customers/{id}/trusted-contacts",CUSTOMER)
                        .header("Idempotency-Key","trusted-contact-replay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody(consentId,"가족 2",VALID_MASK)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRUSTED_CONTACT_IDEMPOTENCY_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from trusted_contact where customer_id=?",Integer.class,CUSTOMER))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from trusted_contact_event where contact_id=?",Integer.class,
                UUID.fromString(contactId))).isEqualTo(1);
    }

    @Test
    @WithMockUser(username=CUSTOMER,authorities={"CONSENT_WRITE","TRUSTED_CONTACT_WRITE"})
    void rejectsFullPhoneNumberWithAnAppendedAsteriskAtApiAndDatabase() throws Exception {
        UUID consentId=createConsent("trusted-mask-consent-001");
        mockMvc.perform(post("/api/v1/customers/{id}/trusted-contacts",CUSTOMER)
                        .header("Idempotency-Key","trusted-mask-invalid-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody(consentId,"가족 1","010-1234-5678*")))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("select count(*) from trusted_contact",Integer.class))
                .isZero();

        MvcResult valid=createContact(consentId,"trusted-mask-valid-001","가족 1",VALID_MASK);
        UUID contactId=UUID.fromString(mapper.readTree(valid.getResponse().getContentAsByteArray())
                .at("/data/contactId").asText());
        assertThatThrownBy(()->jdbcTemplate.update(
                "update trusted_contact set masked_contact=? where contact_id=?",
                "010-1234-5678*",contactId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @WithMockUser(username=CUSTOMER,authorities={
            "CONSENT_WRITE","TRUSTED_CONTACT_READ","TRUSTED_CONTACT_WRITE"})
    void withdrawingConsentRevokesItsActiveTrustedContacts() throws Exception {
        UUID consentId=createConsent("trusted-cascade-consent-001");
        MvcResult created=createContact(consentId,"trusted-cascade-contact-001","가족 1",VALID_MASK);
        JsonNode createdData=mapper.readTree(created.getResponse().getContentAsByteArray()).path("data");
        UUID contactId=UUID.fromString(createdData.path("contactId").asText());
        long contactVersion=createdData.path("version").asLong();

        mockMvc.perform(post("/api/v1/customers/{customer}/consents/{consentId}/withdraw",
                        CUSTOMER,consentId)
                        .header("Idempotency-Key","trusted-consent-withdraw-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\"신뢰연락인 동의 철회\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts/{contactId}",
                        CUSTOMER,contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED_BY_CONSENT"))
                .andExpect(jsonPath("$.data.version").value(contactVersion+1));
        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts",CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        Map<String,Object> row=jdbcTemplate.queryForMap("""
                select status,revocation_reason,row_version,revoked_at is not null as revoked
                  from trusted_contact where contact_id=?
                """,contactId);
        assertThat(row.get("status")).isEqualTo("REVOKED_BY_CONSENT");
        assertThat(row.get("revocation_reason")).isEqualTo("CONSENT_WITHDRAWN");
        assertThat(((Number)row.get("row_version")).longValue()).isEqualTo(contactVersion+1);
        assertThat(row.get("revoked")).isEqualTo(true);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from trusted_contact_event
                 where contact_id=? and event_type='REVOKED_BY_CONSENT'
                """,Integer.class,contactId)).isEqualTo(1);

        UUID eventId=jdbcTemplate.queryForObject(
                "select event_id from trusted_contact_event where contact_id=? limit 1",
                UUID.class,contactId);
        assertThatThrownBy(()->jdbcTemplate.update(
                "update trusted_contact_event set occurred_at=occurred_at where event_id=?",eventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update(
                "delete from trusted_contact_event where event_id=?",eventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    @WithMockUser(username=CUSTOMER,authorities={
            "CONSENT_WRITE","TRUSTED_CONTACT_READ","TRUSTED_CONTACT_WRITE"})
    void auditsTrustedContactListAndDetailReads() throws Exception {
        UUID consentId=createConsent("trusted-read-consent-001");
        MvcResult created=createContact(consentId,"trusted-read-contact-001","가족 1",VALID_MASK);
        UUID contactId=UUID.fromString(mapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/contactId").asText());

        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts",CUSTOMER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/{customer}/trusted-contacts/{contactId}",
                        CUSTOMER,contactId))
                .andExpect(status().isOk());

        List<Map<String,Object>> audits=jdbcTemplate.queryForList("""
                select event_type,actor_customer_id,actor_type,decision,request_hash,
                       detail->>'contactId' as contact_id
                  from consent_access_audit_event
                 where customer_id=?
                 order by occurred_at,evaluation_id
                """,CUSTOMER);
        assertThat(audits).hasSize(2);
        assertThat(audits).extracting(row->row.get("event_type"))
                .containsExactlyInAnyOrder(
                        "TRUSTED_CONTACT_LIST_READ","TRUSTED_CONTACT_DETAIL_READ");
        assertThat(audits).allSatisfy(row->{
            assertThat(row.get("actor_customer_id")).isEqualTo(CUSTOMER);
            assertThat(row.get("actor_type")).isEqualTo("CUSTOMER");
            assertThat(row.get("decision")).isEqualTo("READ");
            assertThat(row.get("request_hash").toString()).hasSize(64);
        });
        assertThat(audits).anySatisfy(row->{
            assertThat(row.get("event_type")).isEqualTo("TRUSTED_CONTACT_DETAIL_READ");
            assertThat(row.get("contact_id")).isEqualTo(contactId.toString());
        });
    }

    @Test
    @WithMockUser(username=CUSTOMER,authorities="CONSENT_WRITE")
    void concurrentSameIdempotencyKeyCreatesExactlyOneTrustedContact() throws Exception {
        UUID consentId=createConsent("trusted-concurrent-consent-001");
        CreateCommand command=new CreateCommand(consentId,"가족 1","FAMILY",VALID_MASK,
                List.of("ALERT_REASON_SUMMARY"),OffsetDateTime.parse("2099-01-01T00:00:00Z"));
        AuditActor actor=new AuditActor(null,CUSTOMER,null,"CUSTOMER");
        int callerCount=6;
        CountDownLatch ready=new CountDownLatch(callerCount);
        CountDownLatch start=new CountDownLatch(1);
        List<Future<Contact>> futures=new ArrayList<>();

        try(ExecutorService executor=Executors.newFixedThreadPool(callerCount)){
            for(int index=0;index<callerCount;index++){
                futures.add(executor.submit(()->{
                    ready.countDown();
                    if(!start.await(10,TimeUnit.SECONDS)){
                        throw new IllegalStateException("동시 시작 준비 시간 초과");
                    }
                    return trustedContactService.create(
                            CUSTOMER,command,"trusted-contact-concurrent-001",actor);
                }));
            }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<UUID> contactIds=new HashSet<>();
            for(Future<Contact> future:futures){
                contactIds.add(future.get(20,TimeUnit.SECONDS).contactId());
            }
            assertThat(contactIds).hasSize(1);
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from trusted_contact",Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from trusted_contact_event",Integer.class))
                .isEqualTo(1);
    }

    @Test
    @WithMockUser(username="OTHER",authorities="TRUSTED_CONTACT_READ")
    void preventsCrossCustomerRead()throws Exception{
        mockMvc.perform(get("/api/v1/customers/{id}/trusted-contacts",CUSTOMER))
                .andExpect(status().isForbidden());
    }

    private UUID createConsent(String idempotencyKey) throws Exception {
        MvcResult result=mockMvc.perform(post("/api/v1/customers/{id}/consents",CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key",idempotencyKey)
                        .content("""
                                {"purposeCode":"TRUSTED_CONTACT_DISCLOSURE",
                                 "scopes":["CONTACT_MINIMUM"],
                                 "expiresAt":"2099-12-31T00:00:00Z"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/consentId").asText());
    }

    private MvcResult createContact(UUID consentId,String idempotencyKey,String displayName,
            String maskedContact) throws Exception {
        return mockMvc.perform(post("/api/v1/customers/{id}/trusted-contacts",CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key",idempotencyKey)
                        .content(contactBody(consentId,displayName,maskedContact)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorizedToAct").value(false))
                .andExpect(jsonPath("$.data.recipientAccepted").value(false))
                .andExpect(jsonPath("$.data.acceptanceStatus").value("PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.data.externalContactEnabled").value(false))
                .andReturn();
    }

    private String contactBody(UUID consentId,String displayName,String maskedContact) {
        return ("{\"consentId\":\"%s\",\"displayName\":\"%s\","
                + "\"relationshipCode\":\"FAMILY\",\"maskedContact\":\"%s\","
                + "\"scopes\":[\"ALERT_REASON_SUMMARY\"],"
                + "\"expiresAt\":\"2099-01-01T00:00:00Z\"}")
                .formatted(consentId,displayName,maskedContact);
    }
}
