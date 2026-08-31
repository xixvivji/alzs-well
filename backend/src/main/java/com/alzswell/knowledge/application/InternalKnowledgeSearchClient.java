package com.alzswell.knowledge.application;

import java.time.LocalDate;
import java.util.*;

public interface InternalKnowledgeSearchClient {
    AiSearchResponse search(AiSearchRequest request);
    AiHealthResponse health();

    record AiSearchRequest(String contractVersion,UUID requestId,String query,List<String> permissions,
            List<String> principalRoles,List<String> requesterAudiences,LocalDate asOf,int limit) {}
    record AiSearchResponse(
            String contractVersion,
            UUID requestId,
            String queryHash,
            String outcome,
            boolean retryable,
            String reasonCode,
            List<AiSearchHit> results
    ) {}
    record AiSearchHit(double score,String content,AiCitation citation) {}
    record AiCitation(String contractVersion,String documentId,String versionLabel,String chunkId,int chunkOrder,
            String title,String issuer,String heading,List<String> sectionPath,Integer page,String citationLabel,
            String sourceUrl,String sourceHash,String textHash,LocalDate retrievedAsOf,String retrievalMethod,
            String indexVersion) {}
    record AiHealthResponse(String status,String service,String embeddingConfiguredBackend,
            String embeddingBackend,String embeddingModelVersion,int embeddingDimensions,
            String modelStatus,String modelRevision,String artifactSha256,String goldenSetSha256,
            String indexVersion,boolean arcticRolloutEnabled,String deploymentEnvironment,
            boolean stagedApprovalEnabled,boolean embeddingFallbackUsed,Map<String,String> checks) {}
}
