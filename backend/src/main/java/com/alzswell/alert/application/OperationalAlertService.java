package com.alzswell.alert.application;

import com.alzswell.alert.api.AlertErrorCode;
import com.alzswell.alert.api.AlertRequests.ContextResponseCommand;
import com.alzswell.alert.api.AlertRequests.DeferCommand;
import com.alzswell.alert.api.AlertResponses.AlertDetail;
import com.alzswell.alert.api.AlertResponses.AlertList;
import com.alzswell.alert.api.AlertResponses.AlertSummary;
import com.alzswell.alert.api.AlertResponses.AlertTransition;
import com.alzswell.alert.api.AlertResponses.AuditEvent;
import com.alzswell.alert.api.AlertResponses.AuditTrail;
import com.alzswell.alert.api.AlertResponses.ContextOption;
import com.alzswell.alert.api.AlertResponses.ContextOptions;
import com.alzswell.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalAlertService {
    private static final List<ContextOption> OPTIONS = List.of(
            new ContextOption("EXPECTED_CHANGE", "제가 알고 있는 변화예요", "정상 생활변화로 확인하고 종료합니다."),
            new ContextOption("UNRECOGNIZED", "제가 모르는 변화예요", "자동 차단 없이 은행 검토 대상으로 연결합니다."),
            new ContextOption("NOT_SURE", "잘 모르겠어요", "추가 설명이 필요해 은행 검토 대상으로 연결합니다.")
    );
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OperationalAlertService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AlertList alerts(String customerId, String state, String severity) {
        requireCustomer(customerId);
        List<AlertSummary> items = jdbcTemplate.query("""
                select alert_id, signal_id, customer_id, state, severity, reason_code,
                       alert_version, deferred_until, created_at, updated_at
                  from operational_alert
                 where customer_id = ?
                   and (? is null or state = ?)
                   and (? is null or severity = ?)
                 order by created_at desc, alert_id desc
                """, this::mapSummary, customerId, state, state, severity, severity);
        return new AlertList(items, items.size());
    }

    @Transactional(readOnly = true)
    public AlertDetail alert(UUID alertId, String actorCustomerId, boolean readAll) {
        List<AlertDetail> rows = jdbcTemplate.query("""
                select a.alert_id, a.signal_id, a.customer_id, a.state, a.severity, a.reason_code,
                       a.alert_version, a.deferred_until, a.created_at, a.updated_at,
                       s.baseline_value, s.current_value, s.unit, s.algorithm_version
                  from operational_alert a
                  join customer_detection_signal s on s.signal_id = a.signal_id
                 where a.alert_id = ? and (? or a.customer_id = ?)
                """, (rs, rowNum) -> new AlertDetail(mapSummary(rs, rowNum),
                        rs.getBigDecimal("baseline_value").toPlainString(),
                        rs.getBigDecimal("current_value").toPlainString(), rs.getString("unit"),
                        rs.getString("algorithm_version"), false, false),
                alertId, readAll, actorCustomerId);
        if (rows.size() != 1) throw new BusinessException(AlertErrorCode.ALERT_NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public ContextOptions contextOptions(UUID alertId, String actorCustomerId, boolean readAll) {
        alert(alertId, actorCustomerId, readAll);
        return new ContextOptions(alertId, "이 변화가 본인이 알고 있는 생활 변화인가요?", OPTIONS);
    }

    @Transactional
    public AlertTransition respond(UUID alertId, ContextResponseCommand command, String idempotencyKey,
                                   String actorCustomerId, boolean respondAll) {
        AlertDetail detail = alert(alertId, actorCustomerId, respondAll);
        String keyHash = sha256(idempotencyKey);
        String requestHash = sha256(alertId + "|" + command.responseCode() + "|" + command.expectedVersion());
        AlertTransition replay = findContextReplay(alertId, keyHash, requestHash, detail.alert());
        if (replay != null) return replay;
        if (!List.of("AWAITING_CONTEXT", "DEFERRED").contains(detail.alert().state())
                || detail.alert().version() != command.expectedVersion()) {
            throw new BusinessException(AlertErrorCode.STATE_CONFLICT);
        }
        String nextState = command.responseCode().equals("EXPECTED_CHANGE") ? "CLOSED_NORMAL" : "BANK_REVIEW";
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = jdbcTemplate.update("""
                update operational_alert
                   set state = ?, deferred_until = null, alert_version = alert_version + 1, updated_at = ?
                 where alert_id = ? and alert_version = ? and state in ('AWAITING_CONTEXT', 'DEFERRED')
                """, nextState, now, alertId, command.expectedVersion());
        if (updated != 1) {
            AlertSummary current = alert(alertId, actorCustomerId, respondAll).alert();
            AlertTransition concurrentReplay = findContextReplay(alertId, keyHash, requestHash, current);
            if (concurrentReplay != null) return concurrentReplay;
            throw new BusinessException(AlertErrorCode.STATE_CONFLICT);
        }
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into operational_alert_context_event (
                    context_event_id, alert_id, response_code, previous_state, resulting_state,
                    request_hash, idempotency_key_hash, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, alertId, command.responseCode(), detail.alert().state(), nextState,
                requestHash, keyHash, now);
        UUID caseId = nextState.equals("BANK_REVIEW") ? createProtectionCase(detail.alert(), now) : null;
        writeAudit(alertId, "CONTEXT_RESPONDED", detail.alert().state(), nextState,
                caseId == null
                        ? Map.of("contextEventId", eventId, "responseCode", command.responseCode())
                        : Map.of("contextEventId", eventId, "responseCode", command.responseCode(),
                                "caseId", caseId), now);
        return transition(alertId, detail.alert().state(), nextState, command.expectedVersion() + 1,
                command.responseCode(), null, now, false);
    }

    @Transactional
    public AlertTransition defer(UUID alertId, DeferCommand command, String actorCustomerId, boolean respondAll) {
        AlertDetail detail = alert(alertId, actorCustomerId, respondAll);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (command.deferredUntil().isAfter(now.plusDays(7))
                || !List.of("AWAITING_CONTEXT", "DEFERRED").contains(detail.alert().state())
                || detail.alert().version() != command.expectedVersion()) {
            throw new BusinessException(AlertErrorCode.STATE_CONFLICT);
        }
        int updated = jdbcTemplate.update("""
                update operational_alert
                   set state = 'DEFERRED', deferred_until = ?, alert_version = alert_version + 1, updated_at = ?
                 where alert_id = ? and alert_version = ? and state in ('AWAITING_CONTEXT', 'DEFERRED')
                """, command.deferredUntil(), now, alertId, command.expectedVersion());
        if (updated != 1) throw new BusinessException(AlertErrorCode.STATE_CONFLICT);
        writeAudit(alertId, "ALERT_DEFERRED", detail.alert().state(), "DEFERRED",
                Map.of("deferredUntil", command.deferredUntil().toString()), now);
        return transition(alertId, detail.alert().state(), "DEFERRED", command.expectedVersion() + 1,
                null, command.deferredUntil(), now, false);
    }

    @Transactional(readOnly = true)
    public AuditTrail audit(UUID alertId, String actorCustomerId, boolean readAll) {
        alert(alertId, actorCustomerId, readAll);
        List<AuditEvent> items = jdbcTemplate.query("""
                select audit_event_id, event_type, previous_state, resulting_state,
                       detail::text, integrity_hash, created_at
                  from operational_alert_audit_event
                 where alert_id = ? order by created_at, audit_event_id
                """, (rs, rowNum) -> new AuditEvent(rs.getObject("audit_event_id", UUID.class),
                        rs.getString("event_type"), rs.getString("previous_state"),
                        rs.getString("resulting_state"), map(rs.getString("detail")),
                        rs.getString("integrity_hash"), rs.getObject("created_at", OffsetDateTime.class)), alertId);
        return new AuditTrail(alertId, items, items.size());
    }

    private AlertTransition findContextReplay(UUID alertId, String keyHash, String requestHash,
                                              AlertSummary current) {
        List<AlertTransition> rows = jdbcTemplate.query("""
                select response_code, previous_state, resulting_state, request_hash, created_at
                  from operational_alert_context_event
                 where alert_id = ? and idempotency_key_hash = ?
                """, (rs, rowNum) -> {
                    if (!secureHashEquals(rs.getString("request_hash"), requestHash)) {
                        throw new BusinessException(AlertErrorCode.IDEMPOTENCY_CONFLICT);
                    }
                    return transition(alertId, rs.getString("previous_state"), rs.getString("resulting_state"),
                            current.version(), rs.getString("response_code"), current.deferredUntil(),
                            rs.getObject("created_at", OffsetDateTime.class), true);
                }, alertId, keyHash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void writeAudit(UUID alertId, String eventType, String previousState, String resultingState,
                            Map<String, Object> detail, OffsetDateTime now) {
        UUID auditId = UUID.randomUUID();
        String detailJson = json(detail);
        String integrityHash = sha256(alertId + "|" + eventType + "|" + previousState + "|"
                + resultingState + "|" + detailJson + "|" + now);
        jdbcTemplate.update("""
                insert into operational_alert_audit_event (
                    audit_event_id, alert_id, event_type, previous_state, resulting_state,
                    detail, integrity_hash, created_at
                ) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, auditId, alertId, eventType, previousState, resultingState, detailJson, integrityHash, now);
    }

    private UUID createProtectionCase(AlertSummary alert, OffsetDateTime now) {
        UUID caseId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into operational_protection_case (
                    case_id, alert_id, signal_id, customer_id, review_priority, task_status,
                    case_version, created_at, updated_at
                ) values (?, ?, ?, ?, ?, 'PENDING', 1, ?, ?)
                on conflict (alert_id) do nothing
                """, caseId, alert.alertId(), alert.signalId(), alert.customerId(), alert.severity(), now, now);
        return jdbcTemplate.queryForObject(
                "select case_id from operational_protection_case where alert_id = ?", UUID.class, alert.alertId());
    }

    private AlertTransition transition(UUID alertId, String previousState, String currentState, long version,
                                       String responseCode, OffsetDateTime deferredUntil,
                                       OffsetDateTime changedAt, boolean replayed) {
        return new AlertTransition(alertId, previousState, currentState, version, responseCode,
                deferredUntil, changedAt, replayed, false, false);
    }

    private AlertSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new AlertSummary(rs.getObject("alert_id", UUID.class), rs.getObject("signal_id", UUID.class),
                rs.getString("customer_id"), rs.getString("state"), rs.getString("severity"),
                rs.getString("reason_code"), rs.getLong("alert_version"),
                rs.getObject("deferred_until", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private void requireCustomer(String customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_profile where customer_id = ?", Integer.class, customerId);
        if (count == null || count == 0) throw new BusinessException(AlertErrorCode.ALERT_NOT_FOUND);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("감사 상세를 직렬화할 수 없습니다.", exception);
        }
    }

    private Map<String, Object> map(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("감사 상세를 역직렬화할 수 없습니다.", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private boolean secureHashEquals(String left, String right) {
        return MessageDigest.isEqual(HexFormat.of().parseHex(left), HexFormat.of().parseHex(right));
    }
}
