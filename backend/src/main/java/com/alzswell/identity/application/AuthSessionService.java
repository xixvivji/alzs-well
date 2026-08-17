package com.alzswell.identity.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.identity.api.AuthErrorCode;
import com.alzswell.identity.api.AuthResponses.CurrentUser;
import com.alzswell.identity.api.AuthResponses.PermissionList;
import com.alzswell.identity.api.AuthResponses.TokenPair;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbcTemplate;
    private final IdentityProviderPort identityProvider;
    private final Clock clock;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public AuthSessionService(JdbcTemplate jdbcTemplate, IdentityProviderPort identityProvider, Clock clock,
            @Value("${app.auth.access-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${app.auth.refresh-ttl-seconds:28800}") long refreshTtlSeconds) {
        if (accessTtlSeconds < 60 || refreshTtlSeconds <= accessTtlSeconds) {
            throw new IllegalArgumentException("인증 token TTL 설정이 올바르지 않습니다.");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.identityProvider = identityProvider;
        this.clock = clock;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @Transactional
    public TokenPair login(String loginId, String password) {
        IdentityProviderPort.AuthenticatedPrincipal principal =
                identityProvider.authenticate(loginId.trim(), password);
        return createSession(principal.principalId());
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<UUID> sessions = jdbcTemplate.query(
                "select session_id from auth_session where refresh_token_hash = ? and revoked_at is null and refresh_expires_at > ? for update",
                (rs, rowNum) -> rs.getObject("session_id", UUID.class), hash(refreshToken), now);
        if (sessions.size() != 1) throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        UUID sessionId = sessions.getFirst();
        String access = token();
        String refresh = token();
        OffsetDateTime accessExpiry = now.plusSeconds(accessTtlSeconds);
        OffsetDateTime refreshExpiry = now.plusSeconds(refreshTtlSeconds);
        jdbcTemplate.update("""
                update auth_session set access_token_hash = ?, refresh_token_hash = ?,
                    access_expires_at = ?, refresh_expires_at = ?, last_rotated_at = ?
                where session_id = ?
                """, hash(access), hash(refresh), accessExpiry, refreshExpiry, now, sessionId);
        return new TokenPair("Bearer", access, accessExpiry, refresh, refreshExpiry);
    }

    @Transactional
    public void logout(Authentication authentication) {
        UUID sessionId = requireSessionId(authentication);
        int changed = jdbcTemplate.update("""
                update auth_session set revoked_at = ?, revoke_reason = 'LOGOUT'
                where session_id = ? and revoked_at is null
                """, OffsetDateTime.now(clock), sessionId);
        if (changed != 1) throw new BusinessException(AuthErrorCode.SESSION_REVOKED);
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser(Authentication authentication) {
        UUID principalId = requirePrincipalId(authentication);
        return jdbcTemplate.query("""
                select p.principal_id, p.login_id, p.customer_id, p.display_name,
                       coalesce(array_agg(pr.role_code order by pr.role_code)
                           filter (where pr.role_code is not null), '{}') roles
                from auth_principal p
                left join auth_principal_role pr on pr.principal_id = p.principal_id
                where p.principal_id = ? and p.status = 'ACTIVE'
                group by p.principal_id
                """, rs -> {
                    if (!rs.next()) throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
                    return new CurrentUser(rs.getObject("principal_id", UUID.class), rs.getString("login_id"),
                            rs.getString("customer_id"), rs.getString("display_name"),
                            List.of((String[]) rs.getArray("roles").getArray()));
                }, principalId);
    }

    @Transactional(readOnly = true)
    public PermissionList permissions(Authentication authentication) {
        UUID principalId = requirePrincipalId(authentication);
        List<String> values = jdbcTemplate.query("""
                select distinct rp.permission_code from auth_principal_role pr
                join auth_role_permission rp on rp.role_code = pr.role_code
                where pr.principal_id = ? order by rp.permission_code
                """, (rs, rowNum) -> rs.getString(1), principalId);
        return new PermissionList(values);
    }

    private TokenPair createSession(UUID principalId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String access = token();
        String refresh = token();
        OffsetDateTime accessExpiry = now.plusSeconds(accessTtlSeconds);
        OffsetDateTime refreshExpiry = now.plusSeconds(refreshTtlSeconds);
        jdbcTemplate.update("""
                insert into auth_session (session_id, principal_id, access_token_hash, refresh_token_hash,
                    access_expires_at, refresh_expires_at, created_at, last_rotated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), principalId, hash(access), hash(refresh), accessExpiry, refreshExpiry, now, now);
        return new TokenPair("Bearer", access, accessExpiry, refresh, refreshExpiry);
    }

    private UUID requireSessionId(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof AuthenticatedSession details)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return details.sessionId();
    }

    private UUID requirePrincipalId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return principal.principalId();
    }

    public record AuthenticatedPrincipal(UUID principalId, String customerId) {
        @Override public String toString() { return customerId; }
    }
    public record AuthenticatedSession(UUID sessionId) {}

    public static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
