package com.alzswell.common.security;

import com.alzswell.identity.application.AuthSessionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

public final class DemoStaffBootstrapTokenIntrospector implements OpaqueTokenIntrospector {
    static final String PRINCIPAL_NAME = "demo-staff-bootstrap";
    static final String AUTHORITY = "DEMO_STAFF_BOOTSTRAP";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9._~-]{64,512}");

    private final byte[] expectedHash;

    public DemoStaffBootstrapTokenIntrospector(String configuredToken) {
        if (configuredToken == null || !TOKEN_PATTERN.matcher(configuredToken).matches()) {
            throw new IllegalStateException(
                    "직원 bootstrap Bearer 토큰은 64~512자의 영문·숫자·._~- 형식이어야 합니다."
            );
        }
        expectedHash = hashBytes(configuredToken);
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()
                || !MessageDigest.isEqual(expectedHash, hashBytes(token))) {
            throw new BadOpaqueTokenException("직원 bootstrap Bearer 토큰이 유효하지 않습니다.");
        }
        return new DefaultOAuth2AuthenticatedPrincipal(
                PRINCIPAL_NAME,
                Map.of("sub", PRINCIPAL_NAME, "token_type", "staff-bootstrap"),
                List.of(new SimpleGrantedAuthority(AUTHORITY))
        );
    }

    private static byte[] hashBytes(String token) {
        return AuthSessionService.hash(token).getBytes(StandardCharsets.US_ASCII);
    }
}
