package com.alzswell.common.security;

import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedSession;
import java.util.UUID;
import org.springframework.security.core.Authentication;

public record AuditActor(UUID principalId, String customerId, UUID sessionId, String actorType) {
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
                .anyMatch(authority -> authority.getAuthority().endsWith("_ALL"));
        return new AuditActor(principalId, customerId, sessionId, staff ? "STAFF" : "CUSTOMER");
    }

    public String legacyActorId() {
        return principalId == null ? customerId : principalId.toString();
    }
}
