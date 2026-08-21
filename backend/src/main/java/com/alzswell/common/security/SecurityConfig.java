package com.alzswell.common.security;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    FilterRegistrationBean<BearerTokenAuthenticationFilter> bearerFilterRegistration(
            BearerTokenAuthenticationFilter filter
    ) {
        FilterRegistrationBean<BearerTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService demoStaffUserDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.staff-bootstrap-username}") String username,
            @Value("${app.demo.staff-bootstrap-password}") String password
    ) {
        if (username == null || !username.matches("[A-Za-z0-9._-]{4,64}")) {
            throw new IllegalStateException("직원 데모 계정 이름은 4~64자의 안전한 형식이어야 합니다.");
        }
        if (password == null || password.length() < 32) {
            throw new IllegalStateException("직원 데모 계정 비밀번호는 32자 이상이어야 합니다.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .authorities("DEMO_STAFF_BOOTSTRAP")
                .build());
    }

    @Bean
    @Order(1)
    SecurityFilterChain staffBootstrapSecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .securityMatcher("/api/v1/demo/staff/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().hasAuthority("DEMO_STAFF_BOOTSTRAP")
                );
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            DemoCapabilityFilter demoCapabilityFilter,
            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/token/refresh"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/system/**",
                                "/api/v1/demo/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(demoCapabilityFilter, AuthorizationFilter.class)
                .addFilterBefore(bearerTokenAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.customer-allowed-origins}") String customerAllowedOrigins,
            @Value("${app.cors.staff-allowed-origins}") String staffAllowedOrigins,
            @Value("${app.cors.require-https:false}") boolean requireHttps
    ) {
        List<String> customerOrigins = parseOrigins(customerAllowedOrigins, requireHttps);
        List<String> staffOrigins = parseOrigins(staffAllowedOrigins, requireHttps);
        Set<String> overlap = new LinkedHashSet<>(customerOrigins);
        overlap.retainAll(staffOrigins);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("고객과 직원 CORS origin은 서로 달라야 합니다: " + overlap);
        }

        List<String> commonAllowedHeaders = List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-Trace-Id",
                "Idempotency-Key",
                DemoCapabilityService.REQUEST_HEADER,
                DemoCapabilityService.RUN_HEADER
        );
        List<String> commonExposedHeaders = List.of(
                "X-Trace-Id",
                DemoCapabilityService.RUN_HEADER
        );

        List<String> allOrigins = new java.util.ArrayList<>(customerOrigins);
        allOrigins.addAll(staffOrigins);

        CorsConfiguration general = configuration(allOrigins, commonAllowedHeaders, commonExposedHeaders);
        CorsConfiguration customerApi = configuration(
                customerOrigins,
                commonAllowedHeaders,
                commonExposedHeaders
        );
        CorsConfiguration staffApi = configuration(
                staffOrigins,
                commonAllowedHeaders,
                commonExposedHeaders
        );
        CorsConfiguration customerIssuance = configuration(
                customerOrigins,
                commonAllowedHeaders,
                List.of("X-Trace-Id", DemoCapabilityService.CUSTOMER_RESPONSE_HEADER)
        );
        CorsConfiguration staffIssuance = configuration(
                staffOrigins,
                commonAllowedHeaders,
                List.of("X-Trace-Id", DemoCapabilityService.STAFF_RESPONSE_HEADER)
        );

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/demo/sessions", customerIssuance);
        source.registerCorsConfiguration("/api/v1/auth/**", customerApi);
        source.registerCorsConfiguration("/api/v1/staff/**", staffApi);
        source.registerCorsConfiguration("/api/v1/admin/**", staffApi);
        source.registerCorsConfiguration("/api/v1/audit/**", staffApi);
        source.registerCorsConfiguration("/api/v1/compliance/decision-traces/**", staffApi);
        source.registerCorsConfiguration("/api/v1/compliance/data-provenance/**", staffApi);
        source.registerCorsConfiguration("/api/v1/detection-runs/**", staffApi);
        source.registerCorsConfiguration("/api/v1/detection-promotions/**", staffApi);
        source.registerCorsConfiguration("/api/v1/synthetic-datasets/**", staffApi);
        source.registerCorsConfiguration("/api/v1/signals/**", staffApi);
        source.registerCorsConfiguration("/api/v1/staff-access-policy/**", staffApi);
        source.registerCorsConfiguration("/api/v1/customers/*/staff-access-grants/**", staffApi);
        source.registerCorsConfiguration("/api/v1/demo/staff/sessions/*/capability", staffIssuance);
        source.registerCorsConfiguration("/api/v1/demo/sessions/*/staff/**", staffApi);
        source.registerCorsConfiguration("/api/v1/demo/sessions/*/cases/**", staffApi);
        source.registerCorsConfiguration("/api/v1/demo/sessions/**", customerApi);
        source.registerCorsConfiguration("/api/v1/demo/scenarios", customerApi);
        source.registerCorsConfiguration("/**", general);
        return source;
    }

    private CorsConfiguration configuration(
            List<String> allowedOrigins,
            List<String> allowedHeaders,
            List<String> exposedHeaders
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.copyOf(allowedHeaders));
        configuration.setExposedHeaders(List.copyOf(exposedHeaders));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        return configuration;
    }

    List<String> parseOrigins(String allowedOrigins, boolean requireHttps) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            throw new IllegalArgumentException("CORS allowlist는 하나 이상의 명시적 origin이 필요합니다.");
        }
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .map(origin -> validateOrigin(origin, requireHttps))
                .distinct()
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("CORS allowlist는 하나 이상의 명시적 origin이 필요합니다.");
        }
        return origins;
    }

    private String validateOrigin(String origin, boolean requireHttps) {
        if (origin.contains("*")) {
            throw new IllegalArgumentException("CORS allowlist에는 wildcard를 사용할 수 없습니다.");
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("유효하지 않은 CORS origin입니다: " + origin, exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean validScheme = scheme.equals("http") || scheme.equals("https");
        boolean originOnly = uri.getHost() != null
                && uri.getUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        if (!validScheme || !originOnly) {
            throw new IllegalArgumentException("CORS 항목은 scheme과 host만 포함한 origin이어야 합니다: " + origin);
        }
        if (requireHttps && !scheme.equals("https")) {
            throw new IllegalArgumentException("운영 CORS origin은 HTTPS여야 합니다: " + origin);
        }
        return origin;
    }
}
