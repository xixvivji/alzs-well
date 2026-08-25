package com.alzswell.knowledge.api;

import java.time.*;
import java.util.*;
import com.alzswell.knowledge.api.KnowledgeGovernanceRequests.SourceTransformation;

public final class KnowledgeGovernanceResponses {
    private KnowledgeGovernanceResponses() {}
    public record GovernedDocument(UUID workflowId,String documentId,String versionLabel,String title,String issuer,
            String sourceType,String sourcePath,String sourceUrl,String sourceHash,
            List<SourceTransformation> sourceTransformations,String documentType,String classification,
            String audience,List<String> allowedRoles,LocalDate effectiveFrom,
            LocalDate effectiveTo,LocalDate checkedAt,String usageRights,String approvalStatus,
            String lifecycleStatus,String approvedBy,OffsetDateTime approvedAt,long version,
            boolean ingestionReady,boolean searchable,boolean externalCallExecuted,OffsetDateTime updatedAt) {}
}
