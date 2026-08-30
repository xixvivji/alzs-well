package com.alzswell.knowledge.application;

import static com.alzswell.knowledge.api.KnowledgeGovernanceErrorCode.*;

import com.alzswell.common.audit.AuditTimestamp;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.common.security.AuditActor;
import com.alzswell.knowledge.api.KnowledgeGovernanceRequests.*;
import com.alzswell.knowledge.api.KnowledgeGovernanceResponses.GovernedDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.Array;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGovernanceService {
    private final JdbcTemplate jdbc;
    private final MutationIdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public KnowledgeGovernanceService(JdbcTemplate jdbc, MutationIdempotencyService idempotency,
            ObjectMapper objectMapper, Clock clock) {
        this.jdbc=jdbc; this.idempotency=idempotency; this.objectMapper=objectMapper; this.clock=clock;
    }

    @Transactional
    public GovernedDocument register(RegisterDocumentCommand command,String key,AuditActor actor) {
        validate(command);
        return idempotency.execute("KNOWLEDGE_REGISTER:"+command.documentId()+":"+command.versionLabel(),key,command,
                GovernedDocument.class,IDEMPOTENCY_CONFLICT,()->registerOnce(command,actor));
    }

    @Transactional
    public GovernedDocument publish(String documentId,PublishDocumentCommand command,String key,AuditActor actor) {
        return idempotency.execute("KNOWLEDGE_PUBLISH:"+documentId+":"+command.versionLabel(),key,command,
                GovernedDocument.class,IDEMPOTENCY_CONFLICT,()->publishOnce(documentId,command,actor));
    }

    private GovernedDocument registerOnce(RegisterDocumentCommand command,AuditActor actor) {
        UUID id=UUID.randomUUID();
        OffsetDateTime now=AuditTimestamp.canonical(OffsetDateTime.now(clock));
        try {
            jdbc.update("""
                insert into knowledge_document_governance(
                 workflow_id,document_id,version_label,title,issuer,source_type,source_path,source_url,source_hash,
                 source_transformations,document_type,classification,audience,allowed_roles,effective_from,effective_to,
                 checked_at,usage_rights,approval_status,lifecycle_status,approved_by,approved_at,
                 supersedes_document_id,supersedes_version_label,row_version,registered_by,registered_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,string_to_array(?,','),?,?,?,?,
                 'IN_REVIEW','PENDING_ACTIVATION',null,null,?,?,1,?,?,?)
                """,id,command.documentId(),command.versionLabel(),command.title().trim(),command.issuer().trim(),
                    command.sourceType(),command.sourcePath(),command.sourceUrl(),command.sourceHash(),json(command.sourceTransformations()),
                    command.documentType(),command.classification(),command.audience(),String.join(",",new TreeSet<>(command.allowedRoles())),
                    command.effectiveFrom(),command.effectiveTo(),command.checkedAt(),command.usageRights(),
                    command.supersedesDocumentId(),command.supersedesVersionLabel(),actor.legacyActorId(),now,now);
        } catch(DuplicateKeyException exception) { throw new BusinessException(DUPLICATE); }
        GovernedDocument result=find(id);
        event(result,"REGISTERED_FOR_REVIEW",actor,null,now);
        return result;
    }

    private GovernedDocument publishOnce(String documentId,PublishDocumentCommand command,AuditActor actor) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,documentId);
        Row target=locked(documentId,command.versionLabel());
        if(target.version()!=command.expectedVersion()) throw new BusinessException(VERSION_CONFLICT);
        if(!"IN_REVIEW".equals(target.approvalStatus())||!"PENDING_ACTIVATION".equals(target.lifecycleStatus()))
            throw new BusinessException(STATE_CONFLICT);
        if("REVIEW_REQUIRED".equals(target.usageRights())) throw new BusinessException(USAGE_REVIEW_REQUIRED);
        OffsetDateTime now=AuditTimestamp.canonical(OffsetDateTime.now(clock));
        List<Row> active=jdbc.query("select * from knowledge_document_governance where document_id=? and lifecycle_status='ACTIVE' for update",
                this::row,documentId);
        if(!active.isEmpty()) {
            Row previous=active.getFirst();
            if(!Objects.equals(target.supersedesDocumentId(),previous.documentId())
                    || !Objects.equals(target.supersedesVersionLabel(),previous.versionLabel())) throw new BusinessException(INVALID_SUPERSEDES);
        } else {
            String legacyHead=currentCatalogVersion(documentId);
            if(legacyHead==null) {
                if(target.supersedesDocumentId()!=null) throw new BusinessException(INVALID_SUPERSEDES);
            } else if(!documentId.equals(target.supersedesDocumentId())
                    ||!legacyHead.equals(target.supersedesVersionLabel())) {
                throw new BusinessException(INVALID_SUPERSEDES);
            }
        }
        List<Row> pending=jdbc.query("""
                select * from knowledge_document_governance
                 where document_id=? and approval_status='APPROVED' and lifecycle_status='PENDING_ACTIVATION'
                   and workflow_id<>? for update
                """,this::row,documentId,target.workflowId());
        if(pending.size()>1) throw new BusinessException(STATE_CONFLICT);
        if(!pending.isEmpty()) {
            Row replaced=pending.getFirst();
            if(!Objects.equals(replaced.supersedesDocumentId(),target.supersedesDocumentId())
                    ||!Objects.equals(replaced.supersedesVersionLabel(),target.supersedesVersionLabel())) {
                throw new BusinessException(INVALID_SUPERSEDES);
            }
            int retired=jdbc.update("""
                    update knowledge_document_governance
                       set lifecycle_status='RETIRED',row_version=row_version+1,updated_at=?
                     where workflow_id=? and approval_status='APPROVED' and lifecycle_status='PENDING_ACTIVATION'
                    """,now,replaced.workflowId());
            if(retired!=1) throw new BusinessException(VERSION_CONFLICT);
            event(find(replaced.workflowId()),"RETIRED",actor,
                    "REPLACED_BY:"+target.versionLabel(),now);
        }
        int changed;
        try {
            changed=jdbc.update("""
                update knowledge_document_governance set approval_status='APPROVED',lifecycle_status='PENDING_ACTIVATION',
                 approved_by=?,approved_at=?,row_version=row_version+1,updated_at=?
                 where workflow_id=? and row_version=? and approval_status='IN_REVIEW' and lifecycle_status='PENDING_ACTIVATION'
                """,actor.legacyActorId(),now,now,target.workflowId(),command.expectedVersion());
        } catch(DuplicateKeyException exception) {
            throw new BusinessException(STATE_CONFLICT);
        }
        if(changed!=1) throw new BusinessException(VERSION_CONFLICT);
        GovernedDocument result=find(target.workflowId());
        event(result,"PUBLISHED",actor,command.approvalReference(),now);
        return result;
    }

    void activateVerifiedImport(String documentId,String versionLabel,String previousVersion,
            AuditActor actor,OffsetDateTime now) {
        Row target=locked(documentId,versionLabel);
        if(!"APPROVED".equals(target.approvalStatus())
                ||!"PENDING_ACTIVATION".equals(target.lifecycleStatus())) {
            throw new BusinessException(STATE_CONFLICT);
        }
        if(previousVersion==null) {
            if(target.supersedesDocumentId()!=null) throw new BusinessException(INVALID_SUPERSEDES);
        } else {
            if(!documentId.equals(target.supersedesDocumentId())
                    ||!previousVersion.equals(target.supersedesVersionLabel())) {
                throw new BusinessException(INVALID_SUPERSEDES);
            }
            List<Row> previousRows=jdbc.query("""
                    select * from knowledge_document_governance
                    where document_id=? and version_label=? for update
                    """,this::row,documentId,previousVersion);
            if(previousRows.size()>1) throw new BusinessException(STATE_CONFLICT);
            if(!previousRows.isEmpty()) {
                Row previous=previousRows.getFirst();
                if(!"APPROVED".equals(previous.approvalStatus())
                        ||!"ACTIVE".equals(previous.lifecycleStatus())) {
                    throw new BusinessException(STATE_CONFLICT);
                }
                int superseded=jdbc.update("""
                        update knowledge_document_governance
                           set lifecycle_status='SUPERSEDED',row_version=row_version+1,updated_at=?
                         where workflow_id=? and approval_status='APPROVED' and lifecycle_status='ACTIVE'
                        """,now,previous.workflowId());
                if(superseded!=1) throw new BusinessException(VERSION_CONFLICT);
                event(find(previous.workflowId()),"SUPERSEDED",actor,"VERIFIED_AI_INGESTION",now);
            } else if(!legacyCatalogHeadExists(documentId,previousVersion)) {
                throw new BusinessException(INVALID_SUPERSEDES);
            }
        }
        int activated=jdbc.update("""
                update knowledge_document_governance
                   set lifecycle_status='ACTIVE',row_version=row_version+1,updated_at=?
                 where workflow_id=? and approval_status='APPROVED' and lifecycle_status='PENDING_ACTIVATION'
                """,now,target.workflowId());
        if(activated!=1) throw new BusinessException(VERSION_CONFLICT);
        event(find(target.workflowId()),"ACTIVATED",actor,"VERIFIED_AI_INGESTION",now);
    }

    private void validate(RegisterDocumentCommand command) {
        if(command.effectiveTo()!=null&&command.effectiveTo().isBefore(command.effectiveFrom())) throw new BusinessException(INVALID_PERIOD);
        if(command.checkedAt().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))))) throw new BusinessException(INVALID_PERIOD);
        if((command.supersedesDocumentId()==null)!=(command.supersedesVersionLabel()==null)) throw new BusinessException(INVALID_SUPERSEDES);
        if(new HashSet<>(command.allowedRoles()).size()!=command.allowedRoles().size()) throw new BusinessException(INVALID_ROLES);
        boolean validSource=switch(command.sourceType()) {
            case "OFFICIAL_EXTERNAL" -> command.sourcePath().startsWith("knowledge/official-source/")&&command.sourceUrl()!=null;
            case "INTERNAL_POLICY" -> command.sourcePath().startsWith("knowledge/internal-policy/");
            case "SYNTHETIC_FIXTURE" -> command.sourcePath().startsWith("contracts/knowledge/fixtures/")
                    && command.sourceUrl()==null&&"SYNTHETIC_UNRESTRICTED".equals(command.usageRights());
            default -> false;
        };
        if(!validSource) throw new BusinessException(INVALID_SOURCE);
        if(command.sourceUrl()!=null) {
            try {
                URI uri=URI.create(command.sourceUrl());
                if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)
                    throw new BusinessException(INVALID_SOURCE);
            } catch(IllegalArgumentException exception) { throw new BusinessException(INVALID_SOURCE); }
        }
    }

    private Row locked(String documentId,String versionLabel) {
        List<Row> rows=jdbc.query("select * from knowledge_document_governance where document_id=? and version_label=? for update",
                this::row,documentId,versionLabel);
        if(rows.size()!=1) throw new BusinessException(NOT_FOUND);
        return rows.getFirst();
    }

    private GovernedDocument find(UUID id) {
        List<GovernedDocument> rows=jdbc.query("select * from knowledge_document_governance where workflow_id=?",
                (rs,n)->map(row(rs,n)),id);
        if(rows.size()!=1) throw new BusinessException(NOT_FOUND);
        return rows.getFirst();
    }

    private Row row(java.sql.ResultSet rs,int n)throws java.sql.SQLException {
        Array roles=rs.getArray("allowed_roles");
        return new Row(rs.getObject("workflow_id",UUID.class),rs.getString("document_id"),rs.getString("version_label"),
                rs.getString("title"),rs.getString("issuer"),rs.getString("source_type"),rs.getString("source_path"),
                rs.getString("source_url"),rs.getString("source_hash"),rs.getString("source_transformations"),rs.getString("document_type"),rs.getString("classification"),
                rs.getString("audience"),List.of((String[])roles.getArray()),rs.getObject("effective_from",LocalDate.class),
                rs.getObject("effective_to",LocalDate.class),rs.getObject("checked_at",LocalDate.class),rs.getString("usage_rights"),
                rs.getString("approval_status"),rs.getString("lifecycle_status"),rs.getString("approved_by"),
                rs.getObject("approved_at",OffsetDateTime.class),rs.getLong("row_version"),rs.getObject("updated_at",OffsetDateTime.class),
                rs.getString("supersedes_document_id"),rs.getString("supersedes_version_label"));
    }

    private GovernedDocument map(Row r) {
        boolean ready="APPROVED".equals(r.approvalStatus())&&"PENDING_ACTIVATION".equals(r.lifecycleStatus());
        return new GovernedDocument(r.workflowId(),r.documentId(),r.versionLabel(),r.title(),r.issuer(),r.sourceType(),
                r.sourcePath(),r.sourceUrl(),r.sourceHash(),transformations(r.sourceTransformationsJson()),r.documentType(),r.classification(),r.audience(),r.allowedRoles(),
                r.effectiveFrom(),r.effectiveTo(),r.checkedAt(),r.usageRights(),r.approvalStatus(),r.lifecycleStatus(),
                r.approvedBy(),r.approvedAt(),r.version(),ready,false,false,r.updatedAt());
    }

    private String currentCatalogVersion(String documentId) {
        List<String> rows=jdbc.query("select current_version from knowledge_document where document_id=? for share",
                (rs,n)->rs.getString(1),documentId);
        return rows.isEmpty()?null:rows.getFirst();
    }

    private boolean legacyCatalogHeadExists(String documentId,String versionLabel) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                  select 1 from knowledge_document document
                  join knowledge_document_version version
                    on version.document_id=document.document_id and version.version_label=document.current_version
                  where document.document_id=? and document.current_version=? and version.superseded_at is null
                    and not exists(
                      select 1 from knowledge_document_governance governance
                      where governance.document_id=document.document_id
                        and governance.version_label=document.current_version
                    )
                )
                """,Boolean.class,documentId,versionLabel));
    }

    private void event(GovernedDocument state,String type,AuditActor actor,String approvalReference,OffsetDateTime now) {
        String snapshot=json(state); UUID eventId=UUID.randomUUID();
        jdbc.update("insert into knowledge_governance_event values(?,?,?,?,?,?,?,?::jsonb,?,?)",eventId,state.workflowId(),
                state.documentId(),state.versionLabel(),type,actor.legacyActorId(),approvalReference,snapshot,now,
                sha256(eventId+"|"+state.workflowId()+"|"+type+"|"+approvalReference+"|"+snapshot+"|"+now));
    }

    private String json(Object value) { try{return objectMapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
    private List<SourceTransformation> transformations(String value) {
        try { return objectMapper.readValue(value,objectMapper.getTypeFactory().constructCollectionType(List.class,SourceTransformation.class)); }
        catch(Exception e) { throw new IllegalStateException("출처 변환 메타데이터를 복원할 수 없습니다.",e); }
    }
    private String sha256(String value) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }

    private record Row(UUID workflowId,String documentId,String versionLabel,String title,String issuer,String sourceType,
            String sourcePath,String sourceUrl,String sourceHash,String sourceTransformationsJson,String documentType,String classification,String audience,
            List<String> allowedRoles,LocalDate effectiveFrom,LocalDate effectiveTo,LocalDate checkedAt,String usageRights,
            String approvalStatus,String lifecycleStatus,String approvedBy,OffsetDateTime approvedAt,long version,
            OffsetDateTime updatedAt,String supersedesDocumentId,String supersedesVersionLabel) {}
}
