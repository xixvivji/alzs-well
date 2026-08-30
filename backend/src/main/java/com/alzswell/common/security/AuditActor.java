package com.alzswell.common.security;

import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedSession;
import java.util.UUID;
import java.util.Set;
import org.springframework.security.core.Authentication;

public record AuditActor(UUID principalId, String customerId, UUID sessionId, String actorType) {
    private static final Set<String> STAFF_ONLY_AUTHORITIES = Set.of(
            "FINANCIAL_INTENT_SHARED_READ", "AUDIT_EXPORT_REQUEST", "AUDIT_READ_ALL",
            "COMPLIANCE_TRACE_READ", "STAFF_ACCESS_GRANT_READ",
            "STAFF_ACCESS_GRANT_WRITE", "STAFF_ACCESS_EVALUATE", "FEATURE_FLAG_READ",
            "FEATURE_FLAG_WRITE", "DETECTION_POLICY_READ", "DETECTION_POLICY_WRITE",
            "DETECTION_PROMOTION_READ", "KNOWLEDGE_READ", "KNOWLEDGE_SEARCH", "KNOWLEDGE_ADMIN_WRITE");

    public static AuditActor from(Authentication authentication) {
        UUID principalId = null;
        String customerId = authentication.getName();
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            principalId = principal.principalId();
            customerId = principal.customerId();
        }
        UUID sessionId = authentication.getDetails() instanceof AuthenticatedSession session
                ? session.sessionId() : null;
        boolean staff = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> authority.startsWith("STAFF_")
                        || authority.endsWith("_ALL")
                        || authority.equals("DETECTION_PROMOTE")
                        || authority.equals("DETECTION_RUN_CREATE")
                        || authority.equals("SYNTHETIC_DATASET_ADMIN")
                        || STAFF_ONLY_AUTHORITIES.contains(authority));
        return new AuditActor(principalId, customerId, sessionId, staff ? "STAFF" : "CUSTOMER");
    }

    public String legacyActorId() {
        return principalId == null ? customerId : principalId.toString();
    }
}
