package com.alzswell.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void acceptsOnlyExplicitHttpsOriginsForProduction() {
        assertThat(securityConfig.parseOrigins(
                "https://customer.example.com, https://staff.example.com,https://customer.example.com",
                true
        )).isEqualTo(List.of("https://customer.example.com", "https://staff.example.com"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.parseOrigins("*", true));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.parseOrigins("http://staff.example.com", true));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.parseOrigins("https://staff.example.com/path", true));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.parseOrigins("", true));
    }

    @Test
    void allowsExplicitLocalOriginsOnlyInDevelopmentMode() {
        assertThat(securityConfig.parseOrigins("http://localhost:5173", false))
                .containsExactly("http://localhost:5173");
    }

    @Test
    void rejectsSharedCustomerAndStaffOrigins() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.corsConfigurationSource(
                        "https://demo.example.com",
                        "https://demo.example.com",
                        true
                ))
                .withMessageContaining("서로 달라야");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.corsConfigurationSource(
                        "https://APP.example.com:443",
                        "https://app.example.com",
                        true
                ))
                .withMessageContaining("서로 달라야");
    }

    @Test
    void doesNotCreateServletSecurityBeansForNonWebBatchApplication() {
        new ApplicationContextRunner()
                .withUserConfiguration(SecurityConfig.class)
                .withPropertyValues(
                        "app.demo.staff-bootstrap-token="
                                + "test-bootstrap-token-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
                    assertThat(context).doesNotHaveBean(CorsConfigurationSource.class);
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                    assertThat(context).hasSingleBean(PasswordEncoder.class);
                });
    }
}
