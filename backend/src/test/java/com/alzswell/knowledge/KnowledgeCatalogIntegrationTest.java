package com.alzswell.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alzswell.knowledge.application.AiCitationValidator;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.RetrievalQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
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
    @Autowired AiCitationValidator citationValidator;
    @Autowired ObjectMapper objectMapper;

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

    @Test
    void revalidatesAiCitationAgainstSpringGovernanceAndPassage() throws Exception {
        String sourceHash="sha256:"+"a".repeat(64);
        jdbc.update("""
                insert into knowledge_document_governance(
                  workflow_id,document_id,version_label,title,issuer,source_type,source_path,source_url,source_hash,
                  source_transformations,document_type,classification,audience,allowed_roles,effective_from,effective_to,
                  checked_at,usage_rights,approval_status,lifecycle_status,approved_by,approved_at,row_version,
                  registered_by,registered_at,updated_at
                ) values (?,?,?,?,?,'OFFICIAL_EXTERNAL',?,?,?,cast(? as jsonb),'PUBLIC_GUIDE','PUBLIC_OFFICIAL','BOTH',
                  string_to_array(?,',')::varchar[],?,null,?,'PUBLIC_REUSE_ALLOWED','APPROVED','ACTIVE',?,?,1,?,?,?)
                """,UUID.randomUUID(),"DOC-FSC-SAFE-BLOCK-001","2026-08","금융거래 안심차단 안내 근거","금융위원회",
                "knowledge/official-source/test.html","https://www.fsc.go.kr/no010101/85644",sourceHash,"[]",
                "PROTECTION_STAFF,DETECTION_ADMIN",LocalDate.of(2024,8,23),LocalDate.of(2026,8,14),
                "reviewer",java.time.OffsetDateTime.parse("2026-08-14T00:00:00Z"),"reviewer",
                java.time.OffsetDateTime.parse("2026-08-14T00:00:00Z"),java.time.OffsetDateTime.parse("2026-08-14T00:00:00Z"));
        String content="안심차단 신청 가능 여부와 세부 범위는 해당 금융회사에서 최종 확인해야 합니다.";
        String textHash=hash(content);
        List<String> sectionPath=List.of("신청 전 확인");
        String chunkId="chk_"+digest(objectMapper.writeValueAsBytes(List.of("DOC-FSC-SAFE-BLOCK-001","2026-08",
                sectionPath,1,textHash,"structure-ko-v1")));
        AiCitation citation=new AiCitation("1.0.0","DOC-FSC-SAFE-BLOCK-001","2026-08",chunkId,1,
                "금융거래 안심차단 안내 근거","금융위원회","신청 전 확인",sectionPath,null,
                "금융거래 안심차단 안내 근거 > 신청 전 확인","https://www.fsc.go.kr/no010101/85644",sourceHash,textHash,
                LocalDate.of(2026,8,14),"KEYWORD","keyword-simple-v1");
        RetrievalQuery query=new RetrievalQuery("안심차단 금융회사",LocalDate.of(2026,8,14),"STAFF",
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);

        assertThat(citationValidator.validate(new AiSearchHit(1.0,content,citation),query)).isPresent();
        assertThat(citationValidator.validate(new AiSearchHit(1.0,content+" 변조",citation),query)).isEmpty();
    }

    private static String hash(String value)throws Exception{return "sha256:"+digest(value.getBytes(StandardCharsets.UTF_8));}
    private static String digest(byte[] value)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
