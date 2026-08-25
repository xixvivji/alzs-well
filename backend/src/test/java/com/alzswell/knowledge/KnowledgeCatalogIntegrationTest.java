package com.alzswell.knowledge;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class KnowledgeCatalogIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test @WithMockUser(authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_READ","KNOWLEDGE_SEARCH","GUIDANCE_CANDIDATE_READ"})
    void providesApprovedEffectiveCitationsAndDeterministicGuidance() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents").param("audience","STAFF").param("asOf","2026-08-14"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mockMvc.perform(get("/api/v1/knowledge/documents/DOC-FSC-SAFE-BLOCK-001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvedForCitation").value(true))
                .andExpect(jsonPath("$.data.contentChecksum").value(org.hamcrest.Matchers.startsWith("sha256:")));
        mockMvc.perform(get("/api/v1/knowledge/documents/DOC-FSC-SAFE-BLOCK-001/versions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/knowledge/passages/95000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.citationLabel").isNotEmpty());
        mockMvc.perform(post("/api/v1/knowledge/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"안심차단 금융회사\",\"asOf\":\"2026-08-14\",\"audience\":\"STAFF\",\"limit\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].retrievalMode").value("DETERMINISTIC_KEYWORD"))
                .andExpect(jsonPath("$.data.externalModelCalled").value(false))
                .andExpect(jsonPath("$.data.vectorSearchUsed").value(false));
        mockMvc.perform(get("/api/v1/guidance-candidates").param("reasonCode","DUPLICATE_TRANSFER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].externalExecutionCreated").value(false));
        Integer accessEvents=jdbc.queryForObject("select count(*) from knowledge_access_audit_event",Integer.class);
        org.assertj.core.api.Assertions.assertThat(accessEvents).isGreaterThanOrEqualTo(5);
        String queryHash=jdbc.queryForObject("select query_hash from knowledge_access_audit_event where event_type='SEARCH' order by occurred_at desc limit 1",String.class);
        org.assertj.core.api.Assertions.assertThat(queryHash).hasSize(64).doesNotContain("안심차단");
    }

    @Test @WithMockUser(authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_READ"})
    void appliesEffectiveDateAndHidesUnknownResources() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents").param("audience","STAFF").param("asOf","2020-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/knowledge/documents/UNKNOWN"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("KNOWLEDGE_DOCUMENT_NOT_FOUND"));
    }

    @Test @WithMockUser(authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_READ","KNOWLEDGE_SEARCH"})
    void clientAudienceCanOnlyNarrowTheRoleDerivedAudience() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents").param("audience","CUSTOMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(post("/api/v1/knowledge/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"안심차단 금융회사\",\"audience\":\"CUSTOMER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.asOf").exists());
    }

    @Test @WithMockUser(authorities={"ROLE_CUSTOMER","KNOWLEDGE_READ"})
    void documentRoleAclCannotBeBypassedWithAReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/knowledge/documents/DOC-FSC-SAFE-BLOCK-001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents")).andExpect(status().isUnauthorized());
    }
}
