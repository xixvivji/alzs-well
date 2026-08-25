package com.alzswell.knowledge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class KnowledgeGovernanceIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private static final String REGISTER="""
        {"documentId":"DOC-TEST-GOV-001","versionLabel":"2026-08","title":"합성 검토 문서",
         "issuer":"안심은행","sourceType":"SYNTHETIC_FIXTURE",
         "sourcePath":"contracts/knowledge/fixtures/synthetic-source.html","sourceUrl":null,
         "sourceHash":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
         "sourceTransformations":[],"documentType":"SYNTHETIC_FIXTURE","classification":"INTERNAL",
         "audience":"STAFF","allowedRoles":["DETECTION_ADMIN","PROTECTION_STAFF"],
         "effectiveFrom":"2026-08-01","effectiveTo":null,"checkedAt":"2026-08-24",
         "usageRights":"SYNTHETIC_UNRESTRICTED","supersedesDocumentId":null,"supersedesVersionLabel":null}
        """;

    @Test @Transactional @WithMockUser(username="knowledge-admin",authorities={"KNOWLEDGE_ADMIN_WRITE","AUDIT_READ_ALL"})
    void registersThenPublishesOnlyAfterExplicitApproval() throws Exception {
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v55-00000001").content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_DOCUMENT_REGISTERED_FOR_REVIEW"))
                .andExpect(jsonPath("$.data.approvalStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.data.ingestionReady").value(false))
                .andExpect(jsonPath("$.data.searchable").value(false))
                .andExpect(jsonPath("$.data.externalCallExecuted").value(false));

        mockMvc.perform(post("/api/v1/admin/knowledge/documents/DOC-TEST-GOV-001/publish")
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key","idem-v55-00000002")
                        .content("{\"versionLabel\":\"2026-08\",\"expectedVersion\":1,\"approvalReference\":\"REVIEW-2026-001\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("KNOWLEDGE_DOCUMENT_PUBLISHED"))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.ingestionReady").value(true))
                .andExpect(jsonPath("$.data.searchable").value(false));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from knowledge_governance_event where document_id='DOC-TEST-GOV-001'",Integer.class)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/audit/events").param("sourceType","KNOWLEDGE_GOVERNANCE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.items[0].targetType").value("KNOWLEDGE_DOCUMENT"));
        assertThatThrownBy(()->jdbc.update("update knowledge_governance_event set event_type='PUBLISHED' where document_id='DOC-TEST-GOV-001'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update knowledge_document_governance set title='변조' where document_id='DOC-TEST-GOV-001'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test @Transactional @WithMockUser(username="knowledge-admin",authorities="KNOWLEDGE_ADMIN_WRITE")
    void rejectsIdempotencyConflictAndUnsafeRepositoryPath() throws Exception {
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v55-00000003").content(REGISTER))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v55-00000003")
                        .content(REGISTER.replace("합성 검토 문서","다른 검토 문서")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KNOWLEDGE_GOVERNANCE_IDEMPOTENCY_CONFLICT"));
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v55-00000004")
                        .content(REGISTER.replace("contracts/knowledge/fixtures/synthetic-source.html","../secret.txt")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresKnowledgeAdminPermission() throws Exception {
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v55-00000005").content(REGISTER))
                .andExpect(status().isUnauthorized());
    }
}
