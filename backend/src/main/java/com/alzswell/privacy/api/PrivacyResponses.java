package com.alzswell.privacy.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PrivacyResponses {
    private PrivacyResponses() {}
    public record RetentionPolicy(String policyCode,String resourceType,int retentionDays,String legalBasis,
                                  String disposalMethod,LocalDate effectiveFrom,long version) {}
    public record RetentionPolicyList(List<RetentionPolicy> items,boolean externalProviderCalled) {}
    public record PrivacyRequest(UUID requestId,String customerId,String requestType,String targetType,
            String targetReference,String reasonCode,String status,String legalExceptionCode,
            OffsetDateTime requestedAt,boolean deletionExecuted,boolean externalActionExecuted) {}
}
