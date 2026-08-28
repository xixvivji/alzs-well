package com.alzswell.knowledge.application;

import com.alzswell.knowledge.application.KnowledgeAccessPolicy.AccessContext;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class VerifiedKnowledgeCitationResolver {
    private static final ZoneId SERVICE_ZONE=ZoneId.of("Asia/Seoul");
    private static final Map<String,String> ACTION_DOCUMENTS=Map.of(
            "SAFE_BLOCK_INFO","DOC-FSC-SAFE-BLOCK-001",
            "BANK_CONSULTATION","DOC-SYN-BANK-SUPPORT-001");
    private final JdbcClient jdbc;
    private final KnowledgeAccessPolicy accessPolicy;
    private final KnowledgeAccessAuditService audit;
    private final Clock clock;

    public VerifiedKnowledgeCitationResolver(JdbcClient jdbc,KnowledgeAccessPolicy accessPolicy,
            KnowledgeAccessAuditService audit,Clock clock) {
        this.jdbc=jdbc;this.accessPolicy=accessPolicy;this.audit=audit;this.clock=clock;
    }

    public Optional<UUID> firstActionCitation(String actionCode,Authentication authentication,String permission) {
        return firstActionCitation(actionCode,accessPolicy.resolve(authentication,permission),
                LocalDate.now(clock.withZone(SERVICE_ZONE)));
    }

    Optional<UUID> firstActionCitation(String actionCode,AccessContext access,LocalDate asOf) {
        String documentId=ACTION_DOCUMENTS.get(actionCode);
        Optional<UUID> result=documentId==null?Optional.empty():jdbc.sql("""
                select p.passage_id
                from knowledge_passage p
                join knowledge_document_version v on v.document_version_id=p.document_version_id
                join knowledge_document d on d.document_id=v.document_id and d.current_version=v.version_label
                join knowledge_document_governance g
                  on g.document_id=v.document_id and g.version_label=v.version_label
                join knowledge_ai_passage_binding b
                  on b.passage_id=p.passage_id and b.document_id=v.document_id
                 and b.version_label=v.version_label and b.chunk_order=p.passage_order
                 and b.source_hash=g.source_hash
                join knowledge_ingestion_import i
                  on i.import_id=b.import_id and i.document_id=b.document_id
                 and i.version_label=b.version_label and i.source_hash=b.source_hash
                 and i.ai_proof_version='AI_DB_SNAPSHOT_V1' and i.ai_verified_at is not null
                where d.document_id=:documentId
                  and d.approval_status='APPROVED' and d.lifecycle_status='ACTIVE'
                  and g.approval_status='APPROVED' and g.lifecycle_status='ACTIVE'
                  and d.title=g.title and d.issuer=g.issuer
                  and d.source_url is not distinct from g.source_url
                  and d.audience=g.audience and d.allowed_roles=g.allowed_roles
                  and d.effective_from=g.effective_from and d.effective_to is not distinct from g.effective_to
                  and d.checked_at=g.checked_at
                  and d.effective_from<=:asOf and (d.effective_to is null or d.effective_to>=:asOf)
                  and g.effective_from<=:asOf and (g.effective_to is null or g.effective_to>=:asOf)
                  and (g.audience='BOTH' or g.audience=any(string_to_array(:audiences,',')))
                  and g.allowed_roles && string_to_array(:roles,',')::varchar[]
                order by p.passage_order,p.passage_id
                limit 1
                """).param("documentId",documentId).param("asOf",asOf)
                .param("audiences",access.audiencesCsv()).param("roles",access.rolesCsv())
                .query(UUID.class).optional();
        String eventType=switch(access.permission()) {
            case "GUIDANCE_CANDIDATE_READ"->"GUIDANCE_CITATION";
            case "PROTECTION_ACTION_READ"->"PROTECTION_ACTION_CITATION";
            default->throw new IllegalArgumentException("Unsupported citation resolution permission");
        };
        audit.record(eventType,access,actionCode,null,asOf,
                result.map(value->List.of(value.toString())).orElseGet(List::of),
                result.isPresent()?"ALLOWED":"NOT_FOUND",
                Map.of("queryType",eventType,"actionCode",actionCode,
                        "documentId",documentId==null?"UNMAPPED":documentId,
                        "requiredProofVersion","AI_DB_SNAPSHOT_V1"));
        return result;
    }
}
