package com.alzswell.knowledge.api;

import java.time.*;
import java.util.*;

public final class KnowledgeResponses {
    public record DocumentSummary(String documentId,String title,String sourceType,String issuer,String audience,
            String status,LocalDate effectiveFrom,LocalDate effectiveTo,LocalDate checkedAt,String currentVersion){}
    public record DocumentList(List<DocumentSummary> items,int total){}
    public record DocumentDetail(DocumentSummary document,String sourceUrl,String contentChecksum,boolean approvedForCitation){}
    public record DocumentVersion(UUID documentVersionId,String versionLabel,String contentChecksum,
            OffsetDateTime publishedAt,OffsetDateTime approvedAt,OffsetDateTime supersededAt){}
    public record VersionList(String documentId,List<DocumentVersion> items,int total){}
    public record Passage(UUID passageId,String documentId,String versionLabel,String heading,String content,
            List<String> keywords,String citationLabel,String sourceUrl,LocalDate effectiveFrom,LocalDate effectiveTo){}
    public record SearchHit(Passage passage,int matchedKeywordCount,String retrievalMode){}
    public record SearchResult(String query,LocalDate asOf,String audience,List<SearchHit> items,int total,
            boolean externalModelCalled,boolean vectorSearchUsed){}
    public record GuidanceCandidate(String actionCode,String title,String eligibilitySummary,String issuer,
            String sourceUrl,String executionType,String policyDecision,UUID citationPassageId,
            boolean externalExecutionCreated){}
    public record GuidanceCandidates(String reasonCode,String policyVersion,List<GuidanceCandidate> items,int total){}
    private KnowledgeResponses(){}
}
