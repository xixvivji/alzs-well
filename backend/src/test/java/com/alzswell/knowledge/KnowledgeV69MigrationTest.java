package com.alzswell.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alzswell.test.PgVectorPostgreSqlContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class KnowledgeV69MigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES=new PgVectorPostgreSqlContainer();

    @Test
    void upgradesOnlyExactlyReverifiableV68ImportsAndKeepsNonContiguousLegacyFailClosed() {
        var dataSource=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("create role alzswell_ai_ingestor nologin nosuperuser nocreatedb nocreaterole noreplication");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("68")).load().migrate();

        insertLegacyRunAndImport(jdbc,true);
        insertLegacyRunAndImport(jdbc,false);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("69")).load().migrate();

        assertThat(jdbc.queryForObject("""
                select ai_proof_version from knowledge_ingestion_import
                where import_id='96900000-0000-0000-0000-000000000001'
                """,String.class)).isEqualTo("AI_DB_SNAPSHOT_V1");
        assertThat(jdbc.queryForObject("""
                select ai_verified_at is not null from knowledge_ingestion_import
                where import_id='96900000-0000-0000-0000-000000000001'
                """,Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                select ai_proof_version is null and ai_verified_at is null
                from knowledge_ingestion_import
                where import_id='96900000-0000-0000-0000-000000000002'
                """,Boolean.class)).isTrue();
        assertThatThrownBy(()->jdbc.update("""
                update knowledge_ingestion_import set ai_verified_at=clock_timestamp()
                where import_id='96900000-0000-0000-0000-000000000002'
                """))
                .isInstanceOf(DataAccessException.class);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("72")).load().migrate();

        assertAiIngestorRejected(dataSource,jdbc,()->jdbc.update("""
                delete from ai_knowledge.chunk
                where document_id='DOC-SYN-BANK-SUPPORT-001' and version_label='1.0.0'
                """),"verified knowledge snapshots are immutable");
        assertThatThrownBy(()->jdbc.update("""
                update ai_knowledge.chunk set document_id='DOC-UNVERIFIED-MOVE-001'
                where document_id='DOC-SYN-BANK-SUPPORT-001' and version_label='1.0.0'
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("verified knowledge snapshots are immutable");
        assertAiIngestorRejected(dataSource,jdbc,()->jdbc.update("""
                update ai_knowledge.ingestion_run
                   set status='FAILED', failure_code='STORAGE_CONFLICT'
                 where run_id='96800000-0000-0000-0000-000000000001'
                """),"verified knowledge ingestion runs are immutable");
        assertAiIngestorRejected(dataSource,jdbc,()->jdbc.update("""
                insert into ai_knowledge.document_snapshot(
                  document_id,version_label,contract_version,title,issuer,source_url,source_hash,
                  classification,audience,allowed_roles,approval_status,lifecycle_status,
                  effective_from,effective_to,indexed_at)
                values('DOC-SYN-BANK-SUPPORT-001','1.0.0','1.0.0','검증 문서','안심은행',null,
                  'sha256:1111111111111111111111111111111111111111111111111111111111111111',
                  'INTERNAL','STAFF',array['PROTECTION_STAFF'],'APPROVED','ACTIVE',
                  '2026-08-01',null,'2026-08-24T00:00:03Z')
                """),"verified knowledge snapshots are immutable");
        assertThat(jdbc.update("""
                delete from ai_knowledge.chunk
                where document_id='DOC-FSC-SAFE-BLOCK-001' and version_label='2026-08'
                """)).isEqualTo(2);
    }

    private void assertAiIngestorRejected(DriverManagerDataSource dataSource,JdbcTemplate jdbc,
            Runnable mutation,String expectedMessage) {
        TransactionTemplate transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status->{
            jdbc.execute("set local role alzswell_ai_ingestor");
            assertThatThrownBy(mutation::run)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining(expectedMessage);
            status.setRollbackOnly();
        });
    }

    private void insertLegacyRunAndImport(JdbcTemplate jdbc,boolean complete) {
        String suffix=complete?"1":"2";
        String documentId=complete?"DOC-SYN-BANK-SUPPORT-001":"DOC-FSC-SAFE-BLOCK-001";
        String version=complete?"1.0.0":"2026-08";
        String heading=complete?"외부 실행 금지":"신청 전 확인";
        String content=complete
                ?"상담 연결은 안내 계획에만 담으며 전화·문자·예약을 자동 실행하지 않습니다."
                :"안심차단 신청 가능 여부와 세부 범위는 해당 금융회사에서 최종 확인해야 합니다.";
        String sourceHash="sha256:"+suffix.repeat(64);
        String textHash="sha256:"+(complete?"3":"4").repeat(64);
        String runId="96800000-0000-0000-0000-00000000000"+suffix;
        String chunkId="chk_"+(complete?"5":"6").repeat(64);
        String importId="96900000-0000-0000-0000-00000000000"+suffix;
        String passageId=complete?"95000000-0000-0000-0000-000000000002":"95000000-0000-0000-0000-000000000001";
        int chunkCount=complete?1:2;

        jdbc.update("""
                insert into ai_knowledge.ingestion_run(
                  run_id,document_id,version_label,source_hash,as_of,status,extractor_version,chunker_version,
                  chunk_count,warning_codes,failure_code,started_at,finished_at)
                values(?::uuid,?,?,?,'2026-08-24','SUCCEEDED','html-structure-v1','structure-ko-v1',?,
                  '{}',null,'2026-08-24T00:00:00Z','2026-08-24T00:00:01Z')
                """,runId,documentId,version,sourceHash,chunkCount);
        jdbc.update("""
                insert into ai_knowledge.chunk(
                  chunk_id,run_id,document_id,version_label,heading,section_path,page,page_start,page_end,chunk_order,
                  content,text_hash,source_hash,extractor_version,chunker_version,created_at)
                values(?,?::uuid,?,?,?,array['legacy','upgrade'],null,null,null,1,?,?,?,
                  'html-structure-v1','structure-ko-v1','2026-08-24T00:00:01Z')
                """,chunkId,runId,documentId,version,heading,content,textHash,sourceHash);
        jdbc.update("""
                insert into knowledge_ingestion_import(
                  import_id,ingestion_run_id,document_id,version_label,source_hash,as_of,extractor_version,
                  chunker_version,chunk_count,imported_by,imported_at,payload_hash,integrity_hash)
                values(?::uuid,?::uuid,?,?,?,'2026-08-24','html-structure-v1','structure-ko-v1',?,
                  'legacy-importer','2026-08-24T00:00:02Z',?,?)
                """,importId,runId,documentId,version,sourceHash,chunkCount,"7".repeat(64),"8".repeat(64));
        jdbc.update("""
                insert into knowledge_ai_passage_binding(
                  chunk_id,passage_id,import_id,document_id,version_label,chunk_order,section_path,page,page_start,
                  page_end,source_hash,text_hash,extractor_version,chunker_version)
                values(?,?::uuid,?::uuid,?,?,1,array['legacy','upgrade'],null,null,null,?,?,
                  'html-structure-v1','structure-ko-v1')
                """,chunkId,passageId,importId,documentId,version,sourceHash,textHash);
        if(!complete) {
            String secondChunkId="chk_"+"9".repeat(64);
            String secondTextHash="sha256:"+"a".repeat(64);
            String secondPassageId="95000000-0000-0000-0000-000000000069";
            jdbc.update("""
                    insert into ai_knowledge.chunk(
                      chunk_id,run_id,document_id,version_label,heading,section_path,page,page_start,page_end,chunk_order,
                      content,text_hash,source_hash,extractor_version,chunker_version,created_at)
                    values(?,?::uuid,?,?,?,array['legacy','upgrade'],null,null,null,3,?,?,?,
                      'html-structure-v1','structure-ko-v1','2026-08-24T00:00:01Z')
                    """,secondChunkId,runId,documentId,version,"두 번째 손상 청크",
                    "연속되지 않은 레거시 청크 순서는 검증 증명으로 승격하지 않습니다.",secondTextHash,sourceHash);
            jdbc.update("""
                    insert into knowledge_passage(
                      passage_id,document_version_id,passage_order,heading,content,keywords,citation_label)
                    values(?::uuid,'94000000-0000-0000-0000-000000000001',3,?,?,array['레거시','검증'],?)
                    """,secondPassageId,"두 번째 손상 청크",
                    "연속되지 않은 레거시 청크 순서는 검증 증명으로 승격하지 않습니다.",
                    "금융거래 안심차단 안내 근거 — 두 번째 손상 청크");
            jdbc.update("""
                    insert into knowledge_ai_passage_binding(
                      chunk_id,passage_id,import_id,document_id,version_label,chunk_order,section_path,page,page_start,
                      page_end,source_hash,text_hash,extractor_version,chunker_version)
                    values(?,?::uuid,?::uuid,?,?,3,array['legacy','upgrade'],null,null,null,?,?,
                      'html-structure-v1','structure-ko-v1')
                    """,secondChunkId,secondPassageId,importId,documentId,version,sourceHash,secondTextHash);
        }
    }
}
