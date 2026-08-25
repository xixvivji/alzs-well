package com.alzswell.knowledge.application;

import com.alzswell.knowledge.api.KnowledgeResponses.*;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.RetrievalQuery;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AiCitationValidator {
    private static final Pattern HASH=Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern CHUNK_ID=Pattern.compile("chk_[0-9a-f]{64}");
    private final JdbcClient jdbc;

    public AiCitationValidator(JdbcClient jdbc){this.jdbc=jdbc;}

    public Optional<SearchHit> validate(AiSearchHit hit,RetrievalQuery query) {
        if(!structurallyValid(hit,query)) return Optional.empty();
        AiCitation citation=hit.citation();
        if(!hash(hit.content()).equals(citation.textHash()))
            return Optional.empty();
        String requestedAudience=query.requestedAudience()==null?"":query.requestedAudience();
        return jdbc.sql("""
                select p.*,d.document_id,d.title,d.issuer,d.source_url,d.effective_from,d.effective_to,
                       v.version_label,g.source_hash,b.section_path,b.page
                from knowledge_passage p
                join knowledge_document_version v on v.document_version_id=p.document_version_id
                join knowledge_document d on d.document_id=v.document_id
                join knowledge_document_governance g on g.document_id=d.document_id and g.version_label=v.version_label
                join knowledge_ai_passage_binding b on b.passage_id=p.passage_id
                where d.document_id=:documentId and v.version_label=:versionLabel and p.passage_order=:chunkOrder
                  and d.approval_status='APPROVED' and d.lifecycle_status='ACTIVE' and v.version_label=d.current_version
                  and d.effective_from<=:asOf and (d.effective_to is null or d.effective_to>=:asOf)
                  and (:audience='' or d.audience in ('BOTH',:audience))
                  and (d.audience='BOTH' or d.audience=any(string_to_array(:audiences,',')))
                  and d.allowed_roles && string_to_array(:roles,',')::varchar[]
                  and g.approval_status='APPROVED' and g.lifecycle_status='ACTIVE'
                  and g.effective_from<=:asOf and (g.effective_to is null or g.effective_to>=:asOf)
                  and (g.audience='BOTH' or g.audience=any(string_to_array(:audiences,',')))
                  and g.allowed_roles && string_to_array(:roles,',')::varchar[] and g.source_hash=:sourceHash
                  and b.chunk_id=:chunkId and b.source_hash=:sourceHash and b.text_hash=:textHash
                """).param("documentId",citation.documentId()).param("versionLabel",citation.versionLabel())
                .param("chunkOrder",citation.chunkOrder()).param("asOf",query.asOf())
                .param("audience",requestedAudience).param("audiences",String.join(",",query.requesterAudiences()))
                .param("roles",String.join(",",query.principalRoles())).param("sourceHash",citation.sourceHash())
                .param("chunkId",citation.chunkId()).param("textHash",citation.textHash())
                .query((rs,n)->{
                    String content=rs.getString("content");
                    Array pathArray=rs.getArray("section_path");
                    List<String> sectionPath=pathArray==null?List.of():List.of((String[])pathArray.getArray());
                    if(!hash(content).equals(citation.textHash())||!content.equals(hit.content())
                            ||!rs.getString("title").equals(citation.title())
                            ||!rs.getString("issuer").equals(citation.issuer())
                            ||!rs.getString("heading").equals(citation.heading())||!sectionPath.equals(citation.sectionPath())
                            ||!Objects.equals(rs.getObject("page",Integer.class),citation.page())
                            ||!Objects.equals(rs.getString("source_url"),citation.sourceUrl())) return null;
                    Array array=rs.getArray("keywords");
                    List<String> keywords=array==null?List.of():List.of((String[])array.getArray());
                    Passage passage=new Passage(rs.getObject("passage_id",UUID.class),rs.getString("document_id"),
                            rs.getString("version_label"),rs.getString("heading"),content,keywords,
                            rs.getString("citation_label"),rs.getString("source_url"),
                            rs.getObject("effective_from",java.time.LocalDate.class),
                            rs.getObject("effective_to",java.time.LocalDate.class));
                    return new SearchHit(passage,score(passage,query.query()),"INTERNAL_RAG_HYBRID");
                }).optional().filter(Objects::nonNull);
    }

    private boolean structurallyValid(AiSearchHit hit,RetrievalQuery query) {
        if(hit==null||hit.content()==null||hit.content().isBlank()||hit.content().length()>1200
                ||hit.score()<0||!Double.isFinite(hit.score())||hit.citation()==null) return false;
        AiCitation c=hit.citation();
        return "1.0.0".equals(c.contractVersion())&&"HYBRID".equals(c.retrievalMethod())
                &&"hybrid-hash-ngram-v1".equals(c.indexVersion())&&query.asOf().equals(c.retrievedAsOf())
                &&c.documentId()!=null&&c.versionLabel()!=null&&c.title()!=null&&c.issuer()!=null
                &&c.heading()!=null&&c.sectionPath()!=null&&c.chunkOrder()>0&&c.citationLabel()!=null
                &&c.sectionPath().stream().allMatch(Objects::nonNull)&&(c.page()==null||c.page()>0)
                &&HASH.matcher(nullToEmpty(c.sourceHash())).matches()
                &&HASH.matcher(nullToEmpty(c.textHash())).matches()
                &&CHUNK_ID.matcher(nullToEmpty(c.chunkId())).matches()
                &&c.citationLabel().equals(c.title()+" > "+c.heading());
    }

    static String normalizeQuery(String value){return nfc(String.join(" ",value.trim().split("\\s+")));}
    static String hash(String value){return "sha256:"+hex(value.getBytes(StandardCharsets.UTF_8));}
    private static String hex(byte[] value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }
    private static String nfc(String value){return Normalizer.normalize(value,Normalizer.Form.NFC);}
    private static String nullToEmpty(String value){return value==null?"":value;}
    private static int score(Passage passage,String query) {
        List<String> terms=Arrays.stream(normalizeQuery(query).toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term->term.length()>=2).distinct().toList();
        String text=(passage.heading()+" "+passage.content()+" "+String.join(" ",passage.keywords()))
                .toLowerCase(Locale.ROOT);
        return (int)terms.stream().filter(text::contains).count();
    }
}
