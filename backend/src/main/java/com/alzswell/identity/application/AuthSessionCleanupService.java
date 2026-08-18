package com.alzswell.identity.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionCleanupService {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final long retentionSeconds;
    private final long loginEventRetentionSeconds;
    private final int batchSize;

    public AuthSessionCleanupService(JdbcTemplate jdbcTemplate, Clock clock,
            @Value("${app.auth.cleanup-retention-seconds:86400}") long retentionSeconds,
            @Value("${app.auth.login-event-retention-seconds:2592000}") long loginEventRetentionSeconds,
            @Value("${app.auth.cleanup-batch-size:200}") int batchSize) {
        if (retentionSeconds < 0 || loginEventRetentionSeconds < 86400 || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("인증 세션 정리 설정이 올바르지 않습니다.");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.retentionSeconds = retentionSeconds;
        this.loginEventRetentionSeconds = loginEventRetentionSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.auth.cleanup-interval-ms:3600000}")
    @Transactional
    public int cleanup() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusSeconds(retentionSeconds);
        int deletedSessions = jdbcTemplate.update("""
                delete from auth_session where session_id in (
                    select session_id from auth_session
                     where absolute_expires_at <= ?
                        or refresh_expires_at <= ?
                        or (revoked_at is not null and revoked_at <= ?)
                     order by coalesce(revoked_at, refresh_expires_at), session_id
                     limit ? for update skip locked
                )
                """, cutoff, cutoff, cutoff, batchSize);
        int deletedLoginEvents = jdbcTemplate.update("""
                delete from auth_login_event where event_id in (
                    select event_id from auth_login_event
                     where occurred_at <= ? order by occurred_at, event_id
                     limit ? for update skip locked
                )
                """, OffsetDateTime.now(clock).minusSeconds(loginEventRetentionSeconds), batchSize);
        return deletedSessions + deletedLoginEvents;
    }
}
