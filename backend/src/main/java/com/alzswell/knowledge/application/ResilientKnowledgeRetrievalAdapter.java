package com.alzswell.knowledge.application;

import com.alzswell.knowledge.api.KnowledgeResponses.SearchHit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class ResilientKnowledgeRetrievalAdapter implements KnowledgeRetrievalPort {
    private static final Logger log=LoggerFactory.getLogger(ResilientKnowledgeRetrievalAdapter.class);
    private final boolean enabled;
    private final InternalRagKnowledgeRetrievalAdapter internal;
    private final DeterministicKnowledgeRetrievalAdapter deterministic;

    public ResilientKnowledgeRetrievalAdapter(@Value("${app.ai-retrieval.enabled:false}") boolean enabled,
            InternalRagKnowledgeRetrievalAdapter internal,DeterministicKnowledgeRetrievalAdapter deterministic) {
        this.enabled=enabled;this.internal=internal;this.deterministic=deterministic;
    }

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if(!enabled) return deterministic.retrieve(query);
        try {
            RetrievalResult result=internal.retrieve(query);
            if(result.hits().isEmpty()&&result.rejectedCitations()>0) {
                log.warn("All internal knowledge citations were rejected; using deterministic fallback (rejected={})",
                        result.rejectedCitations());
                return fallback(query,result.rejectedCitations());
            }
            return result;
        }
        catch(AiRetrievalException exception) {
            String cause=exception.getCause()==null?"none":exception.getCause().getClass().getSimpleName();
            log.warn("Internal knowledge retrieval failed; using deterministic fallback (reason={}, cause={})",
                    exception.getMessage(),cause);
            return fallback(query,0);
        }
    }

    private RetrievalResult fallback(RetrievalQuery query,int rejectedCitations) {
        RetrievalResult fallback=deterministic.retrieve(query);
        List<SearchHit> hits=fallback.hits().stream().map(hit->new SearchHit(hit.passage(),
                hit.matchedKeywordCount(),"DETERMINISTIC_FALLBACK")).toList();
        return new RetrievalResult(hits,"DETERMINISTIC_FALLBACK",true,rejectedCitations);
    }
}
