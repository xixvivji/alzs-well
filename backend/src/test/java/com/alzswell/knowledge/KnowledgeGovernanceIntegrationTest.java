package com.alzswell.knowledge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class KnowledgeGovernanceIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

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
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.ingestionReady").value(true))
                .andExpect(jsonPath("$.data.searchable").value(false));

        String text="승인된 합성 문서의 검색 연결을 검증합니다.";
        String textHash="sha256:"+sha256(text.getBytes(StandardCharsets.UTF_8));
        List<String> sectionPath=List.of("합성 검토 문서","안내");
        String chunkId="chk_"+sha256(objectMapper.writeValueAsBytes(List.of("DOC-TEST-GOV-001","2026-08",
                sectionPath,1,textHash,"structure-ko-v1")));
        String importBody=importBody(chunkId,text,textHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v63-invalid-01")
                        .content(importBody.replace(chunkId,"chk_"+"0".repeat(64))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("KNOWLEDGE_IMPORT_PAYLOAD_INVALID"));
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v69-unverified-01").content(importBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_IMPORT_AI_INGESTION_NOT_VERIFIED"));
        UUID runId=UUID.fromString("97000000-0000-0000-0000-000000000063");
        insertAiProof(runId,"DOC-TEST-GOV-001","2026-08","sha256:"+"a".repeat(64),
                LocalDate.parse("2026-08-24"),chunkId,"안내",sectionPath,text+" 변조",textHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v69-mismatch-001").content(importBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_IMPORT_AI_INGESTION_NOT_VERIFIED"));
        jdbc.update("delete from ai_knowledge.chunk where run_id=?",runId);
        jdbc.update("delete from ai_knowledge.ingestion_run where run_id=?",runId);
        insertAiProof(UUID.fromString("97000000-0000-0000-0000-000000000063"),"DOC-TEST-GOV-001","2026-08",
                "sha256:"+"a".repeat(64),LocalDate.parse("2026-08-24"),chunkId,"안내",sectionPath,text,textHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v63-00000001").content(importBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("KNOWLEDGE_INGESTION_IMPORTED"))
                .andExpect(jsonPath("$.data.chunkCount").value(1)).andExpect(jsonPath("$.data.searchable").value(true));
        validateDeferredCatalogConstraint();
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","idem-v63-00000001").content(importBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.chunkCount").value(1));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select ai_proof_version from knowledge_ingestion_import
                where ingestion_run_id='97000000-0000-0000-0000-000000000063'
                """,String.class)).isEqualTo("AI_DB_SNAPSHOT_V1");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("""
                select approval_status,lifecycle_status,row_version
                from knowledge_document_governance where document_id='DOC-TEST-GOV-001'
                """)).containsEntry("approval_status","APPROVED")
                .containsEntry("lifecycle_status","ACTIVE").containsEntry("row_version",3L);

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from knowledge_governance_event where document_id='DOC-TEST-GOV-001'",Integer.class)).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from knowledge_ai_passage_binding where document_id='DOC-TEST-GOV-001'",Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/audit/events").param("sourceType","KNOWLEDGE_GOVERNANCE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.items[0].targetType").value("KNOWLEDGE_DOCUMENT"));
        mockMvc.perform(get("/api/v1/audit/events").param("sourceType","KNOWLEDGE_IMPORT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.items[0].eventType").value("INGESTION_IMPORTED"));
        assertThatThrownBy(()->jdbc.update("update knowledge_governance_event set event_type='PUBLISHED' where document_id='DOC-TEST-GOV-001'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update knowledge_document_governance set title='변조' where document_id='DOC-TEST-GOV-001'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from knowledge_ai_passage_binding where document_id='DOC-TEST-GOV-001'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test @Transactional @WithMockUser(username="knowledge-admin",authorities="KNOWLEDGE_ADMIN_WRITE")
    void importsSecondGovernedVersionAndSwitchesCatalogAtomically() throws Exception {
        String documentId="DOC-TEST-VERSION-001";
        String firstVersion="1.0.0"; String secondVersion="2.0.0";
        String firstSource="sha256:"+"c".repeat(64); String secondSource="sha256:"+"d".repeat(64);
        publish(documentId,firstVersion,firstSource,null,"version-v69-register-01","version-v69-publish-01");

        String firstText="첫 번째 승인 버전의 합성 안내입니다.";
        String firstTextHash="sha256:"+sha256(firstText.getBytes(StandardCharsets.UTF_8));
        List<String> firstPath=List.of("버전 안내","첫 버전");
        String firstChunk="chk_"+sha256(objectMapper.writeValueAsBytes(List.of(documentId,firstVersion,firstPath,1,
                firstTextHash,"structure-ko-v1")));
        UUID firstRun=UUID.fromString("97000000-0000-0000-0000-000000000069");
        insertAiProof(firstRun,documentId,firstVersion,firstSource,LocalDate.parse("2026-08-24"),firstChunk,
                "첫 버전",firstPath,firstText,firstTextHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","version-v69-import-01")
                        .content(importBody(firstRun,documentId,firstVersion,firstSource,firstChunk,"첫 버전",firstPath,
                                firstText,firstTextHash)))
                .andExpect(status().isCreated());
        validateDeferredCatalogConstraint();

        publish(documentId,secondVersion,secondSource,firstVersion,"version-v69-register-02","version-v69-publish-02");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select current_version from knowledge_document where document_id=?",String.class,documentId))
                .isEqualTo(firstVersion);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance where document_id=? and version_label=?
                """,String.class,documentId,firstVersion)).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance where document_id=? and version_label=?
                """,String.class,documentId,secondVersion)).isEqualTo("PENDING_ACTIVATION");
        String secondText="두 번째 승인 버전의 교체된 합성 안내입니다.";
        String secondTextHash="sha256:"+sha256(secondText.getBytes(StandardCharsets.UTF_8));
        List<String> secondPath=List.of("버전 안내","두 번째 버전");
        String secondChunk="chk_"+sha256(objectMapper.writeValueAsBytes(List.of(documentId,secondVersion,secondPath,1,
                secondTextHash,"structure-ko-v1")));
        UUID secondRun=UUID.fromString("97000000-0000-0000-0000-000000000070");
        String secondImportBody=importBody(secondRun,documentId,secondVersion,secondSource,secondChunk,"두 번째 버전",
                secondPath,secondText,secondTextHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","version-v69-import-unverified-02").content(secondImportBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_IMPORT_AI_INGESTION_NOT_VERIFIED"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select current_version from knowledge_document where document_id=?",String.class,documentId))
                .isEqualTo(firstVersion);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance where document_id=? and version_label=?
                """,String.class,documentId,firstVersion)).isEqualTo("ACTIVE");
        insertAiProof(secondRun,documentId,secondVersion,secondSource,LocalDate.parse("2026-08-24"),secondChunk,
                "두 번째 버전",secondPath,secondText,secondTextHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","version-v69-import-02")
                        .content(secondImportBody))
                .andExpect(status().isCreated());
        validateDeferredCatalogConstraint();

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select current_version from knowledge_document where document_id=?",String.class,documentId))
                .isEqualTo(secondVersion);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select superseded_at is not null from knowledge_document_version
                where document_id=? and version_label=?
                """,Boolean.class,documentId,firstVersion)).isTrue();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select count(*) from knowledge_document_version where document_id=?
                """,Integer.class,documentId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance where document_id=? and version_label=?
                """,String.class,documentId,firstVersion)).isEqualTo("SUPERSEDED");
        jdbc.update("update knowledge_document set current_version=? where document_id=?",firstVersion,documentId);
        assertThatThrownBy(()->jdbc.execute("set constraints trg_knowledge_catalog_version_switch immediate"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test @Transactional @WithMockUser(username="knowledge-admin",authorities="KNOWLEDGE_ADMIN_WRITE")
    void explicitlyAdoptsLegacyV28HeadWhenVerifiedSuccessorIsImported() throws Exception {
        String documentId="DOC-SYN-BANK-SUPPORT-001";
        String version="2.0.0";
        String sourceHash="sha256:"+"e".repeat(64);
        publish(documentId,version,sourceHash,"1.0.0","legacy-v69-register-01","legacy-v69-publish-01");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance where document_id=? and version_label=?
                """,String.class,documentId,version)).isEqualTo("PENDING_ACTIVATION");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select count(*) from knowledge_document_governance where document_id=? and version_label='1.0.0'
                """,Integer.class,documentId)).isZero();

        String text="V28 합성 카탈로그를 명시적으로 대체하는 검증 문서입니다.";
        String textHash="sha256:"+sha256(text.getBytes(StandardCharsets.UTF_8));
        List<String> path=List.of("레거시 카탈로그","대체 버전");
        String chunk="chk_"+sha256(objectMapper.writeValueAsBytes(List.of(documentId,version,path,1,textHash,
                "structure-ko-v1")));
        UUID runId=UUID.fromString("97000000-0000-0000-0000-000000000071");
        insertAiProof(runId,documentId,version,sourceHash,LocalDate.parse("2026-08-24"),chunk,
                "대체 버전",path,text,textHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","legacy-v69-import-01")
                        .content(importBody(runId,documentId,version,sourceHash,chunk,"대체 버전",path,text,textHash)))
                .andExpect(status().isCreated());
        validateDeferredCatalogConstraint();

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select current_version from knowledge_document where document_id=?",String.class,documentId))
                .isEqualTo(version);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select superseded_at is not null from knowledge_document_version
                where document_id=? and version_label='1.0.0'
                """,Boolean.class,documentId)).isTrue();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance where document_id=? and version_label=?
                """,String.class,documentId,version)).isEqualTo("ACTIVE");
    }

    @Test @Transactional @WithMockUser(username="knowledge-admin",authorities="KNOWLEDGE_ADMIN_WRITE")
    void explicitReplacementPublishRetiresAStuckPendingCandidateIdempotently() throws Exception {
        String documentId="DOC-TEST-PENDING-RECOVERY-001";
        publish(documentId,"1.0.0","sha256:"+"8".repeat(64),null,
                "pending-recovery-register-01","pending-recovery-publish-01");

        var body=(com.fasterxml.jackson.databind.node.ObjectNode)objectMapper.readTree(REGISTER);
        body.put("documentId",documentId);body.put("versionLabel","2.0.0");body.put("title","교체 검토 버전");
        body.put("sourceHash","sha256:"+"9".repeat(64));body.putNull("supersedesDocumentId");
        body.putNull("supersedesVersionLabel");
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","pending-recovery-register-02")
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated());
        String publishBody="""
                {"versionLabel":"2.0.0","expectedVersion":1,
                 "approvalReference":"REVIEW-PENDING-REPLACEMENT-002"}
                """;
        mockMvc.perform(post("/api/v1/admin/knowledge/documents/{documentId}/publish",documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","pending-recovery-publish-02").content(publishBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PENDING_ACTIVATION"));
        mockMvc.perform(post("/api/v1/admin/knowledge/documents/{documentId}/publish",documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","pending-recovery-publish-02").content(publishBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PENDING_ACTIVATION"));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance
                where document_id=? and version_label='1.0.0'
                """,String.class,documentId)).isEqualTo("RETIRED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select lifecycle_status from knowledge_document_governance
                where document_id=? and version_label='2.0.0'
                """,String.class,documentId)).isEqualTo("PENDING_ACTIVATION");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                select count(*) from knowledge_governance_event
                where document_id=? and event_type='RETIRED'
                """,Integer.class,documentId)).isOne();
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

    @Test @Transactional
    void runtimeRoleCannotReserveImportIdentityWithPartialNullProof() {
        jdbc.execute("create role alzswell_app nologin nosuperuser nocreatedb nocreaterole noreplication");
        jdbc.execute("grant insert on knowledge_ingestion_import to alzswell_app");
        jdbc.execute("set local role alzswell_app");
        assertThatThrownBy(()->jdbc.update("""
                insert into knowledge_ingestion_import(
                  import_id,ingestion_run_id,document_id,version_label,source_hash,as_of,extractor_version,
                  chunker_version,chunk_count,imported_by,imported_at,payload_hash,integrity_hash,
                  ai_proof_version,ai_verified_at)
                values('97000000-0000-0000-0000-000000000169','97000000-0000-0000-0000-000000000269',
                  'DOC-PROOF-RESERVATION-001','1.0.0',?,'2026-08-24','html-structure-v1','structure-ko-v1',1,
                  'runtime-probe','2026-08-24T00:00:00Z',?,?,null,'2026-08-24T00:00:01Z')
                ""","sha256:"+"f".repeat(64),"1".repeat(64),"2".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test @Transactional
    void runtimeRoleCannotFabricateVerifiedImportWithoutExactBindingsAndPassages() {
        UUID runId=UUID.fromString("97000000-0000-0000-0000-000000000369");
        String documentId="DOC-PROOF-GRAPH-001";
        String version="1.0.0";
        String sourceHash="sha256:"+"7".repeat(64);
        String text="DB proof graph 검증용 합성 청크입니다.";
        String textHash="sha256:"+uncheckedSha256(text.getBytes(StandardCharsets.UTF_8));
        List<String> path=List.of("DB proof","graph");
        String chunk="chk_"+uncheckedSha256(uncheckedJsonBytes(List.of(documentId,version,path,1,textHash,
                "structure-ko-v1")));
        insertAiProof(runId,documentId,version,sourceHash,LocalDate.parse("2026-08-24"),chunk,
                "graph",path,text,textHash);

        jdbc.execute("create role alzswell_app nologin nosuperuser nocreatedb nocreaterole noreplication");
        jdbc.execute("grant insert on knowledge_ingestion_import to alzswell_app");
        jdbc.execute("grant usage on schema ai_knowledge to alzswell_app");
        jdbc.execute("grant select on ai_knowledge.ingestion_run,ai_knowledge.chunk to alzswell_app");
        jdbc.execute("set local role alzswell_app");
        jdbc.update("""
                insert into knowledge_ingestion_import(
                  import_id,ingestion_run_id,document_id,version_label,source_hash,as_of,extractor_version,
                  chunker_version,chunk_count,imported_by,imported_at,payload_hash,integrity_hash,
                  ai_proof_version,ai_verified_at)
                values('97000000-0000-0000-0000-000000000469',?,?,?,?,?,'html-structure-v1',
                  'structure-ko-v1',1,'runtime-probe','2026-08-24T00:00:00Z',?,?,
                  'AI_DB_SNAPSHOT_V1','2026-08-24T00:00:01Z')
                """,runId,documentId,version,sourceHash,LocalDate.parse("2026-08-24"),"3".repeat(64),"4".repeat(64));
        jdbc.execute("reset role");

        assertThatThrownBy(()->jdbc.execute("set constraints trg_knowledge_import_graph_integrity immediate"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private static String sha256(byte[] value)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String uncheckedSha256(byte[] value) {
        try { return sha256(value); }
        catch(Exception exception) { throw new IllegalStateException(exception); }
    }

    private byte[] uncheckedJsonBytes(Object value) {
        try { return objectMapper.writeValueAsBytes(value); }
        catch(Exception exception) { throw new IllegalStateException(exception); }
    }

    private String importBody(String chunkId,String text,String textHash)throws Exception {
        return importBody(UUID.fromString("97000000-0000-0000-0000-000000000063"),"DOC-TEST-GOV-001","2026-08",
                "sha256:"+"a".repeat(64),chunkId,"안내",List.of("합성 검토 문서","안내"),text,textHash);
    }

    private String importBody(UUID runId,String documentId,String versionLabel,String sourceHash,String chunkId,
            String heading,List<String> sectionPath,String text,String textHash)throws Exception {
        var root=objectMapper.createObjectNode();root.put("contractVersion","1.0.0");
        root.put("ingestionRunId",runId.toString());root.put("documentId",documentId);
        root.put("versionLabel",versionLabel);root.put("sourceHash",sourceHash);root.put("asOf","2026-08-24");
        root.put("extractorVersion","html-structure-v1");root.put("chunkerVersion","structure-ko-v1");
        var chunk=root.putArray("chunks").addObject();chunk.put("chunkId",chunkId);chunk.put("chunkOrder",1);
        chunk.put("heading",heading);var path=chunk.putArray("sectionPath");sectionPath.forEach(path::add);
        chunk.putNull("page");chunk.putNull("pageStart");chunk.putNull("pageEnd");chunk.put("text",text);
        chunk.put("textHash",textHash);chunk.put("sourceHash",sourceHash);
        chunk.put("extractorVersion","html-structure-v1");chunk.put("chunkerVersion","structure-ko-v1");
        return objectMapper.writeValueAsString(root);
    }

    private void publish(String documentId,String versionLabel,String sourceHash,String supersedesVersion,
            String registerKey,String publishKey)throws Exception {
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)objectMapper.readTree(REGISTER);
        body.put("documentId",documentId);body.put("versionLabel",versionLabel);body.put("title","합성 버전 "+versionLabel);
        body.put("sourceHash",sourceHash);
        if(supersedesVersion==null) {
            body.putNull("supersedesDocumentId");body.putNull("supersedesVersionLabel");
        } else {
            body.put("supersedesDocumentId",documentId);body.put("supersedesVersionLabel",supersedesVersion);
        }
        mockMvc.perform(post("/api/v1/admin/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key",registerKey).content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated());
        var publish=objectMapper.createObjectNode();publish.put("versionLabel",versionLabel);publish.put("expectedVersion",1);
        publish.put("approvalReference","REVIEW-"+versionLabel);
        mockMvc.perform(post("/api/v1/admin/knowledge/documents/{documentId}/publish",documentId)
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key",publishKey)
                        .content(objectMapper.writeValueAsBytes(publish)))
                .andExpect(status().isOk());
    }

    private void insertAiProof(UUID runId,String documentId,String versionLabel,String sourceHash,LocalDate asOf,
            String chunkId,String heading,List<String> sectionPath,String text,String textHash) {
        var started=java.time.OffsetDateTime.parse("2026-08-24T00:00:00Z");
        var finished=started.plusSeconds(1);
        jdbc.update("""
                insert into ai_knowledge.ingestion_run(
                  run_id,document_id,version_label,source_hash,as_of,status,extractor_version,chunker_version,
                  chunk_count,warning_codes,failure_code,started_at,finished_at)
                values(?,?,?,?,?,'SUCCEEDED','html-structure-v1','structure-ko-v1',1,'{}',null,?,?)
                """,runId,documentId,versionLabel,sourceHash,asOf,started,finished);
        jdbc.update("""
                insert into ai_knowledge.chunk(
                  chunk_id,run_id,document_id,version_label,heading,section_path,page,page_start,page_end,chunk_order,
                  content,text_hash,source_hash,extractor_version,chunker_version,created_at)
                values(?,?,?,?,?,string_to_array(?,?)::text[],null,null,null,1,?,?,?,'html-structure-v1','structure-ko-v1',?)
                """,chunkId,runId,documentId,versionLabel,heading,String.join("\u001f",sectionPath),"\u001f",
                text,textHash,sourceHash,finished);
    }

    private void validateDeferredCatalogConstraint() {
        jdbc.execute("set constraints trg_knowledge_catalog_version_switch immediate");
        jdbc.execute("set constraints trg_knowledge_catalog_version_switch deferred");
    }
}
