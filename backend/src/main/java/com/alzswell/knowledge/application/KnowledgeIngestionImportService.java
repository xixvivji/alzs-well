package com.alzswell.knowledge.application;

import static com.alzswell.knowledge.api.KnowledgeImportErrorCode.*;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.common.security.AuditActor;
import com.alzswell.knowledge.api.KnowledgeImportRequests.*;
import com.alzswell.knowledge.api.KnowledgeImportResponses.ImportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeIngestionImportService {
    private final JdbcTemplate jdbc;
    private final MutationIdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public KnowledgeIngestionImportService(JdbcTemplate jdbc,MutationIdempotencyService idempotency,
            ObjectMapper objectMapper,Clock clock) {
        this.jdbc=jdbc;this.idempotency=idempotency;this.objectMapper=objectMapper;this.clock=clock;
    }

    @Transactional
    public ImportResult importIngestion(ImportIngestionCommand command,String key,AuditActor actor) {
        validatePayload(command);
        return idempotency.execute("KNOWLEDGE_INGESTION_IMPORT:"+command.ingestionRunId(),key,command,
                ImportResult.class,IDEMPOTENCY_CONFLICT,()->importOnce(command,actor));
    }

    private ImportResult importOnce(ImportIngestionCommand command,AuditActor actor) {
        Governance governance=governance(command);
        if(!"APPROVED".equals(governance.approvalStatus())||!"ACTIVE".equals(governance.lifecycleStatus())
                ||!governance.sourceHash().equals(command.sourceHash())
                ||command.asOf().isBefore(governance.effectiveFrom())
                ||governance.effectiveTo()!=null&&command.asOf().isAfter(governance.effectiveTo()))
            throw new BusinessException(GOVERNANCE_NOT_READY);
        if(command.chunks().stream().anyMatch(chunk->(governance.title()+" — "+chunk.heading()).length()>400))
            throw new BusinessException(PAYLOAD_INVALID);
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from knowledge_document where document_id=?)",
                Boolean.class,command.documentId()))) throw new BusinessException(CATALOG_CONFLICT);
        UUID importId=UUID.randomUUID();
        OffsetDateTime now=OffsetDateTime.now(clock);
        String payloadHash=sha256(jsonBytes(command));
        String integrityHash=sha256((importId+"|"+command.ingestionRunId()+"|"+command.documentId()+"|"
                +command.versionLabel()+"|"+payloadHash+"|"+actor.legacyActorId()+"|"+now)
                .getBytes(StandardCharsets.UTF_8));
        UUID versionId=stableUuid("knowledge-version-v1|"+command.documentId()+"|"+command.versionLabel());
        List<UUID> passageIds=new ArrayList<>();
        try {
            jdbc.update("""
                    insert into knowledge_ingestion_import values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,importId,command.ingestionRunId(),command.documentId(),command.versionLabel(),command.sourceHash(),
                    command.asOf(),command.extractorVersion(),command.chunkerVersion(),command.chunks().size(),
                    actor.legacyActorId(),now,payloadHash,integrityHash);
            jdbc.update("""
                    insert into knowledge_document(document_id,title,source_type,issuer,source_url,audience,status,
                      effective_from,effective_to,checked_at,current_version,approval_status,lifecycle_status,allowed_roles)
                    values(?,?,?,?,?,?,'APPROVED',?,?,?,?, 'APPROVED','ACTIVE',string_to_array(?,',')::varchar[])
                    """,command.documentId(),governance.title(),catalogSourceType(governance.sourceType()),governance.issuer(),
                    governance.sourceUrl(),governance.audience(),governance.effectiveFrom(),governance.effectiveTo(),
                    governance.checkedAt(),command.versionLabel(),String.join(",",governance.allowedRoles()));
            jdbc.update("""
                    insert into knowledge_document_version(document_version_id,document_id,version_label,content_checksum,
                      published_at,approved_at,superseded_at) values(?,?,?,?,?,?,null)
                    """,versionId,command.documentId(),command.versionLabel(),"sha256:"+payloadHash,
                    governance.approvedAt(),governance.approvedAt());
            for(ImportChunk chunk:command.chunks()) {
                UUID passageId=stableUuid("knowledge-passage-v1|"+chunk.chunkId());
                passageIds.add(passageId);
                jdbc.update("""
                        insert into knowledge_passage(passage_id,document_version_id,passage_order,heading,content,keywords,citation_label)
                        values(?,?,?,?,?,cast('{}' as text[]),?)
                        """,passageId,versionId,chunk.chunkOrder(),chunk.heading(),chunk.text(),
                        governance.title()+" — "+chunk.heading());
                jdbc.update("""
                        insert into knowledge_ai_passage_binding(chunk_id,passage_id,import_id,document_id,version_label,
                          chunk_order,section_path,page,page_start,page_end,source_hash,text_hash,extractor_version,chunker_version)
                        values(?,?,?,?,?,?,string_to_array(?,?)::text[],?,?,?,?,?,?,?)
                        """,chunk.chunkId(),passageId,importId,command.documentId(),command.versionLabel(),chunk.chunkOrder(),
                        String.join("\u001f",chunk.sectionPath()),"\u001f",chunk.page(),chunk.pageStart(),chunk.pageEnd(),
                        chunk.sourceHash(),chunk.textHash(),chunk.extractorVersion(),chunk.chunkerVersion());
            }
        } catch(DuplicateKeyException exception) {throw new BusinessException(CATALOG_CONFLICT);}
        return new ImportResult(importId,command.ingestionRunId(),command.documentId(),command.versionLabel(),
                passageIds.size(),List.copyOf(passageIds),true,now);
    }

    private Governance governance(ImportIngestionCommand command) {
        List<Governance> rows=jdbc.query("""
                select * from knowledge_document_governance
                where document_id=? and version_label=? for share
                """,(rs,n)->{
                    Array roles=rs.getArray("allowed_roles");
                    return new Governance(rs.getString("title"),rs.getString("issuer"),rs.getString("source_type"),
                            rs.getString("source_url"),rs.getString("source_hash"),rs.getString("audience"),
                            List.of((String[])roles.getArray()),rs.getObject("effective_from",LocalDate.class),
                            rs.getObject("effective_to",LocalDate.class),rs.getObject("checked_at",LocalDate.class),
                            rs.getString("approval_status"),rs.getString("lifecycle_status"),
                            rs.getObject("approved_at",OffsetDateTime.class));
                },command.documentId(),command.versionLabel());
        if(rows.size()!=1) throw new BusinessException(GOVERNANCE_NOT_READY);
        return rows.getFirst();
    }

    private void validatePayload(ImportIngestionCommand command) {
        if(!Set.of("structure-ko-v1","pdf-structure-ko-v1").contains(command.chunkerVersion()))
            throw new BusinessException(PAYLOAD_INVALID);
        Set<String> ids=new HashSet<>();
        for(int index=0;index<command.chunks().size();index++) {
            ImportChunk chunk=command.chunks().get(index);
            boolean pdf="pdf-structure-ko-v1".equals(chunk.chunkerVersion());
            boolean pageValid=pdf ? chunk.page()!=null&&Objects.equals(chunk.page(),chunk.pageStart())
                    &&chunk.pageEnd()!=null&&chunk.pageEnd()>=chunk.pageStart()
                    : chunk.page()==null&&chunk.pageStart()==null&&chunk.pageEnd()==null;
            if(chunk.chunkOrder()!=index+1||!ids.add(chunk.chunkId())||!pageValid
                    ||!command.sourceHash().equals(chunk.sourceHash())
                    ||!command.extractorVersion().equals(chunk.extractorVersion())
                    ||!command.chunkerVersion().equals(chunk.chunkerVersion())
                    ||!nfc(chunk.text())||!nfc(chunk.heading())||chunk.sectionPath().stream().anyMatch(value->!nfc(value))
                    ||chunk.sectionPath().stream().anyMatch(value->value.indexOf('\u001f')>=0)
                    ||!textHash(chunk.text()).equals(chunk.textHash())
                    ||!chunkId(command,chunk).equals(chunk.chunkId())) throw new BusinessException(PAYLOAD_INVALID);
        }
    }

    private String chunkId(ImportIngestionCommand command,ImportChunk chunk) {
        return "chk_"+sha256(jsonBytes(List.of(command.documentId(),command.versionLabel(),chunk.sectionPath(),
                chunk.chunkOrder(),chunk.textHash(),chunk.chunkerVersion())));
    }
    private String textHash(String value){return "sha256:"+sha256(value.getBytes(StandardCharsets.UTF_8));}
    private boolean nfc(String value){return Normalizer.isNormalized(value,Normalizer.Form.NFC);}
    private byte[] jsonBytes(Object value){try{return objectMapper.writeValueAsBytes(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
    private UUID stableUuid(String value) {
        byte[] hash;
        try {hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}
        catch(Exception exception){throw new IllegalStateException(exception);}
        hash[6]=(byte)((hash[6]&0x0f)|0x50); hash[8]=(byte)((hash[8]&0x3f)|0x80);
        ByteBuffer buffer=ByteBuffer.wrap(hash);
        return new UUID(buffer.getLong(),buffer.getLong());
    }
    private String catalogSourceType(String sourceType) {
        return switch(sourceType){case "OFFICIAL_EXTERNAL"->"OFFICIAL_PUBLIC";case "SYNTHETIC_FIXTURE"->"SYNTHETIC_DEMO";
            case "INTERNAL_POLICY"->"INTERNAL_POLICY";default->throw new BusinessException(PAYLOAD_INVALID);};
    }

    private record Governance(String title,String issuer,String sourceType,String sourceUrl,String sourceHash,
            String audience,List<String> allowedRoles,LocalDate effectiveFrom,LocalDate effectiveTo,LocalDate checkedAt,
            String approvalStatus,String lifecycleStatus,OffsetDateTime approvedAt) {}
}
