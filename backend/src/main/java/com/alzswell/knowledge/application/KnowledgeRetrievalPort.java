package com.alzswell.knowledge.application;

import com.alzswell.knowledge.api.KnowledgeResponses.SearchHit;
import java.time.LocalDate;
import java.util.List;

public interface KnowledgeRetrievalPort {
    RetrievalResult retrieve(RetrievalQuery query);
    record RetrievalQuery(String query,LocalDate asOf,String requestedAudience,List<String> principalRoles,
            List<String> requesterAudiences,int limit) {}
    record RetrievalResult(List<SearchHit> hits,String retrievalMode,boolean fallbackUsed,int rejectedCitations) {
        public RetrievalResult {
            hits=List.copyOf(hits);
        }
    }
}
