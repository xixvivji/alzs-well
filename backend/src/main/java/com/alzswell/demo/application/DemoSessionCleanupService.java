package com.alzswell.demo.application;

import com.alzswell.demo.domain.DemoSession;
import com.alzswell.demo.domain.DemoSessionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DemoSessionCleanupService {

    private final DemoSessionRepository sessionRepository;
    private final DemoAuditWriter auditWriter;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;

    public DemoSessionCleanupService(
            DemoSessionRepository sessionRepository,
            DemoAuditWriter auditWriter,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock,
            @Value("${app.demo.cleanup-batch-size:100}") int batchSize
    ) {
        this.sessionRepository = sessionRepository;
        this.auditWriter = auditWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
    }

    @Scheduled(fixedDelayString = "${app.demo.cleanup-interval-ms:300000}")
    public int cleanupExpiredSessions() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<UUID> candidates = jdbcTemplate.queryForList(
                """
                select session_id
                  from demo_session
                 where expires_at <= ?
                 order by expires_at, session_id
                 limit ?
                """,
                UUID.class,
                now,
                batchSize
        );

        int deleted = 0;
        for (UUID sessionId : candidates) {
            Boolean removed = transactionTemplate.execute(status -> cleanupOne(sessionId, now));
            if (Boolean.TRUE.equals(removed)) {
                deleted += 1;
            }
        }
        return deleted;
    }

    private boolean cleanupOne(UUID sessionId, OffsetDateTime now) {
        Boolean lockAcquired = jdbcTemplate.queryForObject(
                "select pg_try_advisory_xact_lock(hashtextextended(cast(? as text), 0))",
                Boolean.class,
                sessionId.toString()
        );
        if (!Boolean.TRUE.equals(lockAcquired)) {
            return false;
        }

        DemoSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.isExpiredAt(now)) {
            return false;
        }

        auditWriter.write(
                sessionId,
                session.getDemoRunId(),
                "DEMO_SESSION_EXPIRED_PURGED",
                Map.of(
                        "actorType", "SYSTEM",
                        "previousState", "EXPIRED",
                        "currentState", "PURGED",
                        "syntheticDataDeleted", true,
                        "externalActionCreated", false
                ),
                now
        );
        jdbcTemplate.queryForObject(
                "select set_config('app.demo_session_discard', ?, true)",
                String.class,
                sessionId.toString()
        );
        sessionRepository.delete(session);
        sessionRepository.flush();
        return true;
    }
}
