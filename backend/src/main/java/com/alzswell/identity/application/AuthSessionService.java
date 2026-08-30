package com.alzswell.identity.application;

import com.alzswell.common.audit.AuditTimestamp;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.identity.api.AuthErrorCode;
import com.alzswell.identity.api.AuthResponses.AuthSessionList;
import com.alzswell.identity.api.AuthResponses.AuthSessionRevocation;
import com.alzswell.identity.api.AuthResponses.AuthSessionSummary;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactionTemplate;

    public AuthSessionService(JdbcTemplate jdbcTemplate, IdentityProviderPort identityProvider,
            AuthSecurityEventService securityEventService, Clock clock,
            PlatformTransactionManager transactionManager,
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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TokenPair login(String loginId, String password) {
        String normalizedLoginId = loginId.trim();
        String loginIdHash = hash(normalizedLoginId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Long attemptId = securityEventService.reserve(loginIdHash,
                now.minusSeconds(loginFailureWindowSeconds), loginFailureLimit, now);
        if (attemptId == null) {
            throw new BusinessException(AuthErrorCode.LOGIN_RATE_LIMITED);
        }
        IdentityProviderPort.AuthenticatedPrincipal principal;
        try {
            principal = identityProvider.authenticate(normalizedLoginId, password);
        } catch (BusinessException exception) {
            securityEventService.complete(attemptId, "FAILED");
            throw exception;
        } catch (RuntimeException exception) {
            securityEventService.complete(attemptId, "ERROR");
            throw exception;
        }
        securityEventService.complete(attemptId, "SUCCEEDED");
        TokenPair pair = transactionTemplate.execute(status -> createSession(principal.principalId(), now));
        if (pair == null) throw new IllegalStateException("인증 세션 생성 transaction 결과가 없습니다.");
        return pair;
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

    @Transactional(readOnly = true)
    public AuthSessionList sessions(Authentication authentication) {
        UUID principalId = requirePrincipalId(authentication);
        UUID currentSessionId = requireSessionId(authentication);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<AuthSessionSummary> items = jdbcTemplate.query("""
                select session_id, created_at, last_rotated_at, access_expires_at, refresh_expires_at,
                       absolute_expires_at, revoked_at, revoke_reason
                  from auth_session where principal_id = ?
                 order by case when revoked_at is null and refresh_expires_at > ? and absolute_expires_at > ?
                               then 0 else 1 end,
                          created_at desc, session_id desc limit 50
                """, (rs, rowNum) -> {
                    UUID sessionId = rs.getObject("session_id", UUID.class);
                    OffsetDateTime revokedAt = rs.getObject("revoked_at", OffsetDateTime.class);
                    OffsetDateTime refreshExpiresAt = rs.getObject("refresh_expires_at", OffsetDateTime.class);
                    OffsetDateTime absoluteExpiresAt = rs.getObject("absolute_expires_at", OffsetDateTime.class);
                    String status = revokedAt != null ? "REVOKED"
                            : (!refreshExpiresAt.isAfter(now) || !absoluteExpiresAt.isAfter(now)
                                    ? "EXPIRED" : "ACTIVE");
                    return new AuthSessionSummary(sessionId, status, sessionId.equals(currentSessionId),
                            rs.getObject("created_at", OffsetDateTime.class),
                            rs.getObject("last_rotated_at", OffsetDateTime.class),
                            rs.getObject("access_expires_at", OffsetDateTime.class), refreshExpiresAt,
                            absoluteExpiresAt, revokedAt, rs.getString("revoke_reason"));
                }, principalId, now, now);
        int activeCount = (int) items.stream().filter(item -> "ACTIVE".equals(item.status())).count();
        return new AuthSessionList(items, items.size(), activeCount);
    }

    @Transactional
    public AuthSessionRevocation revokeSession(UUID targetSessionId, Authentication authentication) {
        UUID principalId = requirePrincipalId(authentication);
        UUID actorSessionId = requireSessionId(authentication);
        List<SessionOwnershipRow> rows = jdbcTemplate.query("""
                select revoked_at from auth_session
                 where session_id = ? and principal_id = ? for update
                """, (rs, rowNum) -> new SessionOwnershipRow(
                        rs.getObject("revoked_at", OffsetDateTime.class)), targetSessionId, principalId);
        if (rows.size() != 1) throw new BusinessException(AuthErrorCode.SESSION_NOT_FOUND);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime revokedAt = rows.getFirst().revokedAt();
        boolean alreadyEnded = revokedAt != null;
        if (!alreadyEnded) {
            jdbcTemplate.update("""
                    update auth_session set revoked_at = ?, revoke_reason = 'USER_SESSION_REVOKE'
                     where session_id = ? and principal_id = ? and revoked_at is null
                    """, now, targetSessionId, principalId);
            jdbcTemplate.update("""
                    update auth_refresh_token set revoked_at = ?
                     where session_id = ? and revoked_at is null
                    """, now, targetSessionId);
            revokedAt = now;
        }
        recordSessionEvent(principalId, actorSessionId, targetSessionId,
                alreadyEnded ? "ALREADY_ENDED" : "REVOKED", now);
        return new AuthSessionRevocation(targetSessionId, "REVOKED", targetSessionId.equals(actorSessionId),
                alreadyEnded, revokedAt);
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
    private record SessionOwnershipRow(OffsetDateTime revokedAt) {}

    private void recordSessionEvent(UUID principalId, UUID actorSessionId, UUID targetSessionId,
            String outcome, OffsetDateTime now) {
        now = AuditTimestamp.canonical(now);
        UUID eventId = UUID.randomUUID();
        String integrity = hash(eventId + "|" + principalId + "|" + actorSessionId + "|"
                + targetSessionId + "|" + outcome + "|" + now);
        jdbcTemplate.update("""
                insert into auth_session_event(event_id, principal_id, actor_session_id, target_session_id,
                    event_type, outcome, reason_code, occurred_at, integrity_hash)
                values (?, ?, ?, ?, 'SESSION_REVOKE_REQUESTED', ?, 'USER_SESSION_REVOKE', ?, ?)
                """, eventId, principalId, actorSessionId, targetSessionId, outcome, now, integrity);
    }

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
