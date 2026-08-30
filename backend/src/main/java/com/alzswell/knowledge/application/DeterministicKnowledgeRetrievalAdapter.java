package com.alzswell.knowledge.application;

import com.alzswell.knowledge.api.KnowledgeResponses.*;
import java.sql.Array;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DeterministicKnowledgeRetrievalAdapter implements KnowledgeRetrievalPort {
    private static final int MAX_DATABASE_CANDIDATES=200;
    private static final int MAX_QUERY_TERMS=24;
    private final JdbcClient jdbc;
    public DeterministicKnowledgeRetrievalAdapter(JdbcClient jdbc){this.jdbc=jdbc;}

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if(query.requestedAudience()!=null&&!query.requesterAudiences().contains(query.requestedAudience()))
            return new RetrievalResult(List.of(),"DETERMINISTIC_KEYWORD",false,0);
        String requestedAudience=query.requestedAudience()==null?"":query.requestedAudience();
        List<String> terms=Arrays.stream(AiCitationValidator.normalizeQuery(query.query())
                        .toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term->term.length()>=2).distinct().limit(MAX_QUERY_TERMS).toList();
        if(terms.isEmpty()) return new RetrievalResult(List.of(),"DETERMINISTIC_KEYWORD",false,0);
        // Only placeholder names are assembled here. User text is always bound as a JDBC parameter.
        String databaseMatch=java.util.stream.IntStream.range(0,terms.size())
                .mapToObj(index->"(p.search_vector @@ plainto_tsquery('pg_catalog.simple'::regconfig,:term"+index+")"
                        +" or p.keywords @> array[:term"+index+"]::text[])")
                .collect(java.util.stream.Collectors.joining(" or "));
        String databaseScore=java.util.stream.IntStream.range(0,terms.size())
                .mapToObj(index->"case when p.search_vector @@ plainto_tsquery('pg_catalog.simple'::regconfig,:term"
                        +index+") or p.keywords @> array[:term"+index+"]::text[] then 1 else 0 end")
                .collect(java.util.stream.Collectors.joining(" + "));
        String sql="""
                select p.*,d.document_id,d.source_url,d.effective_from,d.effective_to,v.version_label,
                       (__DATABASE_SCORE__) as matched_keyword_count
                from knowledge_passage p join knowledge_document_version v on v.document_version_id=p.document_version_id
                join knowledge_document d on d.document_id=v.document_id and d.current_version=v.version_label
                join knowledge_document_governance g
                  on g.document_id=d.document_id and g.version_label=v.version_label
                join knowledge_ai_passage_binding b on b.passage_id=p.passage_id
                  and b.document_id=g.document_id and b.version_label=g.version_label
                  and b.chunk_order=p.passage_order and b.source_hash=g.source_hash
                join knowledge_ingestion_import i on i.import_id=b.import_id
                  and i.document_id=b.document_id and i.version_label=b.version_label and i.source_hash=b.source_hash
                  and i.ai_proof_version='AI_DB_SNAPSHOT_V1' and i.ai_verified_at is not null
                where d.approval_status='APPROVED' and d.lifecycle_status='ACTIVE'
                  and d.title=g.title and d.issuer=g.issuer
                  and d.source_url is not distinct from g.source_url
                  and d.audience=g.audience and d.allowed_roles=g.allowed_roles
                  and d.effective_from=g.effective_from and d.effective_to is not distinct from g.effective_to
                  and d.checked_at=g.checked_at and d.effective_from<=:asOf
                  and (d.effective_to is null or d.effective_to>=:asOf)
                  and (:audience='' or d.audience in ('BOTH',:audience))
                  and (d.audience='BOTH' or d.audience=any(string_to_array(:audiences,',')))
                  and d.allowed_roles && string_to_array(:roles,',')::varchar[]
                  and g.approval_status='APPROVED' and g.lifecycle_status='ACTIVE'
                  and g.effective_from<=:asOf and (g.effective_to is null or g.effective_to>=:asOf)
                  and (:audience='' or g.audience in ('BOTH',:audience))
                  and (g.audience='BOTH' or g.audience=any(string_to_array(:audiences,',')))
                  and g.allowed_roles && string_to_array(:roles,',')::varchar[]
                  and (__DATABASE_MATCH__)
                order by (__DATABASE_SCORE__) desc,d.document_id,p.passage_order
                limit :candidateLimit
                """.replace("__DATABASE_MATCH__",databaseMatch).replace("__DATABASE_SCORE__",databaseScore);
        var statement=jdbc.sql(sql).param("asOf",query.asOf()).param("audience",requestedAudience)
                .param("audiences",String.join(",",query.requesterAudiences()))
                .param("roles",String.join(",",query.principalRoles()))
                .param("candidateLimit",Math.min(MAX_DATABASE_CANDIDATES,Math.max(50,query.limit()*20)));
        for(int index=0;index<terms.size();index++) statement=statement.param("term"+index,terms.get(index));
        List<SearchHit> hits=statement.query((rs,n)->new SearchHit(passage(rs,n),
                        rs.getInt("matched_keyword_count"),"DETERMINISTIC_KEYWORD")).list().stream()
                .limit(query.limit()).toList();
        return new RetrievalResult(hits,"DETERMINISTIC_KEYWORD",false,0);
    }
    private Passage passage(java.sql.ResultSet rs,int n)throws java.sql.SQLException {
        Array array=rs.getArray("keywords");
        List<String> keywords=array==null?List.of():List.of((String[])array.getArray());
        return new Passage(rs.getObject("passage_id",UUID.class),rs.getString("document_id"),rs.getString("version_label"),
                rs.getString("heading"),rs.getString("content"),keywords,rs.getString("citation_label"),
                rs.getString("source_url"),rs.getObject("effective_from",java.time.LocalDate.class),
                rs.getObject("effective_to",java.time.LocalDate.class));
    }
}
