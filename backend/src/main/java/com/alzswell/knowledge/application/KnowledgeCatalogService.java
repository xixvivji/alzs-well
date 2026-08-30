package com.alzswell.knowledge.application;

import static com.alzswell.knowledge.api.KnowledgeErrorCode.DOCUMENT_NOT_FOUND;
import static com.alzswell.knowledge.api.KnowledgeErrorCode.PASSAGE_NOT_FOUND;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.knowledge.api.KnowledgeRequests.SearchCommand;
import com.alzswell.knowledge.api.KnowledgeResponses.*;
import com.alzswell.knowledge.application.KnowledgeAccessPolicy.AccessContext;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.RetrievalQuery;
import java.sql.Array;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeCatalogService {
    private static final ZoneId SERVICE_ZONE=ZoneId.of("Asia/Seoul");
    private static final String VERIFIED_CURRENT_PASSAGE_JOINS="""
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
            """;
    private static final String VERIFIED_CURRENT_POLICY="""
            d.approval_status='APPROVED' and d.lifecycle_status='ACTIVE'
              and g.approval_status='APPROVED' and g.lifecycle_status='ACTIVE'
              and d.title=g.title and d.issuer=g.issuer
              and d.source_url is not distinct from g.source_url
              and d.audience=g.audience and d.allowed_roles=g.allowed_roles
              and d.effective_from=g.effective_from and d.effective_to is not distinct from g.effective_to
              and d.checked_at=g.checked_at
              and d.effective_from<=:asOf and (d.effective_to is null or d.effective_to>=:asOf)
              and g.effective_from<=:asOf and (g.effective_to is null or g.effective_to>=:asOf)
              and (:requestedAudience='' or g.audience in ('BOTH',:requestedAudience))
              and (g.audience='BOTH' or g.audience=any(string_to_array(:audiences,',')))
              and g.allowed_roles && string_to_array(:roles,',')::varchar[]
            """;
    private final JdbcClient jdbc;
    private final KnowledgeAccessPolicy accessPolicy;
    private final KnowledgeRetrievalPort retrievalPort;
    private final VerifiedKnowledgeCitationResolver citationResolver;
    private final KnowledgeAccessAuditService audit;
    private final Clock clock;

    public KnowledgeCatalogService(JdbcClient jdbc,KnowledgeAccessPolicy accessPolicy,
            KnowledgeRetrievalPort retrievalPort,VerifiedKnowledgeCitationResolver citationResolver,
            KnowledgeAccessAuditService audit,Clock clock) {
        this.jdbc=jdbc; this.accessPolicy=accessPolicy; this.retrievalPort=retrievalPort;
        this.citationResolver=citationResolver; this.audit=audit; this.clock=clock;
    }

    @Transactional
    public DocumentList documents(String requestedAudience,LocalDate asOf,Authentication authentication) {
        AccessContext access=accessPolicy.resolve(authentication,"KNOWLEDGE_READ");
        LocalDate date=resolveAsOf(asOf);
        if(!access.allowsAudience(requestedAudience)) {
            audit.record("DOCUMENT_LIST",access,null,null,date,List.of(),"FILTERED",
                    Map.of("requestedAudience",requestedAudience,"reason","AUDIENCE_NARROWING_NOT_ALLOWED"));
            return new DocumentList(List.of(),0);
        }
        String audience=requestedAudience==null?"":requestedAudience;
        String sql="""
                select distinct d.*
                from knowledge_passage p
                """+VERIFIED_CURRENT_PASSAGE_JOINS+"""
                where
                """+VERIFIED_CURRENT_POLICY+"""
                order by d.title,d.document_id
                """;
        List<DocumentSummary> items=jdbc.sql(sql).param("asOf",date).param("requestedAudience",audience)
                .param("audiences",access.audiencesCsv()).param("roles",access.rolesCsv())
                .query(this::summary).list();
        List<String> ids=items.stream().map(DocumentSummary::documentId).toList();
        audit.record("DOCUMENT_LIST",access,null,null,date,ids,"ALLOWED",
                Map.of("requestedAudience",requestedAudience==null?"ALL_ALLOWED":requestedAudience,"total",items.size()));
        return new DocumentList(items,items.size());
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public DocumentDetail document(String documentId,Authentication authentication) {
        AccessContext access=accessPolicy.resolve(authentication,"KNOWLEDGE_READ");
        LocalDate date=resolveAsOf(null);
        Optional<DocumentDetail> result=documentRow(documentId,date,access);
        audit.record("DOCUMENT_DETAIL",access,documentId,null,date,
                result.isPresent()?List.of(documentId):List.of(),result.isPresent()?"ALLOWED":"NOT_FOUND",Map.of());
        return result.orElseThrow(()->new BusinessException(DOCUMENT_NOT_FOUND));
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public VersionList versions(String documentId,Authentication authentication) {
        AccessContext access=accessPolicy.resolve(authentication,"KNOWLEDGE_READ");
        LocalDate date=resolveAsOf(null);
        if(documentRow(documentId,date,access).isEmpty()) {
            audit.record("VERSION_LIST",access,documentId,null,date,List.of(),"NOT_FOUND",Map.of());
            throw new BusinessException(DOCUMENT_NOT_FOUND);
        }
        List<DocumentVersion> items=jdbc.sql("""
                select distinct v.* from knowledge_document_version v
                join knowledge_document_governance g
                  on g.document_id=v.document_id and g.version_label=v.version_label
                join knowledge_ai_passage_binding b
                  on b.document_id=v.document_id and b.version_label=v.version_label
                join knowledge_ingestion_import i
                  on i.import_id=b.import_id and i.document_id=b.document_id
                 and i.version_label=b.version_label and i.source_hash=b.source_hash
                 and i.ai_proof_version='AI_DB_SNAPSHOT_V1' and i.ai_verified_at is not null
                where v.document_id=:documentId and g.approval_status='APPROVED'
                  and g.lifecycle_status in ('ACTIVE','SUPERSEDED') and g.source_hash=b.source_hash
                  and (g.audience='BOTH' or g.audience=any(string_to_array(:audiences,',')))
                  and g.allowed_roles && string_to_array(:roles,',')::varchar[]
                order by v.approved_at desc,v.document_version_id
                """).param("documentId",documentId).param("audiences",access.audiencesCsv())
                .param("roles",access.rolesCsv()).query((rs,n)->new DocumentVersion(rs.getObject("document_version_id",UUID.class),
                        rs.getString("version_label"),rs.getString("content_checksum"),
                        rs.getObject("published_at",OffsetDateTime.class),rs.getObject("approved_at",OffsetDateTime.class),
                        rs.getObject("superseded_at",OffsetDateTime.class))).list();
        audit.record("VERSION_LIST",access,documentId,null,date,
                items.stream().map(item->item.documentVersionId().toString()).toList(),"ALLOWED",Map.of("total",items.size()));
        return new VersionList(documentId,items,items.size());
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public Passage passage(UUID passageId,Authentication authentication) {
        AccessContext access=accessPolicy.resolve(authentication,"KNOWLEDGE_READ");
        LocalDate date=resolveAsOf(null);
        String sql="""
                select p.*,d.document_id,d.source_url,d.effective_from,d.effective_to,v.version_label
                from knowledge_passage p
                """+VERIFIED_CURRENT_PASSAGE_JOINS+"""
                where p.passage_id=:passageId and
                """+VERIFIED_CURRENT_POLICY;
        Optional<Passage> result=jdbc.sql(sql).param("passageId",passageId).param("asOf",date)
                .param("requestedAudience","")
                .param("audiences",access.audiencesCsv()).param("roles",access.rolesCsv())
                .query(this::mapPassage).optional();
        audit.record("PASSAGE_DETAIL",access,passageId.toString(),null,date,
                result.isPresent()?List.of(passageId.toString()):List.of(),result.isPresent()?"ALLOWED":"NOT_FOUND",Map.of());
        return result.orElseThrow(()->new BusinessException(PASSAGE_NOT_FOUND));
    }

    @Transactional
    public SearchResult search(SearchCommand command,Authentication authentication) {
        AccessContext access=accessPolicy.resolve(authentication,"KNOWLEDGE_SEARCH");
        LocalDate date=resolveAsOf(command.asOf());
        KnowledgeRetrievalPort.RetrievalResult retrieval=retrievalPort.retrieve(new RetrievalQuery(
                command.query(),date,command.audience(),access.roles(),access.audiences(),command.resolvedLimit()));
        List<SearchHit> hits=retrieval.hits();
        boolean vectorSearchUsed="INTERNAL_RAG_HYBRID".equals(retrieval.retrievalMode());
        List<String> ids=hits.stream().map(hit->hit.passage().passageId().toString()).toList();
        String outcome=command.audience()!=null&&!access.allowsAudience(command.audience())?"FILTERED":"ALLOWED";
        audit.record("SEARCH",access,null,command.query(),date,ids,outcome,
                Map.of("requestedAudience",command.audience()==null?"ALL_ALLOWED":command.audience(),
                        "limit",command.resolvedLimit(),"retrievalMode",retrieval.retrievalMode(),
                        "fallbackUsed",retrieval.fallbackUsed(),"rejectedCitations",retrieval.rejectedCitations(),
                        "vectorSearchUsed",vectorSearchUsed,"total",hits.size()));
        return new SearchResult(command.query(),date,command.audience(),hits,hits.size(),false,vectorSearchUsed);
    }

    @Transactional
    public GuidanceCandidates guidanceCandidates(String reasonCode,Authentication authentication) {
        AccessContext access=accessPolicy.resolve(authentication,"GUIDANCE_CANDIDATE_READ");
        LocalDate date=resolveAsOf(null);
        Set<String> allowed=switch(reasonCode) {
            case "MISSED_RECURRING_PAYMENT","REPEATED_CONFIRMATION"->Set.of("BANK_CONSULTATION");
            case "DUPLICATE_TRANSFER"->Set.of("SAFE_BLOCK_INFO","BANK_CONSULTATION");
            default->Set.of();
        };
        if(allowed.isEmpty()) return new GuidanceCandidates(reasonCode,"context-policy-v1.0.0",List.of(),0);
        List<GuidanceCandidate> items=jdbc.sql("""
                select * from protection_action_catalog
                where action_code in ('SAFE_BLOCK_INFO','BANK_CONSULTATION') order by display_order
                """).query((rs,n)->new GuidanceAction(rs.getString("action_code"),rs.getString("title"),
                        rs.getString("eligibility_summary"),rs.getString("issuer"),rs.getString("source_url"),
                        rs.getString("execution_type"))).list().stream()
                .filter(item->allowed.contains(item.actionCode()))
                .map(item->citationResolver.firstActionCitation(item.actionCode(),access,date)
                        .map(citation->new GuidanceCandidate(item.actionCode(),item.title(),item.eligibilitySummary(),
                                item.issuer(),item.sourceUrl(),item.executionType(),"GUIDANCE_ALLOWED",citation,false)))
                .flatMap(Optional::stream).toList();
        return new GuidanceCandidates(reasonCode,"context-policy-v1.0.0",items,items.size());
    }

    private Optional<DocumentDetail> documentRow(String documentId,LocalDate date,AccessContext access) {
        String sql="""
                select distinct d.*,v.content_checksum
                from knowledge_passage p
                """+VERIFIED_CURRENT_PASSAGE_JOINS+"""
                where d.document_id=:documentId and
                """+VERIFIED_CURRENT_POLICY;
        return jdbc.sql(sql).param("documentId",documentId).param("asOf",date)
                .param("requestedAudience","")
                .param("audiences",access.audiencesCsv()).param("roles",access.rolesCsv())
                .query((rs,n)->new DocumentDetail(summary(rs,n),rs.getString("source_url"),
                        rs.getString("content_checksum"),true)).optional();
    }

    private LocalDate resolveAsOf(LocalDate asOf) {
        return asOf==null?LocalDate.now(clock.withZone(SERVICE_ZONE)):asOf;
    }
    private DocumentSummary summary(java.sql.ResultSet rs,int n)throws java.sql.SQLException {
        return new DocumentSummary(rs.getString("document_id"),rs.getString("title"),rs.getString("source_type"),
                rs.getString("issuer"),rs.getString("audience"),rs.getString("status"),
                rs.getObject("effective_from",LocalDate.class),rs.getObject("effective_to",LocalDate.class),
                rs.getObject("checked_at",LocalDate.class),rs.getString("current_version"));
    }
    private Passage mapPassage(java.sql.ResultSet rs,int n)throws java.sql.SQLException {
        Array array=rs.getArray("keywords");
        List<String> keywords=array==null?List.of():List.of((String[])array.getArray());
        return new Passage(rs.getObject("passage_id",UUID.class),rs.getString("document_id"),rs.getString("version_label"),
                rs.getString("heading"),rs.getString("content"),keywords,rs.getString("citation_label"),
                rs.getString("source_url"),rs.getObject("effective_from",LocalDate.class),rs.getObject("effective_to",LocalDate.class));
    }
    private record GuidanceAction(String actionCode,String title,String eligibilitySummary,String issuer,
            String sourceUrl,String executionType) {}
}
