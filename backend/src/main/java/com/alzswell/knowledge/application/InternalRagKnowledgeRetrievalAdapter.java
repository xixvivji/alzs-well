package com.alzswell.knowledge.application;

import com.alzswell.knowledge.api.KnowledgeResponses.SearchHit;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class InternalRagKnowledgeRetrievalAdapter implements KnowledgeRetrievalPort {
    private static final String CONTRACT_VERSION="1.0.0";
    private final InternalKnowledgeSearchClient client;
    private final AiCitationValidator citationValidator;

    public InternalRagKnowledgeRetrievalAdapter(InternalKnowledgeSearchClient client,AiCitationValidator citationValidator) {
        this.client=client;this.citationValidator=citationValidator;
    }

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if(query.requestedAudience()!=null&&!query.requesterAudiences().contains(query.requestedAudience()))
            return new RetrievalResult(List.of(),"INTERNAL_RAG_HYBRID",false,0);
        String normalized=AiCitationValidator.normalizeQuery(query.query());
        UUID requestId=UUID.randomUUID();
        AiSearchResponse response=client.search(new AiSearchRequest(CONTRACT_VERSION,requestId,normalized,
                List.of("KNOWLEDGE_SEARCH"),query.principalRoles(),query.requesterAudiences(),query.asOf(),query.limit()));
        if(response==null||!CONTRACT_VERSION.equals(response.contractVersion())||!requestId.equals(response.requestId())
                ||!AiCitationValidator.hash(normalized).equals(response.queryHash())||response.results()==null
                ||response.results().size()>query.limit())
            throw new AiRetrievalException("AI retrieval response contract is invalid");
        List<SearchHit> hits=new ArrayList<>();
        Set<UUID> passageIds=new HashSet<>();
        int rejected=0;
        for(AiSearchHit result:response.results()) {
            Optional<SearchHit> validated=citationValidator.validate(result,query);
            if(validated.isPresent()&&passageIds.add(validated.get().passage().passageId())) hits.add(validated.get());
            else rejected++;
        }
        return new RetrievalResult(hits,"INTERNAL_RAG_HYBRID",false,rejected);
    }
}
