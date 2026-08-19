package com.alzswell.knowledge.application;

import static com.alzswell.knowledge.api.KnowledgeErrorCode.*;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.knowledge.api.KnowledgeRequests.SearchCommand;
import com.alzswell.knowledge.api.KnowledgeResponses.*;
import java.sql.Array;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeCatalogService {
    private final JdbcClient jdbc;
    public KnowledgeCatalogService(JdbcClient jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public DocumentList documents(String audience,LocalDate asOf){
        String resolvedAudience=audience==null?"STAFF":audience;
        LocalDate date=asOf==null?LocalDate.now(ZoneOffset.UTC):asOf;
        List<DocumentSummary> items=jdbc.sql("""
                select * from knowledge_document where status='APPROVED'
                  and audience in ('BOTH',:audience) and effective_from<=:asOf
                  and (effective_to is null or effective_to>=:asOf)
                order by title,document_id
                """).param("audience",resolvedAudience).param("asOf",date).query(this::summary).list();
        return new DocumentList(items,items.size());
    }

    @Transactional(readOnly=true)
    public DocumentDetail document(String documentId){
        return jdbc.sql("""
                select d.*,v.content_checksum from knowledge_document d
                join knowledge_document_version v on v.document_id=d.document_id and v.version_label=d.current_version
                where d.document_id=? and d.status='APPROVED'
                """).param(documentId).query((rs,n)->new DocumentDetail(summary(rs,n),rs.getString("source_url"),
                        rs.getString("content_checksum"),true)).optional()
                .orElseThrow(()->new BusinessException(DOCUMENT_NOT_FOUND));
    }

    @Transactional(readOnly=true)
    public VersionList versions(String documentId){
        document(documentId);
        List<DocumentVersion> items=jdbc.sql("""
                select * from knowledge_document_version where document_id=? order by approved_at desc,document_version_id
                """).param(documentId).query((rs,n)->new DocumentVersion(rs.getObject("document_version_id",UUID.class),
                        rs.getString("version_label"),rs.getString("content_checksum"),
                        rs.getObject("published_at",OffsetDateTime.class),rs.getObject("approved_at",OffsetDateTime.class),
                        rs.getObject("superseded_at",OffsetDateTime.class))).list();
        return new VersionList(documentId,items,items.size());
    }

    @Transactional(readOnly=true)
    public Passage passage(UUID passageId){
        return jdbc.sql("""
                select p.*,d.document_id,d.source_url,d.effective_from,d.effective_to,v.version_label
                from knowledge_passage p join knowledge_document_version v on v.document_version_id=p.document_version_id
                join knowledge_document d on d.document_id=v.document_id
                where p.passage_id=? and d.status='APPROVED' and v.version_label=d.current_version
                """).param(passageId).query(this::mapPassage).optional()
                .orElseThrow(()->new BusinessException(PASSAGE_NOT_FOUND));
    }

    @Transactional(readOnly=true)
    public SearchResult search(SearchCommand command){
        List<String> terms=Arrays.stream(command.query().toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(term->term.length()>=2).distinct().toList();
        List<SearchHit> hits=jdbc.sql("""
                select p.*,d.document_id,d.source_url,d.effective_from,d.effective_to,v.version_label
                from knowledge_passage p join knowledge_document_version v on v.document_version_id=p.document_version_id
                join knowledge_document d on d.document_id=v.document_id
                where d.status='APPROVED' and v.version_label=d.current_version
                  and d.audience in ('BOTH',:audience) and d.effective_from<=:asOf
                  and (d.effective_to is null or d.effective_to>=:asOf)
                order by d.document_id,p.passage_order
                """).param("audience",command.audience()).param("asOf",command.asOf()).query(this::mapPassage).list().stream()
                .map(item->new SearchHit(item,score(item,terms),"DETERMINISTIC_KEYWORD"))
                .filter(hit->hit.matchedKeywordCount()>0)
                .sorted(Comparator.comparingInt(SearchHit::matchedKeywordCount).reversed()
                        .thenComparing(hit->hit.passage().passageId()))
                .limit(command.resolvedLimit()).toList();
        return new SearchResult(command.query(),command.asOf(),command.audience(),hits,hits.size(),false,false);
    }

    @Transactional(readOnly=true)
    public GuidanceCandidates guidanceCandidates(String reasonCode){
        Set<String> allowed=switch(reasonCode){
            case "MISSED_RECURRING_PAYMENT","REPEATED_CONFIRMATION"->Set.of("BANK_CONSULTATION");
            case "DUPLICATE_TRANSFER"->Set.of("SAFE_BLOCK_INFO","BANK_CONSULTATION");
            default->Set.of();
        };
        if(allowed.isEmpty()) return new GuidanceCandidates(reasonCode,"context-policy-v1.0.0",List.of(),0);
        List<GuidanceCandidate> items=jdbc.sql("""
                select * from protection_action_catalog where action_code in ('SAFE_BLOCK_INFO','BANK_CONSULTATION')
                order by display_order
                """).query((rs,n)->{
                    String code=rs.getString("action_code");
                    UUID citation="SAFE_BLOCK_INFO".equals(code)
                            ?UUID.fromString("95000000-0000-0000-0000-000000000001")
                            :UUID.fromString("95000000-0000-0000-0000-000000000002");
                    return new GuidanceCandidate(code,rs.getString("title"),rs.getString("eligibility_summary"),
                            rs.getString("issuer"),rs.getString("source_url"),rs.getString("execution_type"),
                            "GUIDANCE_ALLOWED",citation,false);
                }).list().stream().filter(item->allowed.contains(item.actionCode())).toList();
        return new GuidanceCandidates(reasonCode,"context-policy-v1.0.0",items,items.size());
    }

    private int score(Passage passage,List<String> terms){
        String searchable=(passage.heading()+" "+passage.content()+" "+String.join(" ",passage.keywords())).toLowerCase(Locale.ROOT);
        return (int)terms.stream().filter(searchable::contains).count();
    }
    private DocumentSummary summary(java.sql.ResultSet rs,int n)throws java.sql.SQLException{
        return new DocumentSummary(rs.getString("document_id"),rs.getString("title"),rs.getString("source_type"),
                rs.getString("issuer"),rs.getString("audience"),rs.getString("status"),
                rs.getObject("effective_from",LocalDate.class),rs.getObject("effective_to",LocalDate.class),
                rs.getObject("checked_at",LocalDate.class),rs.getString("current_version"));
    }
    private Passage mapPassage(java.sql.ResultSet rs,int n)throws java.sql.SQLException{
        Array array=rs.getArray("keywords");
        List<String> keywords=array==null?List.of():List.of((String[])array.getArray());
        return new Passage(rs.getObject("passage_id",UUID.class),rs.getString("document_id"),rs.getString("version_label"),
                rs.getString("heading"),rs.getString("content"),keywords,rs.getString("citation_label"),
                rs.getString("source_url"),rs.getObject("effective_from",LocalDate.class),rs.getObject("effective_to",LocalDate.class));
    }
}
