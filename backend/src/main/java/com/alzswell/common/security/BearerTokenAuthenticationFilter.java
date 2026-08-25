package com.alzswell.common.security;

import com.alzswell.identity.application.AuthSessionService;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public BearerTokenAuthenticationFilter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            chain.doFilter(request, response);
            return;
        }
        String hash = AuthSessionService.hash(header.substring(7));
        List<SessionRow> rows = jdbcTemplate.query("""
                select s.session_id, p.principal_id, p.customer_id,
                       array(select distinct rp.permission_code
                               from auth_principal_role pr
                               join auth_role_permission rp on rp.role_code = pr.role_code
                              where pr.principal_id = p.principal_id) permissions,
                       array(select distinct pr.role_code from auth_principal_role pr
                              where pr.principal_id = p.principal_id) roles
                  from auth_session s join auth_principal p on p.principal_id = s.principal_id
                 where s.access_token_hash = ? and s.revoked_at is null
                   and s.access_expires_at > ? and p.status = 'ACTIVE'
                """, (rs, rowNum) -> new SessionRow(rs.getObject("session_id", UUID.class),
                        rs.getObject("principal_id", UUID.class), rs.getString("customer_id"),
                        (String[]) rs.getArray("permissions").getArray(),
                        (String[]) rs.getArray("roles").getArray()), hash, OffsetDateTime.now(clock));
        if (rows.size() == 1) {
            SessionRow row = rows.getFirst();
            var authorities = java.util.stream.Stream.concat(
                    java.util.Arrays.stream(row.permissions()),
                    java.util.Arrays.stream(row.roles()).map(role -> "ROLE_" + role))
                    .distinct().map(SimpleGrantedAuthority::new).toList();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    new AuthenticatedPrincipal(row.principalId(), row.customerId()), null, authorities);
            authentication.setDetails(new AuthenticatedSession(row.sessionId()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private record SessionRow(UUID sessionId, UUID principalId, String customerId, String[] permissions,String[] roles) {}
}
