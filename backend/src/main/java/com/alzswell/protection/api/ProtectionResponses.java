package com.alzswell.protection.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProtectionResponses {
    public record ActionSummary(String actionCode, String title, String actionStatus, String executionType,
            String eligibilitySummary, String issuer, LocalDate effectiveFrom, LocalDate checkedAt,
            boolean externalExecutionAvailable) {}
    public record ActionList(List<ActionSummary> items, int total) {}
    public record ActionDetail(ActionSummary action, String sourceUrl, List<String> supportedReasonCodes,
            List<UUID> citationPassageIds, boolean applicationEndpointProvided) {}
    public record EligibilityEvaluation(String evaluationId, String customerId, String actionCode,
            String reasonCode, String policyVersion, String decision, List<String> reasons,
            boolean eligibleForGuidance, boolean externalExecutionAllowed, boolean applicationCreated) {}
    public record Enrollment(UUID enrollmentId, String customerId, String actionCode, String actionTitle,
            String institutionId, String institutionName, String enrollmentStatus, LocalDate observedAsOf,
            String providerMode, boolean readOnly) {}
    public record EnrollmentList(String customerId, List<Enrollment> items, int total,
            boolean externalProviderCalled) {}
    private ProtectionResponses() {}
}
