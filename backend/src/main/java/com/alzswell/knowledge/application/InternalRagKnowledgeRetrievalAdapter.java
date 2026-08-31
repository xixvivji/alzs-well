package com.alzswell.knowledge.application;

import com.alzswell.knowledge.api.KnowledgeResponses.SearchHit;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class InternalRagKnowledgeRetrievalAdapter implements KnowledgeRetrievalPort {
    private static final String CONTRACT_VERSION="1.0.0";
    static final String RESULTS_MODE="INTERNAL_RAG_HYBRID";
    static final String POLICY_ABSTAIN_MODE="INTERNAL_RAG_POLICY_ABSTAIN";
    static final String NO_MATCH_MODE="INTERNAL_RAG_NO_MATCH";
    private final InternalKnowledgeSearchClient client;
    private final AiCitationValidator citationValidator;

    public InternalRagKnowledgeRetrievalAdapter(InternalKnowledgeSearchClient client,AiCitationValidator citationValidator) {
        this.client=client;this.citationValidator=citationValidator;
    }

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if(query.requestedAudience()!=null&&!query.requesterAudiences().contains(query.requestedAudience()))
            return new RetrievalResult(List.of(),POLICY_ABSTAIN_MODE,false,0);
        String normalized=AiCitationValidator.normalizeQuery(query.query());
        UUID requestId=UUID.randomUUID();
        AiSearchResponse response=client.search(new AiSearchRequest(CONTRACT_VERSION,requestId,normalized,
                List.of("KNOWLEDGE_SEARCH"),query.principalRoles(),query.requesterAudiences(),query.asOf(),query.limit()));
        // 정책 거절을 선언한 upstream 응답은 나머지 필드가 손상됐더라도 더 허용적인
        // 결정론적 답변으로 우회하지 않는다. 계약 위반은 가용성을 낮추되 답변 범위를 넓히지 않는다.
        if(response!=null&&"POLICY_ABSTAIN".equals(response.outcome()))
            return new RetrievalResult(List.of(),POLICY_ABSTAIN_MODE,false,0);
        if(response==null||!CONTRACT_VERSION.equals(response.contractVersion())||!requestId.equals(response.requestId())
                ||!AiCitationValidator.hash(normalized).equals(response.queryHash())||response.results()==null
                ||response.results().size()>query.limit()||response.outcome()==null)
            throw new AiRetrievalException("AI retrieval response contract is invalid");
        if("NO_MATCH".equals(response.outcome())) {
            requireTerminalEmpty(response,"NO_RELEVANT_MATCH");
            return new RetrievalResult(List.of(),NO_MATCH_MODE,false,0);
        }
        if("INDEX_UNAVAILABLE".equals(response.outcome())) {
            if(!response.retryable()||!response.results().isEmpty()||!isUnavailableReason(response.reasonCode()))
                throw new AiRetrievalException("AI retrieval unavailable response contract is invalid");
            throw new AiRetrievalException("AI retrieval index is unavailable: "+response.reasonCode());
        }
        if(!"RESULTS".equals(response.outcome())||response.retryable()||response.reasonCode()!=null
                ||response.results().isEmpty())
            throw new AiRetrievalException("AI retrieval result outcome contract is invalid");
        List<SearchHit> hits=new ArrayList<>();
        Set<UUID> passageIds=new HashSet<>();
        int rejected=0;
        for(AiSearchHit result:response.results()) {
            Optional<SearchHit> validated=citationValidator.validate(result,query);
            if(validated.isPresent()&&passageIds.add(validated.get().passage().passageId())) hits.add(validated.get());
            else rejected++;
        }
        return new RetrievalResult(hits,RESULTS_MODE,false,rejected);
    }

    private void requireTerminalEmpty(AiSearchResponse response,String reasonCode) {
        if(response.retryable()||!response.results().isEmpty()||!reasonCode.equals(response.reasonCode()))
            throw new AiRetrievalException("AI retrieval terminal outcome contract is invalid");
    }

    private boolean isUnavailableReason(String reasonCode) {
        return "STORAGE_UNAVAILABLE".equals(reasonCode)
                ||"SEARCH_TIMEOUT".equals(reasonCode)
                ||"EMBEDDING_MODEL_UNAVAILABLE".equals(reasonCode)
                ||"EMBEDDING_VECTOR_INVALID".equals(reasonCode);
    }
}
