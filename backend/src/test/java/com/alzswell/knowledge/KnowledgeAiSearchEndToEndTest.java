package com.alzswell.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class KnowledgeAiSearchEndToEndTest {
    private static final String TOKEN="synthetic-internal-service-token-000063";
    private static final String DOCUMENT_ID="DOC-SYN-CONTRACT-001";
    private static final String VERSION="1.0.0";
    private static final String SOURCE_HASH="sha256:232e71a1e03d58e8afd24e291ea341e67b7b6c302263f88a9ec06504dec3d653";
    private static final String TEXT="이 문서는 계약 검증을 위한 합성 자료입니다.";
    private static final List<String> SECTION_PATH=List.of("합성 안심 안내","신청 방법");
    private static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules();
    private static final AtomicReference<Mode> MODE=new AtomicReference<>(Mode.VALID);
    private static final HttpServer AI_SERVER=startServer();
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.ai-retrieval.enabled",()->true);
        registry.add("app.ai-retrieval.base-url",()->"http://127.0.0.1:"+AI_SERVER.getAddress().getPort());
        registry.add("app.ai-retrieval.internal-token",()->TOKEN);
        registry.add("app.ai-retrieval.request-timeout-ms",()->500);
    }

    @AfterAll static void stopServer(){AI_SERVER.stop(0);}

    @Test @WithMockUser(username="knowledge-admin",authorities={"ROLE_PROTECTION_STAFF","KNOWLEDGE_ADMIN_WRITE","KNOWLEDGE_SEARCH","KNOWLEDGE_READ"})
    void importsApprovedChunkThenSearchesThroughFastApiAndFallsBackSafely() throws Exception {
        insertApprovedGovernance();
        String textHash="sha256:"+sha256(TEXT.getBytes(StandardCharsets.UTF_8));
        String chunkId="chk_"+sha256(MAPPER.writeValueAsBytes(List.of(DOCUMENT_ID,VERSION,SECTION_PATH,1,textHash,"structure-ko-v1")));
        insertAiIngestion(chunkId,textHash);
        mockMvc.perform(post("/api/v1/admin/knowledge/ingestion-imports").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key","e2e-import-v63-0001").content(importBody(chunkId,textHash)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.searchable").value(true));

        MODE.set(Mode.VALID);
        mockMvc.perform(searchRequest()).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].retrievalMode").value("INTERNAL_RAG_HYBRID"))
                .andExpect(jsonPath("$.data.vectorSearchUsed").value(true))
                .andExpect(jsonPath("$.data.items[0].passage.documentId").value(DOCUMENT_ID));

        MODE.set(Mode.TAMPERED);
        mockMvc.perform(searchRequest()).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].retrievalMode").value("DETERMINISTIC_FALLBACK"))
                .andExpect(jsonPath("$.data.vectorSearchUsed").value(false));
        Integer rejected=jdbc.queryForObject("""
                select (detail->>'rejectedCitations')::integer from knowledge_access_audit_event
                where event_type='SEARCH' order by occurred_at desc limit 1
                """,Integer.class);
        assertThat(rejected).isOne();

        MODE.set(Mode.UNAVAILABLE);
        mockMvc.perform(searchRequest()).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].retrievalMode").value("DETERMINISTIC_FALLBACK"))
                .andExpect(jsonPath("$.data.vectorSearchUsed").value(false));

    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder searchRequest() {
        return post("/api/v1/knowledge/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"계약 검증\",\"asOf\":\"2026-08-21\",\"audience\":\"STAFF\",\"limit\":5}");
    }

    private void insertApprovedGovernance() {
        OffsetDateTime approvedAt=OffsetDateTime.parse("2026-08-21T00:00:00Z");
        jdbc.update("""
                insert into knowledge_document_governance(
                  workflow_id,document_id,version_label,title,issuer,source_type,source_path,source_url,source_hash,
                  source_transformations,document_type,classification,audience,allowed_roles,effective_from,effective_to,
                  checked_at,usage_rights,approval_status,lifecycle_status,approved_by,approved_at,row_version,
                  registered_by,registered_at,updated_at
                ) values(?,?,?,?,?,'SYNTHETIC_FIXTURE','contracts/knowledge/fixtures/synthetic-source.html',null,?,
                  '[]'::jsonb,'SYNTHETIC_FIXTURE','INTERNAL','STAFF',string_to_array('PROTECTION_STAFF,DETECTION_ADMIN',',')::varchar[],
                  '2026-08-21',null,'2026-08-21','SYNTHETIC_UNRESTRICTED','APPROVED','PENDING_ACTIVATION','reviewer',?,2,'reviewer',?,?)
                """,UUID.randomUUID(),DOCUMENT_ID,VERSION,"합성 지식 계약 검증 안내","ALZ's well 테스트",SOURCE_HASH,
                approvedAt,approvedAt,approvedAt);
    }

    private void insertAiIngestion(String chunkId,String textHash) {
        UUID runId=UUID.fromString("97000000-0000-0000-0000-000000000063");
        OffsetDateTime started=OffsetDateTime.parse("2026-08-21T00:00:00Z");
        jdbc.update("""
                insert into ai_knowledge.ingestion_run(
                  run_id,document_id,version_label,source_hash,as_of,status,extractor_version,chunker_version,
                  chunk_count,warning_codes,failure_code,started_at,finished_at)
                values(?,?,?,?,?,'SUCCEEDED','html-structure-v1','structure-ko-v1',1,'{}',null,?,?)
                """,runId,DOCUMENT_ID,VERSION,SOURCE_HASH,LocalDate.of(2026,8,21),started,started.plusSeconds(1));
        jdbc.update("""
                insert into ai_knowledge.chunk(
                  chunk_id,run_id,document_id,version_label,heading,section_path,page,page_start,page_end,chunk_order,
                  content,text_hash,source_hash,extractor_version,chunker_version,created_at)
                values(?,?,?,?,?,string_to_array(?,?)::text[],null,null,null,1,?,?,?,'html-structure-v1','structure-ko-v1',?)
                """,chunkId,runId,DOCUMENT_ID,VERSION,"신청 방법",String.join("\u001f",SECTION_PATH),"\u001f",
                TEXT,textHash,SOURCE_HASH,started.plusSeconds(1));
    }

    private static String importBody(String chunkId,String textHash)throws Exception {
        ObjectNode root=MAPPER.createObjectNode();
        root.put("contractVersion","1.0.0");root.put("ingestionRunId","97000000-0000-0000-0000-000000000063");root.put("documentId",DOCUMENT_ID);
        root.put("versionLabel",VERSION);root.put("sourceHash",SOURCE_HASH);root.put("asOf","2026-08-21");
        root.put("extractorVersion","html-structure-v1");root.put("chunkerVersion","structure-ko-v1");
        ObjectNode chunk=root.putArray("chunks").addObject();chunk.put("chunkId",chunkId);chunk.put("chunkOrder",1);
        chunk.put("heading","신청 방법");chunk.putArray("sectionPath").add("합성 안심 안내").add("신청 방법");
        chunk.putNull("page");chunk.putNull("pageStart");chunk.putNull("pageEnd");chunk.put("text",TEXT);
        chunk.put("textHash",textHash);chunk.put("sourceHash",SOURCE_HASH);chunk.put("extractorVersion","html-structure-v1");
        chunk.put("chunkerVersion","structure-ko-v1");return MAPPER.writeValueAsString(root);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/internal/v1/search",exchange->{
                try {
                    if(MODE.get()==Mode.UNAVAILABLE) {exchange.sendResponseHeaders(503,-1);return;}
                    JsonNode request=MAPPER.readTree(exchange.getRequestBody());
                    if(!TOKEN.equals(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"))) {
                        exchange.sendResponseHeaders(401,-1);return;
                    }
                    String textHash="sha256:"+sha256(TEXT.getBytes(StandardCharsets.UTF_8));
                    String chunkId="chk_"+sha256(MAPPER.writeValueAsBytes(List.of(DOCUMENT_ID,VERSION,SECTION_PATH,1,textHash,"structure-ko-v1")));
                    ObjectNode response=MAPPER.createObjectNode();response.put("contractVersion","1.0.0");
                    response.put("requestId",request.get("requestId").asText());
                    response.put("queryHash","sha256:"+sha256(request.get("query").asText().getBytes(StandardCharsets.UTF_8)));
                    ObjectNode hit=response.putArray("results").addObject();hit.put("score",1.0);
                    hit.put("content",MODE.get()==Mode.TAMPERED?TEXT+" 변조":TEXT);
                    ObjectNode citation=hit.putObject("citation");citation.put("contractVersion","1.0.0");
                    citation.put("documentId",DOCUMENT_ID);citation.put("versionLabel",VERSION);citation.put("chunkId",chunkId);
                    citation.put("chunkOrder",1);citation.put("title","합성 지식 계약 검증 안내");citation.put("issuer","ALZ's well 테스트");
                    citation.put("heading","신청 방법");citation.putArray("sectionPath").add("합성 안심 안내").add("신청 방법");
                    citation.putNull("page");citation.put("citationLabel","합성 지식 계약 검증 안내 > 신청 방법");citation.putNull("sourceUrl");
                    citation.put("sourceHash",SOURCE_HASH);citation.put("textHash",textHash);citation.put("retrievedAsOf",request.get("asOf").asText());
                    citation.put("retrievalMethod","HYBRID");citation.put("indexVersion","hybrid-hash-ngram-v3");
                    byte[] bytes=MAPPER.writeValueAsBytes(response);exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
                } catch(Exception exception) {exchange.sendResponseHeaders(500,-1);}
                finally {exchange.close();}
            });
            server.start();return server;
        } catch(Exception exception){throw new ExceptionInInitializerError(exception);}
    }

    private static String sha256(byte[] value)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    private enum Mode {VALID,TAMPERED,UNAVAILABLE}
}
