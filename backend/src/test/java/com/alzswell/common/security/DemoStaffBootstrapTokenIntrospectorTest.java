package com.alzswell.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

class DemoStaffBootstrapTokenIntrospectorTest {
    private static final String TOKEN =
            "test-bootstrap-token-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void authenticatesOnlyTheConfiguredOpaqueBearerToken() {
        var introspector = new DemoStaffBootstrapTokenIntrospector(TOKEN);

        var principal = introspector.introspect(TOKEN);

        assertThat(principal.getName()).isEqualTo(DemoStaffBootstrapTokenIntrospector.PRINCIPAL_NAME);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly(DemoStaffBootstrapTokenIntrospector.AUTHORITY);
        assertThat(principal.<String>getAttribute("token_type")).isEqualTo("staff-bootstrap");
    }

    @Test
    void rejectsWrongOrMalformedTokens() {
        var introspector = new DemoStaffBootstrapTokenIntrospector(TOKEN);

        assertThatThrownBy(() -> introspector.introspect(TOKEN.replace('a', 'b')))
                .isInstanceOf(BadOpaqueTokenException.class);
        assertThatThrownBy(() -> introspector.introspect("short"))
                .isInstanceOf(BadOpaqueTokenException.class);
        assertThatThrownBy(() -> introspector.introspect(null))
                .isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void rejectsUnsafeBootstrapConfiguration() {
        assertThatThrownBy(() -> new DemoStaffBootstrapTokenIntrospector("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64~512자");
    }
}
