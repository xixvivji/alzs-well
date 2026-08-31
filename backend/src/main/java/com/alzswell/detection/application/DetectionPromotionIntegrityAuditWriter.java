package com.alzswell.detection.application;

import com.alzswell.common.audit.AuditTimestamp;
import com.alzswell.common.security.AuditActor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DetectionPromotionIntegrityAuditWriter {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public DetectionPromotionIntegrityAuditWriter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(
            UUID runId,
            String customerId,
            String reasonCode,
            String storedHash,
            String recomputedHash,
            AuditActor actor
    ) {
        OffsetDateTime now = AuditTimestamp.canonical(OffsetDateTime.now(clock));
        UUID eventId = UUID.randomUUID();
        String integrityHash = sha256(String.join("|",
                eventId.toString(), runId.toString(), nullSafe(customerId), reasonCode,
                nullSafe(storedHash), nullSafe(recomputedHash), nullSafe(actor.principalId()),
                nullSafe(actor.customerId()), nullSafe(actor.sessionId()), actor.actorType(), now.toString()
        ));
        jdbcTemplate.update("""
                insert into detection_promotion_integrity_event(
                    integrity_event_id,detection_run_id,customer_id,outcome,reason_code,
                    stored_hash,recomputed_hash,actor_principal_id,actor_customer_id,
                    actor_session_id,actor_type,occurred_at,integrity_hash
                ) values(?,?,?,'REJECTED',?,?,?,?,?,?,?,?,?)
                """, eventId, runId, customerId, reasonCode, storedHash, recomputedHash,
                actor.principalId(), actor.customerId(), actor.sessionId(), actor.actorType(), now, integrityHash);
    }

    private String sha256(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
