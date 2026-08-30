package com.alzswell.detection.api;

import com.alzswell.detection.api.DetectionPolicyRequests.RuleInput;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DetectionPolicyResponses {
    private DetectionPolicyResponses() {}

    public record PolicySummary(
            UUID ruleId, String versionCode, String status, String description, String rulesHash,
            long version, OffsetDateTime createdAt, OffsetDateTime publishedAt
    ) {}

    public record PolicyDetail(PolicySummary policy, List<RuleInput> rules, UUID basedOnPolicyId) {}
    public record PolicyList(List<PolicySummary> items, int totalCount) {}
    public record VersionList(List<PolicySummary> items, int totalCount, String activeVersion) {}
    public record AlgorithmVersion(String version, String status, boolean advisoryAiUsed,
                                   boolean externalProviderCalled) {}
    public record AlgorithmVersionList(List<AlgorithmVersion> items, int totalCount) {}
}
