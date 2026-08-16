package com.alzswell.demo.application;

import com.alzswell.common.web.TraceIdContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoAuditWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String policyVersion;
    private final String algorithmVersion;
    private final String schemaVersion;

    public DemoAuditWriter(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${app.versions.policy:context-policy-v1.0.0}") String policyVersion,
            @Value("${app.versions.algorithm:baseline-rules-v2.0.0}") String algorithmVersion,
            @Value("${app.versions.schema:7}") String schemaVersion
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.policyVersion = policyVersion;
        this.algorithmVersion = algorithmVersion;
        this.schemaVersion = schemaVersion;
    }

    public void write(
            UUID sessionId,
            String eventType,
            Map<String, Object> payload,
            OffsetDateTime occurredAt
    ) {
        write(sessionId, null, eventType, payload, occurredAt);
    }

    public void write(
            UUID sessionId,
            UUID demoRunId,
            String eventType,
            Map<String, Object> payload,
            OffsetDateTime occurredAt
    ) {
        String traceId = TraceIdContext.currentOrCreate();
        String payloadJson = toJson(payload);
        String actorType = stringValue(payload, "actorType", "SYSTEM");
        String actorId = nullableString(payload.get("actorId"));
        String targetType = targetType(payload);
        String targetId = targetId(sessionId, payload);
        String beforeState = firstString(payload, "beforeState", "fromState", "previousState");
        String afterState = firstString(payload, "afterState", "toState", "currentState");
        String evidenceHash = sha256(payloadJson);
        String requestHash = nullableString(payload.get("requestHash"));
        String idempotencyKeyHash = nullableString(payload.get("idempotencyKeyHash"));

        jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtextextended(cast(? as text), 0))",
                resultSet -> null,
                sessionId.toString()
        );
        String previousEventHash = jdbcTemplate.query(
                """
                select event_hash from decision_audit
                 where demo_session_id = ?
                 order by audit_sequence desc
                 limit 1
                """,
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                sessionId
        );
        UUID auditId = UUID.randomUUID();
        String eventHash = sha256(String.join("\n",
                previousEventHash == null ? "GENESIS" : previousEventHash,
                auditId.toString(),
                sessionId.toString(),
                demoRunId == null ? "" : demoRunId.toString(),
                traceId,
                eventType,
                actorType,
                actorId == null ? "" : actorId,
                targetType,
                targetId,
                beforeState == null ? "" : beforeState,
                afterState == null ? "" : afterState,
                policyVersion,
                algorithmVersion,
                schemaVersion,
                payloadJson,
                occurredAt.toInstant().toString()
        ));
        jdbcTemplate.update(
                """
                insert into decision_audit (
                    audit_id,
                    demo_session_id,
                    demo_run_id,
                    trace_id,
                    event_type,
                    actor_type,
                    actor_id,
                    target_type,
                    target_id,
                    before_state,
                    after_state,
                    policy_version,
                    algorithm_version,
                    schema_version,
                    evidence_hash,
                    request_hash,
                    idempotency_key_hash,
                    previous_event_hash,
                    event_hash,
                    event_payload,
                    occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """,
                auditId,
                sessionId,
                demoRunId,
                traceId,
                eventType,
                actorType,
                actorId,
                targetType,
                targetId,
                beforeState,
                afterState,
                policyVersion,
                algorithmVersion,
                schemaVersion,
                evidenceHash,
                requestHash,
                idempotencyKeyHash,
                previousEventHash,
                eventHash,
                payloadJson,
                occurredAt
        );
    }

    private String targetType(Map<String, Object> payload) {
        if (payload.containsKey("caseId")) {
            return "PROTECTION_CASE";
        }
        if (payload.containsKey("alertId")) {
            return "ALERT_INCIDENT";
        }
        return "DEMO_SESSION";
    }

    private String targetId(UUID sessionId, Map<String, Object> payload) {
        if (payload.containsKey("caseId")) {
            return String.valueOf(payload.get("caseId"));
        }
        if (payload.containsKey("alertId")) {
            return String.valueOf(payload.get("alertId"));
        }
        return sessionId.toString();
    }

    private String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            String value = nullableString(payload.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Map<String, Object> payload, String key, String fallback) {
        String value = nullableString(payload.get(key));
        return value == null ? fallback : value;
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("감사 이벤트를 직렬화할 수 없습니다.", exception);
        }
    }
}
