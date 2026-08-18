package com.alzswell.identity.application;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSecurityEventService {
    private final JdbcTemplate jdbcTemplate;

    public AuthSecurityEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public boolean isRateLimited(String loginIdHash, OffsetDateTime since, int failureLimit) {
        Long failures = jdbcTemplate.queryForObject("""
                select count(*) from auth_login_event
                 where login_id_hash = ? and outcome in ('FAILED', 'RATE_LIMITED') and occurred_at >= ?
                """, Long.class, loginIdHash, since);
        return failures != null && failures >= failureLimit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String loginIdHash, String outcome, OffsetDateTime occurredAt) {
        jdbcTemplate.update("""
                insert into auth_login_event (login_id_hash, outcome, occurred_at) values (?, ?, ?)
                """, loginIdHash, outcome, occurredAt);
    }
}
