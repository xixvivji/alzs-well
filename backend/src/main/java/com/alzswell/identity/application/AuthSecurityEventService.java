package com.alzswell.identity.application;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSecurityEventService {
    private final JdbcTemplate jdbcTemplate;

    public AuthSecurityEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long reserve(String loginIdHash, OffsetDateTime since,
                        int failureLimit, OffsetDateTime occurredAt) {
        jdbcTemplate.queryForObject(
                "select 1 from (select pg_advisory_xact_lock(hashtextextended(?, 0))) locked",
                Integer.class, loginIdHash);
        Long failures = jdbcTemplate.queryForObject("""
                select count(*) from auth_login_event
                 where login_id_hash = ? and outcome in ('PENDING','FAILED') and occurred_at >= ?
                """, Long.class, loginIdHash, since);
        boolean limited = failures != null && failures >= failureLimit;
        if (limited) {
            recordRow(loginIdHash, "RATE_LIMITED", occurredAt);
            return null;
        }
        return jdbcTemplate.queryForObject("""
                insert into auth_login_event(login_id_hash,outcome,occurred_at)
                values(?,'PENDING',?) returning event_id
                """, Long.class, loginIdHash, occurredAt);
    }

    @Transactional
    public void complete(long eventId, String outcome) {
        int updated = jdbcTemplate.update("""
                update auth_login_event set outcome=? where event_id=? and outcome='PENDING'
                """, outcome, eventId);
        if (updated != 1) throw new IllegalStateException("로그인 시도 예약을 확정할 수 없습니다.");
    }

    private void recordRow(String loginIdHash, String outcome, OffsetDateTime occurredAt) {
        jdbcTemplate.update("""
                insert into auth_login_event (login_id_hash, outcome, occurred_at) values (?, ?, ?)
                """, loginIdHash, outcome, occurredAt);
    }
}
