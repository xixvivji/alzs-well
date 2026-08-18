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
    private final AuthSecurityEventService securityEventService;
    private final Clock clock;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final long absoluteTtlSeconds;
    private final int maxActiveSessions;
    private final int loginFailureLimit;
    private final long loginFailureWindowSeconds;

    public AuthSessionService(JdbcTemplate jdbcTemplate, IdentityProviderPort identityProvider,
            AuthSecurityEventService securityEventService, Clock clock,
            @Value("${app.auth.access-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${app.auth.refresh-ttl-seconds:28800}") long refreshTtlSeconds,
            @Value("${app.auth.absolute-ttl-seconds:86400}") long absoluteTtlSeconds,
            @Value("${app.auth.max-active-sessions-per-principal:5}") int maxActiveSessions,
            @Value("${app.auth.login-failure-limit:10}") int loginFailureLimit,
            @Value("${app.auth.login-failure-window-seconds:900}") long loginFailureWindowSeconds) {
        if (accessTtlSeconds < 60 || refreshTtlSeconds <= accessTtlSeconds
                || absoluteTtlSeconds < refreshTtlSeconds || maxActiveSessions < 1
                || loginFailureLimit < 1 || loginFailureWindowSeconds < 60) {
            throw new IllegalArgumentException("인증 token TTL 설정이 올바르지 않습니다.");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.identityProvider = identityProvider;
        this.securityEventService = securityEventService;
        this.clock = clock;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.absoluteTtlSeconds = absoluteTtlSeconds;
        this.maxActiveSessions = maxActiveSessions;
        this.loginFailureLimit = loginFailureLimit;
        this.loginFailureWindowSeconds = loginFailureWindowSeconds;
    }

    @Transactional
    public TokenPair login(String loginId, String password) {
        String normalizedLoginId = loginId.trim();
        String loginIdHash = hash(normalizedLoginId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        // 동일 ID의 병렬 실패가 count-then-insert 사이를 통과하지 않도록 transaction 단위로 직렬화한다.
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, loginIdHash);
                statement.execute();
            }
            return null;
        });
        if (securityEventService.isRateLimited(loginIdHash,
                now.minusSeconds(loginFailureWindowSeconds), loginFailureLimit)) {
            securityEventService.record(loginIdHash, "RATE_LIMITED", now);
            throw new BusinessException(AuthErrorCode.LOGIN_RATE_LIMITED);
        }
        try {
            IdentityProviderPort.AuthenticatedPrincipal principal =
                    identityProvider.authenticate(normalizedLoginId, password);
            TokenPair pair = createSession(principal.principalId(), now);
            securityEventService.record(loginIdHash, "SUCCEEDED", now);
            return pair;
        } catch (BusinessException exception) {
            securityEventService.record(loginIdHash, "FAILED", now);
            throw exception;
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPair refresh(String refreshToken) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<RefreshTokenRow> tokens = jdbcTemplate.query("""
                select t.session_id, s.token_family_id, s.absolute_expires_at, s.revoked_at,
                       t.expires_at, t.used_at, t.revoked_at token_revoked_at
                  from auth_refresh_token t
                  join auth_session s on s.session_id = t.session_id
                 where t.token_hash = ?
                 for update of t, s
                """, (rs, rowNum) -> new RefreshTokenRow(
                        rs.getObject("session_id", UUID.class), rs.getObject("token_family_id", UUID.class),
                        rs.getObject("absolute_expires_at", OffsetDateTime.class),
                        rs.getObject("revoked_at", OffsetDateTime.class),
                        rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("used_at", OffsetDateTime.class),
                        rs.getObject("token_revoked_at", OffsetDateTime.class)), hash(refreshToken));
        if (tokens.size() != 1) throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        RefreshTokenRow tokenRow = tokens.getFirst();
        if (tokenRow.usedAt() != null || tokenRow.tokenRevokedAt() != null) {
            revokeFamily(tokenRow.tokenFamilyId(), now, "REFRESH_TOKEN_REUSE");
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        if (tokenRow.sessionRevokedAt() != null || !tokenRow.expiresAt().isAfter(now)
                || !tokenRow.absoluteExpiresAt().isAfter(now)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        String access = token();
        String refresh = token();
        OffsetDateTime accessExpiry = now.plusSeconds(accessTtlSeconds);
        OffsetDateTime refreshExpiry = min(now.plusSeconds(refreshTtlSeconds), tokenRow.absoluteExpiresAt());
        if (accessExpiry.isAfter(refreshExpiry)) accessExpiry = refreshExpiry;
        jdbcTemplate.update("update auth_refresh_token set used_at = ? where token_hash = ? and used_at is null",
                now, hash(refreshToken));
        jdbcTemplate.update("""
                insert into auth_refresh_token (token_hash, session_id, issued_at, expires_at)
                values (?, ?, ?, ?)
                """, hash(refresh), tokenRow.sessionId(), now, refreshExpiry);
        jdbcTemplate.update("""
                update auth_session set access_token_hash = ?, refresh_token_hash = ?,
                    access_expires_at = ?, refresh_expires_at = ?, last_rotated_at = ?
                where session_id = ?
                """, hash(access), hash(refresh), accessExpiry, refreshExpiry, now, tokenRow.sessionId());
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
        jdbcTemplate.update("update auth_refresh_token set revoked_at = ? where session_id = ? and revoked_at is null",
                OffsetDateTime.now(clock), sessionId);
    }

    @Transactional
    public void logoutAll(Authentication authentication) {
        UUID principalId = requirePrincipalId(authentication);
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                update auth_refresh_token set revoked_at = ? where revoked_at is null and session_id in (
                    select session_id from auth_session where principal_id = ?
                )
                """, now, principalId);
        jdbcTemplate.update("""
                update auth_session set revoked_at = ?, revoke_reason = 'LOGOUT_ALL'
                 where principal_id = ? and revoked_at is null
                """, now, principalId);
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

    private TokenPair createSession(UUID principalId, OffsetDateTime now) {
        jdbcTemplate.queryForObject("select principal_id from auth_principal where principal_id = ? for update",
                UUID.class, principalId);
        Long activeSessions = jdbcTemplate.queryForObject("""
                select count(*) from auth_session
                 where principal_id = ? and revoked_at is null
                   and refresh_expires_at > ? and absolute_expires_at > ?
                """, Long.class, principalId, now, now);
        long sessionsToRevoke = Math.max(0, (activeSessions == null ? 0 : activeSessions) - maxActiveSessions + 1);
        if (sessionsToRevoke > 0) {
            jdbcTemplate.update("""
                    update auth_session set revoked_at = ?, revoke_reason = 'SESSION_LIMIT'
                     where session_id in (
                        select session_id from auth_session
                         where principal_id = ? and revoked_at is null
                         order by created_at, session_id limit ? for update
                     )
                    """, now, principalId, sessionsToRevoke);
            jdbcTemplate.update("""
                    update auth_refresh_token set revoked_at = ?
                     where revoked_at is null and session_id in (
                        select session_id from auth_session
                         where principal_id = ? and revoke_reason = 'SESSION_LIMIT' and revoked_at = ?
                     )
                    """, now, principalId, now);
        }
        String access = token();
        String refresh = token();
        UUID sessionId = UUID.randomUUID();
        UUID tokenFamilyId = UUID.randomUUID();
        OffsetDateTime accessExpiry = now.plusSeconds(accessTtlSeconds);
        OffsetDateTime refreshExpiry = now.plusSeconds(refreshTtlSeconds);
        OffsetDateTime absoluteExpiry = now.plusSeconds(absoluteTtlSeconds);
        jdbcTemplate.update("""
                insert into auth_session (session_id, principal_id, access_token_hash, refresh_token_hash,
                    access_expires_at, refresh_expires_at, created_at, last_rotated_at,
                    token_family_id, absolute_expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, sessionId, principalId, hash(access), hash(refresh), accessExpiry, refreshExpiry, now, now,
                tokenFamilyId, absoluteExpiry);
        jdbcTemplate.update("""
                insert into auth_refresh_token (token_hash, session_id, issued_at, expires_at)
                values (?, ?, ?, ?)
                """, hash(refresh), sessionId, now, refreshExpiry);
        return new TokenPair("Bearer", access, accessExpiry, refresh, refreshExpiry);
    }

    private void revokeFamily(UUID tokenFamilyId, OffsetDateTime now, String reason) {
        jdbcTemplate.update("""
                update auth_refresh_token set revoked_at = ? where revoked_at is null and session_id in (
                    select session_id from auth_session where token_family_id = ?
                )
                """, now, tokenFamilyId);
        jdbcTemplate.update("""
                update auth_session set revoked_at = ?, compromised_at = ?, revoke_reason = ?
                 where token_family_id = ? and revoked_at is null
                """, now, now, reason, tokenFamilyId);
    }

    private OffsetDateTime min(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
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

    private record RefreshTokenRow(UUID sessionId, UUID tokenFamilyId, OffsetDateTime absoluteExpiresAt,
                                   OffsetDateTime sessionRevokedAt, OffsetDateTime expiresAt,
                                   OffsetDateTime usedAt, OffsetDateTime tokenRevokedAt) {}

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
