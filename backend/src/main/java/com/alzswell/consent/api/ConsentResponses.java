package com.alzswell.consent.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ConsentResponses {
    public record Consent(UUID consentId,String customerId,String purposeCode,String status,List<String> scopes,
            OffsetDateTime grantedAt,OffsetDateTime expiresAt,OffsetDateTime withdrawnAt,String withdrawalReason,
            long version,boolean revocable) {}
    public record ConsentList(String customerId,List<Consent> items,int total,OffsetDateTime evaluatedAt) {}
    public record ConsentEvent(UUID eventId,String eventType,String statusSnapshot,List<String> scopeSnapshot,
            String reason,String actorId,OffsetDateTime occurredAt,long version) {}
    public record ConsentHistory(UUID consentId,List<ConsentEvent> items,int total) {}
    public record DisclosureEvaluation(String evaluationId,UUID consentId,String customerId,String purposeCode,
            List<String> requestedScopes,List<String> missingScopes,String consentStatus,String decision,
            String policyVersion,boolean disclosureAllowed,boolean externalDisclosureRequested,
            boolean externalDisclosureCreated) {}
    private ConsentResponses() {}
}
