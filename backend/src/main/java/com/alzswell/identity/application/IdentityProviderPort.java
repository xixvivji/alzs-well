package com.alzswell.identity.application;

import java.util.UUID;

public interface IdentityProviderPort {
    AuthenticatedPrincipal authenticate(String loginId, String password);
    record AuthenticatedPrincipal(UUID principalId, String customerId) {}
}
