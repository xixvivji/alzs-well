package com.alzswell.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedSession;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AuditActorTest {
    @Test
    void usesBearerPrincipalAndSessionForStaffAuditIdentity() {
        UUID principalId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(principalId, "SYN_CUSTOMER_FIN_MGMT_001"), null,
                List.of(new SimpleGrantedAuthority("STAFF_CASE_ASSIGN")));
        authentication.setDetails(new AuthenticatedSession(sessionId));

        AuditActor actor = AuditActor.from(authentication);

        assertThat(actor.principalId()).isEqualTo(principalId);
        assertThat(actor.customerId()).isEqualTo("SYN_CUSTOMER_FIN_MGMT_001");
        assertThat(actor.sessionId()).isEqualTo(sessionId);
        assertThat(actor.actorType()).isEqualTo("STAFF");
        assertThat(actor.legacyActorId()).isEqualTo(principalId.toString());
    }

    @Test
    void keepsMockCustomerNameAsLegacyIdentity() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "SYN_CUSTOMER_FIN_MGMT_001", null,
                List.of(new SimpleGrantedAuthority("ALERT_RESPOND")));

        AuditActor actor = AuditActor.from(authentication);

        assertThat(actor.principalId()).isNull();
        assertThat(actor.customerId()).isEqualTo("SYN_CUSTOMER_FIN_MGMT_001");
        assertThat(actor.actorType()).isEqualTo("CUSTOMER");
        assertThat(actor.legacyActorId()).isEqualTo("SYN_CUSTOMER_FIN_MGMT_001");
    }
}
