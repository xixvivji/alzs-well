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
import java.time.*;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class KnowledgeCatalogIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiCitationValidator citationValidator;
    @Autowired ObjectMapper objectMapper;

    @Test @Transactional
    @WithMockUser(authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_READ","KNOWLEDGE_SEARCH","GUIDANCE_CANDIDATE_READ"})
    void providesApprovedEffectiveCitationsAndDeterministicGuidance() throws Exception {
        bindLegacyOfficialDocument();
        mockMvc.perform(get("/api/v1/knowledge/documents").param("audience","STAFF").param("asOf","2026-08-14"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
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
                .andExpect(jsonPath("$.data.items[0].matchedKeywordCount").value(2))
                .andExpect(jsonPath("$.data.externalModelCalled").value(false))
                .andExpect(jsonPath("$.data.vectorSearchUsed").value(false));
        mockMvc.perform(post("/api/v1/knowledge/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"보호수단\",\"asOf\":\"2026-08-14\",\"audience\":\"STAFF\",\"limit\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/guidance-candidates").param("reasonCode","DUPLICATE_TRANSFER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].citationPassageId")
                        .value("95000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.data.items[0].externalExecutionCreated").value(false));
        Integer accessEvents=jdbc.queryForObject("select count(*) from knowledge_access_audit_event",Integer.class);
        org.assertj.core.api.Assertions.assertThat(accessEvents).isGreaterThanOrEqualTo(5);
        String queryHash=jdbc.queryForObject("select query_hash from knowledge_access_audit_event where event_type='SEARCH' order by occurred_at desc limit 1",String.class);
        org.assertj.core.api.Assertions.assertThat(queryHash).hasSize(64).doesNotContain("안심차단");
    }

    @Test @Transactional
    @WithMockUser(authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_READ","KNOWLEDGE_SEARCH","GUIDANCE_CANDIDATE_READ"})
    void hidesLegacyCatalogRowsWithoutVerifiedImportFromEveryReadPath() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/knowledge/documents/DOC-FSC-SAFE-BLOCK-001"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/knowledge/passages/95000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/knowledge/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"안심차단 금융회사\",\"asOf\":\"2026-08-14\",\"audience\":\"STAFF\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/guidance-candidates").param("reasonCode","DUPLICATE_TRANSFER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        assertThat(jdbc.queryForObject("""
                select count(*) from knowledge_access_audit_event
                where event_type='GUIDANCE_CITATION' and permission_code='GUIDANCE_CANDIDATE_READ'
                  and requested_resource_id='SAFE_BLOCK_INFO' and outcome='NOT_FOUND'
                """,Integer.class)).isOne();
    }

    @Test @Transactional
    @WithMockUser(authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_READ","KNOWLEDGE_SEARCH"})
    void usesGinIndexedSimpleSearchWithoutInterpretingSqlText() throws Exception {
        bindLegacyOfficialDocument();
        String indexDefinition=jdbc.queryForObject("""
                select indexdef from pg_indexes
                where schemaname=current_schema() and indexname='idx_knowledge_passage_search_vector'
                """,String.class);
        assertThat(indexDefinition).contains("USING gin (search_vector)");
        jdbc.execute("set local enable_seqscan=off");
        List<String> plan=jdbc.queryForList("""
                explain select passage_id from knowledge_passage
                where search_vector @@ plainto_tsquery('pg_catalog.simple'::regconfig,'안심차단')
                """,String.class);
        assertThat(String.join("\n",plan)).contains("idx_knowledge_passage_search_vector");

        mockMvc.perform(post("/api/v1/knowledge/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"안심차단 '); DROP TABLE knowledge_document; --\",\"asOf\":\"2026-08-14\",\"audience\":\"STAFF\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from knowledge_document",Integer.class)).isEqualTo(2);
    }

    @Test @Transactional
    @WithMockUser(username="knowledge-admin",authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_ADMIN_WRITE",
            "KNOWLEDGE_READ","KNOWLEDGE_SEARCH","GUIDANCE_CANDIDATE_READ","PROTECTION_ACTION_READ"})
    void resolvesGuidanceAndProtectionCitationsFromTheVerifiedCurrentVersion() throws Exception {
        bindLegacyOfficialDocument();
        UUID currentPassage=importSecondOfficialVersion();
        assertThat(currentPassage).isNotEqualTo(UUID.fromString("95000000-0000-0000-0000-000000000001"));

        mockMvc.perform(get("/api/v1/guidance-candidates").param("reasonCode","DUPLICATE_TRANSFER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].citationPassageId").value(currentPassage.toString()));
        mockMvc.perform(get("/api/v1/protection-actions/SAFE_BLOCK_INFO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.citationPassageIds[0]").value(currentPassage.toString()));
        mockMvc.perform(get("/api/v1/knowledge/passages/{passageId}",currentPassage))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.versionLabel").value("2026-09"));
        mockMvc.perform(get("/api/v1/knowledge/passages/95000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/knowledge/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"세부\",\"asOf\":\"2026-08-14\",\"audience\":\"STAFF\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        assertThat(jdbc.queryForObject("""
                select count(*) from knowledge_access_audit_event
                where event_type='GUIDANCE_CITATION' and permission_code='GUIDANCE_CANDIDATE_READ'
                  and requested_resource_id='SAFE_BLOCK_INFO' and outcome='ALLOWED'
                  and returned_resource_ids @> array[?]::text[]
                  and detail->>'queryType'='GUIDANCE_CITATION'
                """,Integer.class,currentPassage.toString())).isOne();
        assertThat(jdbc.queryForObject("""
                select count(*) from knowledge_access_audit_event
                where event_type='PROTECTION_ACTION_CITATION' and permission_code='PROTECTION_ACTION_READ'
                  and requested_resource_id='SAFE_BLOCK_INFO' and outcome='ALLOWED'
                  and returned_resource_ids @> array[?]::text[]
                  and detail->>'queryType'='PROTECTION_ACTION_CITATION'
                """,Integer.class,currentPassage.toString())).isOne();
    }

    @Test @Transactional @WithMockUser(authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_READ"})
    void appliesEffectiveDateAndHidesUnknownResources() throws Exception {
        bindLegacyOfficialDocument();
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

    @Test @Transactional
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
        UUID importId=UUID.randomUUID();
        UUID runId=UUID.randomUUID();
        insertAiSnapshot(runId,"DOC-FSC-SAFE-BLOCK-001","2026-08",sourceHash,LocalDate.of(2026,8,14),
                chunkId,"신청 전 확인",sectionPath,content,textHash);
        jdbc.update("""
                insert into knowledge_ingestion_import(
                  import_id,ingestion_run_id,document_id,version_label,source_hash,as_of,extractor_version,
                  chunker_version,chunk_count,imported_by,imported_at,payload_hash,integrity_hash,
                  ai_proof_version,ai_verified_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,'AI_DB_SNAPSHOT_V1',?)
                """,importId,runId,
                "DOC-FSC-SAFE-BLOCK-001","2026-08",sourceHash,LocalDate.of(2026,8,14),"html-structure-v1",
                "structure-ko-v1",1,"reviewer",java.time.OffsetDateTime.parse("2026-08-14T00:00:00Z"),
                "b".repeat(64),"c".repeat(64),java.time.OffsetDateTime.parse("2026-08-14T00:00:01Z"));
        jdbc.update("""
                insert into knowledge_ai_passage_binding values(?,?,?,?,?,?,string_to_array(?,','),null,null,null,?,?,?,?)
                """,chunkId,UUID.fromString("95000000-0000-0000-0000-000000000001"),importId,
                "DOC-FSC-SAFE-BLOCK-001","2026-08",1,"신청 전 확인",sourceHash,textHash,
                "html-structure-v1","structure-ko-v1");
        AiCitation citation=new AiCitation("1.0.0","DOC-FSC-SAFE-BLOCK-001","2026-08",chunkId,1,
                "금융거래 안심차단 안내 근거","금융위원회","신청 전 확인",sectionPath,null,
                "금융거래 안심차단 안내 근거 > 신청 전 확인","https://www.fsc.go.kr/no010101/85644",sourceHash,textHash,
                LocalDate.of(2026,8,14),"HYBRID","hybrid-hash-ngram-v3");
        RetrievalQuery query=new RetrievalQuery("안심차단 금융회사",LocalDate.of(2026,8,14),"STAFF",
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);

        assertThat(citationValidator.validate(new AiSearchHit(1.0,content,citation),query)).isPresent();
        AiCitation arcticCitation=new AiCitation("1.0.0","DOC-FSC-SAFE-BLOCK-001","2026-08",chunkId,1,
                "금융거래 안심차단 안내 근거","금융위원회","신청 전 확인",sectionPath,null,
                "금융거래 안심차단 안내 근거 > 신청 전 확인","https://www.fsc.go.kr/no010101/85644",sourceHash,textHash,
                LocalDate.of(2026,8,14),"HYBRID","hybrid-arctic-ko-v1");
        assertThat(citationValidator.validate(new AiSearchHit(1.0,content,arcticCitation),query)).isPresent();
        assertThat(citationValidator.validate(new AiSearchHit(1.0,content+" 변조",citation),query)).isEmpty();
    }

    private void bindLegacyOfficialDocument()throws Exception {
        String documentId="DOC-FSC-SAFE-BLOCK-001";
        String version="2026-08";
        String sourceHash="sha256:"+"d".repeat(64);
        OffsetDateTime approvedAt=OffsetDateTime.parse("2026-08-14T00:00:00Z");
        jdbc.update("""
                insert into knowledge_document_governance(
                  workflow_id,document_id,version_label,title,issuer,source_type,source_path,source_url,source_hash,
                  source_transformations,document_type,classification,audience,allowed_roles,effective_from,effective_to,
                  checked_at,usage_rights,approval_status,lifecycle_status,approved_by,approved_at,row_version,
                  registered_by,registered_at,updated_at
                ) values(?,?,?,?,?,'OFFICIAL_EXTERNAL',?,?,?,'[]'::jsonb,'PUBLIC_GUIDE','PUBLIC_OFFICIAL','BOTH',
                  string_to_array('PROTECTION_STAFF,DETECTION_ADMIN',',')::varchar[],'2024-08-23',null,'2026-08-14',
                  'PUBLIC_REUSE_ALLOWED','APPROVED','ACTIVE','reviewer',?,2,'reviewer',?,?)
                """,UUID.randomUUID(),documentId,version,"금융거래 안심차단 안내 근거","금융위원회",
                "knowledge/official-source/test.html","https://www.fsc.go.kr/no010101/85644",sourceHash,
                approvedAt,approvedAt,approvedAt);
        String content="안심차단 신청 가능 여부와 세부 범위는 해당 금융회사에서 최종 확인해야 합니다.";
        String textHash=hash(content);
        List<String> sectionPath=List.of("신청 전 확인");
        String chunkId="chk_"+digest(objectMapper.writeValueAsBytes(List.of(documentId,version,sectionPath,1,
                textHash,"structure-ko-v1")));
        UUID importId=UUID.randomUUID();
        UUID runId=UUID.randomUUID();
        insertAiSnapshot(runId,documentId,version,sourceHash,LocalDate.of(2026,8,14),chunkId,
                "신청 전 확인",sectionPath,content,textHash);
        jdbc.update("""
                insert into knowledge_ingestion_import(
                  import_id,ingestion_run_id,document_id,version_label,source_hash,as_of,extractor_version,
                  chunker_version,chunk_count,imported_by,imported_at,payload_hash,integrity_hash,
                  ai_proof_version,ai_verified_at)
                values(?,?,?,?,?,'2026-08-14','html-structure-v1','structure-ko-v1',1,'reviewer',?,
                  ?,?,'AI_DB_SNAPSHOT_V1',?)
                """,importId,runId,documentId,version,sourceHash,approvedAt,
                "e".repeat(64),"f".repeat(64),approvedAt.plusSeconds(1));
        jdbc.update("""
                insert into knowledge_ai_passage_binding(
                  chunk_id,passage_id,import_id,document_id,version_label,chunk_order,section_path,page,page_start,
                  page_end,source_hash,text_hash,extractor_version,chunker_version)
                values(?,?,?,?,?,1,string_to_array('신청 전 확인',',')::text[],null,null,null,?,?,
                  'html-structure-v1','structure-ko-v1')
                """,chunkId,UUID.fromString("95000000-0000-0000-0000-000000000001"),
                importId,documentId,version,sourceHash,textHash);
    }

    private UUID importSecondOfficialVersion()throws Exception {
        String documentId="DOC-FSC-SAFE-BLOCK-001";
        String version="2026-09";
        String sourceHash="sha256:"+"2".repeat(64);
        var register=objectMapper.createObjectNode();
        register.put("documentId",documentId);register.put("versionLabel",version);
        register.put("title","금융거래 안심차단 최신 안내 근거");register.put("issuer","금융위원회");
        register.put("sourceType","OFFICIAL_EXTERNAL");
        register.put("sourcePath","knowledge/official-source/test-v2.html");
        register.put("sourceUrl","https://www.fsc.go.kr/no010101/85644");register.put("sourceHash",sourceHash);
        register.putArray("sourceTransformations");register.put("documentType","PUBLIC_GUIDE");
        register.put("classification","PUBLIC_OFFICIAL");register.put("audience","BOTH");
        register.putArray("allowedRoles").add("PROTECTION_STAFF").add("DETECTION_ADMIN");
        register.put("effectiveFrom","2024-08-23");register.putNull("effectiveTo");
        register.put("checkedAt","2026-08-14");register.put("usageRights","PUBLIC_REUSE_ALLOWED");
        register.put("supersedesDocumentId",documentId);register.put("supersedesVersionLabel","2026-08");
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","catalog-v2-register-001")
                        .content(objectMapper.writeValueAsBytes(register)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/knowledge/documents/{documentId}/publish",documentId)
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key","catalog-v2-publish-001")
                        .content("{\"versionLabel\":\"2026-09\",\"expectedVersion\":1,"
                                +"\"approvalReference\":\"REVIEW-CATALOG-V2\"}"))
                .andExpect(status().isOk());

        String text="최신 안심차단 신청 범위는 금융회사에서 확인해야 합니다.";
        String textHash=hash(text);
        List<String> sectionPath=List.of("안심차단 최신 안내","신청 범위");
        String chunkId="chk_"+digest(objectMapper.writeValueAsBytes(List.of(documentId,version,sectionPath,1,
                textHash,"structure-ko-v1")));
        UUID runId=UUID.fromString("97000000-0000-0000-0000-000000000071");
        OffsetDateTime started=OffsetDateTime.parse("2026-08-14T00:00:00Z");
        jdbc.update("""
                insert into ai_knowledge.ingestion_run(
                  run_id,document_id,version_label,source_hash,as_of,status,extractor_version,chunker_version,
                  chunk_count,warning_codes,failure_code,started_at,finished_at)
                values(?,?,?,?,?,'SUCCEEDED','html-structure-v1','structure-ko-v1',1,'{}',null,?,?)
                """,runId,documentId,version,sourceHash,LocalDate.of(2026,8,14),started,started.plusSeconds(1));
        jdbc.update("""
                insert into ai_knowledge.chunk(
                  chunk_id,run_id,document_id,version_label,heading,section_path,page,page_start,page_end,chunk_order,
                  content,text_hash,source_hash,extractor_version,chunker_version,created_at)
                values(?,?,?,?,?,string_to_array(?,?)::text[],null,null,null,1,?,?,?,
                  'html-structure-v1','structure-ko-v1',?)
                """,chunkId,runId,documentId,version,"신청 범위",String.join("\u001f",sectionPath),"\u001f",
                text,textHash,sourceHash,started.plusSeconds(1));
        var bundle=objectMapper.createObjectNode();bundle.put("contractVersion","1.0.0");
        bundle.put("ingestionRunId",runId.toString());bundle.put("documentId",documentId);
        bundle.put("versionLabel",version);bundle.put("sourceHash",sourceHash);bundle.put("asOf","2026-08-14");
        bundle.put("extractorVersion","html-structure-v1");bundle.put("chunkerVersion","structure-ko-v1");
        var chunk=bundle.putArray("chunks").addObject();chunk.put("chunkId",chunkId);chunk.put("chunkOrder",1);
        chunk.put("heading","신청 범위");sectionPath.forEach(chunk.putArray("sectionPath")::add);
        chunk.putNull("page");chunk.putNull("pageStart");chunk.putNull("pageEnd");chunk.put("text",text);
        chunk.put("textHash",textHash);chunk.put("sourceHash",sourceHash);
        chunk.put("extractorVersion","html-structure-v1");chunk.put("chunkerVersion","structure-ko-v1");
        var result=mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports")
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key","catalog-v2-import-001")
                        .content(objectMapper.writeValueAsBytes(bundle)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/passageIds/0").asText());
    }

    private void insertAiSnapshot(UUID runId,String documentId,String versionLabel,String sourceHash,LocalDate asOf,
            String chunkId,String heading,List<String> sectionPath,String content,String textHash) {
        OffsetDateTime started=OffsetDateTime.parse("2026-08-14T00:00:00Z");
        jdbc.update("""
                insert into ai_knowledge.ingestion_run(
                  run_id,document_id,version_label,source_hash,as_of,status,extractor_version,chunker_version,
                  chunk_count,warning_codes,failure_code,started_at,finished_at)
                values(?,?,?,?,?,'SUCCEEDED','html-structure-v1','structure-ko-v1',1,'{}',null,?,?)
                """,runId,documentId,versionLabel,sourceHash,asOf,started,started.plusSeconds(1));
        jdbc.update("""
                insert into ai_knowledge.chunk(
                  chunk_id,run_id,document_id,version_label,heading,section_path,page,page_start,page_end,chunk_order,
                  content,text_hash,source_hash,extractor_version,chunker_version,created_at)
                values(?,?,?,?,?,string_to_array(?,?)::text[],null,null,null,1,?,?,?,
                  'html-structure-v1','structure-ko-v1',?)
                """,chunkId,runId,documentId,versionLabel,heading,String.join("\u001f",sectionPath),"\u001f",
                content,textHash,sourceHash,started.plusSeconds(1));
    }

    private static String hash(String value)throws Exception{return "sha256:"+digest(value.getBytes(StandardCharsets.UTF_8));}
    private static String digest(byte[] value)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
