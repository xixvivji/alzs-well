package com.alzswell.knowledge.application;

import com.alzswell.knowledge.api.KnowledgeResponses.*;
import java.sql.Array;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DeterministicKnowledgeRetrievalAdapter implements KnowledgeRetrievalPort {
    private final JdbcClient jdbc;
    public DeterministicKnowledgeRetrievalAdapter(JdbcClient jdbc){this.jdbc=jdbc;}

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if(query.requestedAudience()!=null&&!query.requesterAudiences().contains(query.requestedAudience()))
            return new RetrievalResult(List.of(),"DETERMINISTIC_KEYWORD",false,0);
        String requestedAudience=query.requestedAudience()==null?"":query.requestedAudience();
        List<String> terms=Arrays.stream(query.query().toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(term->term.length()>=2).distinct().toList();
        List<SearchHit> hits=jdbc.sql("""
                select p.*,d.document_id,d.source_url,d.effective_from,d.effective_to,v.version_label
                from knowledge_passage p join knowledge_document_version v on v.document_version_id=p.document_version_id
                join knowledge_document d on d.document_id=v.document_id
                where d.approval_status='APPROVED' and d.lifecycle_status='ACTIVE'
                  and v.version_label=d.current_version and d.effective_from<=:asOf
                  and (d.effective_to is null or d.effective_to>=:asOf)
                  and (:audience='' or d.audience in ('BOTH',:audience))
                  and (d.audience='BOTH' or d.audience=any(string_to_array(:audiences,',')))
                  and d.allowed_roles && string_to_array(:roles,',')::varchar[]
                order by d.document_id,p.passage_order
                """).param("asOf",query.asOf()).param("audience",requestedAudience)
                .param("audiences",String.join(",",query.requesterAudiences()))
                .param("roles",String.join(",",query.principalRoles())).query(this::passage).list().stream()
                .map(item->new SearchHit(item,score(item,terms),"DETERMINISTIC_KEYWORD"))
                .filter(hit->hit.matchedKeywordCount()>0)
                .sorted(Comparator.comparingInt(SearchHit::matchedKeywordCount).reversed()
                        .thenComparing(hit->hit.passage().passageId()))
                .limit(query.limit()).toList();
        return new RetrievalResult(hits,"DETERMINISTIC_KEYWORD",false,0);
    }

    private int score(Passage passage,List<String> terms) {
        String searchable=(passage.heading()+" "+passage.content()+" "+String.join(" ",passage.keywords())).toLowerCase(Locale.ROOT);
        return (int)terms.stream().filter(searchable::contains).count();
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
