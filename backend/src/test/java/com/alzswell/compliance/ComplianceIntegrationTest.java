package com.alzswell.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@WithMockUser(username = "approved-auditor", authorities = {"AUDIT_READ_ALL", "COMPLIANCE_TRACE_READ"})
class ComplianceIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    UUID firstDecision;
    UUID secondDecision;

    @BeforeEach
    void seedDecisions() {
        jdbcTemplate.execute("truncate table audit_export_request_event, audit_export_request");
        firstDecision = insertDecision("AUDIT_TEST_FIRST", "sha256:audit-first", OffsetDateTime.now().plusMinutes(1));
        secondDecision = insertDecision("AUDIT_TEST_SECOND", "sha256:audit-second", OffsetDateTime.now().plusMinutes(2));
    }

    @Test
    @WithMockUser(username="approved-auditor",authorities="AUDIT_EXPORT_REQUEST")
    void createsIdempotentInternalExportRequestWithoutArtifactOrTransfer() throws Exception {
        String body="""
                {"from":"2026-01-01T00:00:00Z","to":"2026-02-01T00:00:00Z",
                 "sourceTypes":["DECISION","ALERT"],"purposeCode":"INTERNAL_AUDIT",
                 "approvalReference":"APPROVAL-2026-001"}
                """;
        MvcResult first=mockMvc.perform(post("/api/v1/audit/export-requests")
                        .header("Idempotency-Key","audit-export-001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.artifactCreated").value(false))
                .andExpect(jsonPath("$.data.downloadEnabled").value(false))
                .andExpect(jsonPath("$.data.externalTransferExecuted").value(false)).andReturn();
        String id=objectMapper.readTree(first.getResponse().getContentAsByteArray()).at("/data/requestId").asText();
        mockMvc.perform(post("/api/v1/audit/export-requests").header("Idempotency-Key","audit-export-001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.requestId").value(id));
        mockMvc.perform(post("/api/v1/audit/export-requests").header("Idempotency-Key","audit-export-001")
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("INTERNAL_AUDIT","REGULATORY_REVIEW")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUDIT_EXPORT_IDEMPOTENCY_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_export_request",Integer.class)).isEqualTo(1);
        UUID eventId=jdbcTemplate.queryForObject("select event_id from audit_export_request_event",UUID.class);
        assertThatThrownBy(()->jdbcTemplate.update("delete from audit_export_request_event where event_id=?",eventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void searchesPagesAndReadsImmutableAuditEvents() throws Exception {
        MvcResult firstPage = mockMvc.perform(get("/api/v1/audit/events")
                        .queryParam("sourceType", "DECISION")
                        .queryParam("eventType", "AUDIT_TEST_SECOND")
                        .queryParam("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sourceType").value("DECISION"))
                .andExpect(jsonPath("$.data.items[0].immutable").value(true))
                .andReturn();
        JsonNode body = objectMapper.readTree(firstPage.getResponse().getContentAsByteArray());
        String eventId = body.at("/data/items/0/eventId").asText();

        mockMvc.perform(get("/api/v1/audit/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceId").value(secondDecision.toString()))
                .andExpect(jsonPath("$.data.integrityHash").value("sha256:audit-second-" + secondDecision));

        MvcResult paged = mockMvc.perform(get("/api/v1/audit/events")
                        .queryParam("sourceType", "DECISION").queryParam("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(true)).andReturn();
        String cursor = objectMapper.readTree(paged.getResponse().getContentAsByteArray())
                .at("/data/nextCursor").asText();
        mockMvc.perform(get("/api/v1/audit/events")
                        .queryParam("sourceType", "DECISION").queryParam("limit", "1")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sourceId").value(firstDecision.toString()));
    }

    @Test
    void cursorPreservesPostgresMicrosecondsBetweenPages() throws Exception {
        UUID microFirst = insertDecision("AUDIT_MICRO_FIRST", "sha256:audit-micro-first",
                OffsetDateTime.parse("2099-08-21T00:00:00.123100Z"));
        UUID microSecond = insertDecision("AUDIT_MICRO_SECOND", "sha256:audit-micro-second",
                OffsetDateTime.parse("2099-08-21T00:00:00.123900Z"));
        MvcResult page = mockMvc.perform(get("/api/v1/audit/events")
                        .queryParam("sourceType", "DECISION").queryParam("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sourceId").value(microSecond.toString()))
                .andReturn();
        String cursor = objectMapper.readTree(page.getResponse().getContentAsByteArray())
                .at("/data/nextCursor").asText();
        mockMvc.perform(get("/api/v1/audit/events").queryParam("sourceType", "DECISION")
                        .queryParam("limit", "1").queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sourceId").value(microFirst.toString()));
    }

    @Test
    void returnsDecisionTraceAndPolicyProvenanceWithoutExternalCalls() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/decision-traces/{decisionId}", secondDecision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyVersion").value("context-policy-v1.0.0"))
                .andExpect(jsonPath("$.data.algorithmVersion").value("baseline-rules-v2.0.0"))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false));

        UUID policyId = UUID.fromString("34000000-0000-4000-8000-000000000001");
        mockMvc.perform(get("/api/v1/compliance/data-provenance/POLICY/{resourceId}", policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syntheticData").value(true))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false));
    }

    @Test
    @WithMockUser(username = "ordinary-staff", authorities = "STAFF_CASE_READ")
    void doesNotGrantBroadAuditPermissionToExistingStaffRoles() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events")).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from auth_role_permission
                 where permission_code in ('AUDIT_READ_ALL','COMPLIANCE_TRACE_READ')
                """, Integer.class)).isZero();
    }

    private UUID insertDecision(String eventType, String hash, OffsetDateTime occurredAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into decision_audit (
                    audit_id,trace_id,event_type,actor_type,actor_id,policy_version,algorithm_version,
                    schema_version,event_payload,occurred_at,target_type,target_id,evidence_hash,event_hash
                ) values (?,?,?,'STAFF','approved-auditor','context-policy-v1.0.0','baseline-rules-v2.0.0',
                          '36','{}'::jsonb,?,'DETECTION_RUN',?, 'sha256:evidence',?)
                """, id, "audit-test-" + id, eventType, occurredAt, id.toString(), hash + "-" + id);
        return id;
    }
}
