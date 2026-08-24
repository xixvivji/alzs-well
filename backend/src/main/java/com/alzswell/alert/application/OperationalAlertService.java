package com.alzswell.alert.application;

import com.alzswell.alert.api.AlertErrorCode;
import com.alzswell.alert.api.AlertRequests.ContextResponseCommand;
import com.alzswell.alert.api.AlertRequests.DeferCommand;
import com.alzswell.alert.api.AlertRequests.AppealCommand;
import com.alzswell.alert.api.AlertResponses.AlertDetail;
import com.alzswell.alert.api.AlertResponses.AlertList;
import com.alzswell.alert.api.AlertResponses.AlertSummary;
import com.alzswell.alert.api.AlertResponses.AlertTransition;
import com.alzswell.alert.api.AlertResponses.AuditEvent;
import com.alzswell.alert.api.AlertResponses.AuditTrail;
import com.alzswell.alert.api.AlertResponses.ContextOption;
import com.alzswell.alert.api.AlertResponses.ContextOptions;
import com.alzswell.alert.api.AlertResponses.Appeal;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.common.security.AuditActor;
import com.alzswell.common.security.SensitiveTextPolicy;
import com.alzswell.staffaccess.application.StaffAccessPolicyService;
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
    private final StaffAccessPolicyService staffAccess;
    private final MutationIdempotencyService idempotency;
    private final SensitiveTextPolicy sensitiveTextPolicy;

    public OperationalAlertService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock,
            StaffAccessPolicyService staffAccess, MutationIdempotencyService idempotency,
            SensitiveTextPolicy sensitiveTextPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.staffAccess = staffAccess;
        this.idempotency = idempotency;
        this.sensitiveTextPolicy = sensitiveTextPolicy;
    }

    @Transactional
    public AlertList alerts(String customerId, String state, String severity, AuditActor actor) {
        requireCustomer(customerId);
        staffAccess.require(actor, customerId, "ALERT_MANAGEMENT", "ALERT_READ", "ALERT", null);
        List<AlertSummary> items = jdbcTemplate.query("""
                select alert_id, signal_id, customer_id, state, severity, reason_code,
                       alert_version, deferred_until, created_at, updated_at
                  from operational_alert
                 where customer_id = ?
                   and (cast(? as varchar) is null or state = ?)
                   and (cast(? as varchar) is null or severity = ?)
                 order by created_at desc, alert_id desc
                """, this::mapSummary, customerId, state, state, severity, severity);
        return new AlertList(items, items.size());
    }

    @Transactional
    public AlertDetail alert(UUID alertId, AuditActor actor, boolean readAll) {
        AlertDetail detail = loadAlert(alertId, actor.customerId(), readAll);
        staffAccess.require(actor, detail.alert().customerId(), "ALERT_MANAGEMENT", "ALERT_READ",
                "ALERT", alertId.toString());
        return detail;
    }

    private AlertDetail loadAlert(UUID alertId, String actorCustomerId, boolean readAll) {
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

    @Transactional
    public ContextOptions contextOptions(UUID alertId, AuditActor actor, boolean readAll) {
        alert(alertId, actor, readAll);
        return new ContextOptions(alertId, "이 변화가 본인이 알고 있는 생활 변화인가요?", OPTIONS);
    }

    @Transactional
    public AlertTransition respond(UUID alertId, ContextResponseCommand command, String idempotencyKey,
                                   boolean respondAll, AuditActor auditActor) {
        AlertDetail detail = loadAlert(alertId, auditActor.customerId(), respondAll);
        staffAccess.require(auditActor, detail.alert().customerId(), "ALERT_MANAGEMENT", "ALERT_RESPOND",
                "ALERT", alertId.toString());
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
            AlertSummary current = loadAlert(alertId, auditActor.customerId(), respondAll).alert();
            AlertTransition concurrentReplay = findContextReplay(alertId, keyHash, requestHash, current);
            if (concurrentReplay != null) return concurrentReplay;
            throw new BusinessException(AlertErrorCode.STATE_CONFLICT);
        }
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into operational_alert_context_event (
                    context_event_id, alert_id, response_code, previous_state, resulting_state,
                    request_hash, idempotency_key_hash, created_at, actor_principal_id,
                    actor_customer_id, actor_session_id, actor_type
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, alertId, command.responseCode(), detail.alert().state(), nextState,
                requestHash, keyHash, now, auditActor.principalId(), auditActor.customerId(),
                auditActor.sessionId(), auditActor.actorType());
        UUID caseId = nextState.equals("BANK_REVIEW") ? createProtectionCase(detail.alert(), now) : null;
        writeAudit(alertId, "CONTEXT_RESPONDED", detail.alert().state(), nextState,
                caseId == null
                        ? Map.of("contextEventId", eventId, "responseCode", command.responseCode())
                        : Map.of("contextEventId", eventId, "responseCode", command.responseCode(),
                                "caseId", caseId), auditActor, now);
        return transition(alertId, detail.alert().state(), nextState, command.expectedVersion() + 1,
                command.responseCode(), null, now, false);
    }

    @Transactional
    public AlertTransition defer(UUID alertId, DeferCommand command, String idempotencyKey,
                                 boolean respondAll, AuditActor auditActor) {
        return idempotency.execute("ALERT_DEFER:" + alertId, idempotencyKey, command,
                AlertTransition.class, AlertErrorCode.IDEMPOTENCY_CONFLICT,
                () -> deferOnce(alertId, command, respondAll, auditActor));
    }

    @Transactional
    public Appeal appeal(UUID alertId, AppealCommand command, String idempotencyKey, AuditActor actor) {
        AlertDetail visible = loadAlert(alertId, actor.customerId(), false);
        String statement = sensitiveTextPolicy.validate(command.statement(), "statement");
        String keyHash = sha256(idempotencyKey);
        String requestHash = sha256(alertId + "|" + command.reasonCode() + "|" + statement
                + "|" + command.expectedVersion());
        Appeal replay = findAppealReplay(alertId, keyHash, requestHash, true);
        if (replay != null) return replay;
        jdbcTemplate.queryForObject("select alert_id from operational_alert where alert_id=? for update",
                UUID.class, alertId);
        replay = findAppealReplay(alertId, keyHash, requestHash, true);
        if (replay != null) return replay;
        AlertSummary current = loadAlert(alertId, actor.customerId(), false).alert();
        Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from operational_alert_appeal where alert_id=?", Integer.class, alertId);
        if (existing != null && existing > 0) {
            throw new BusinessException(AlertErrorCode.APPEAL_ALREADY_SUBMITTED);
        }
        if (current.version() != command.expectedVersion()
                || !List.of("AWAITING_CONTEXT", "DEFERRED", "CLOSED_NORMAL").contains(current.state())) {
            throw new BusinessException(AlertErrorCode.STATE_CONFLICT);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = jdbcTemplate.update("""
                update operational_alert set state='BANK_REVIEW',deferred_until=null,
                       alert_version=alert_version+1,updated_at=?
                 where alert_id=? and customer_id=? and alert_version=?
                   and state in ('AWAITING_CONTEXT','DEFERRED','CLOSED_NORMAL')
                """, now, alertId, visible.alert().customerId(), command.expectedVersion());
        if (updated != 1) throw new BusinessException(AlertErrorCode.STATE_CONFLICT);
        UUID caseId = createProtectionCase(current, now);
        UUID appealId = UUID.randomUUID();
        String integrityHash = sha256(appealId + "|" + alertId + "|" + caseId + "|"
                + command.reasonCode() + "|" + statement + "|" + current.state() + "|" + now);
        jdbcTemplate.update("""
                insert into operational_alert_appeal(
                    appeal_id,alert_id,customer_id,reason_code,statement,previous_state,resulting_state,
                    case_id,status,request_hash,idempotency_key_hash,actor_customer_id,created_at,integrity_hash
                ) values(?,?,?,?,?,?,'BANK_REVIEW',?,'SUBMITTED',?,?,?,?,?)
                """, appealId, alertId, current.customerId(), command.reasonCode(), statement,
                current.state(), caseId, requestHash, keyHash, actor.customerId(), now, integrityHash);
        writeAudit(alertId, "APPEAL_SUBMITTED", current.state(), "BANK_REVIEW",
                Map.of("appealId", appealId, "caseId", caseId, "reasonCode", command.reasonCode()), actor, now);
        return new Appeal(appealId, alertId, caseId, command.reasonCode(), "SUBMITTED",
                current.state(), "BANK_REVIEW", command.expectedVersion() + 1, now,
                false, false, false);
    }

    private Appeal findAppealReplay(UUID alertId, String keyHash, String requestHash, boolean replayed) {
        List<Appeal> rows = jdbcTemplate.query("""
                select p.appeal_id,p.alert_id,p.case_id,p.reason_code,p.status,p.previous_state,
                       p.resulting_state,a.alert_version,p.request_hash,p.created_at
                  from operational_alert_appeal p join operational_alert a on a.alert_id=p.alert_id
                 where p.alert_id=? and p.idempotency_key_hash=?
                """, (rs,n) -> {
                    if (!secureHashEquals(rs.getString("request_hash"), requestHash)) {
                        throw new BusinessException(AlertErrorCode.APPEAL_IDEMPOTENCY_CONFLICT);
                    }
                    return new Appeal(rs.getObject("appeal_id",UUID.class),rs.getObject("alert_id",UUID.class),
                            rs.getObject("case_id",UUID.class),rs.getString("reason_code"),rs.getString("status"),
                            rs.getString("previous_state"),rs.getString("resulting_state"),
                            rs.getLong("alert_version"),rs.getObject("created_at",OffsetDateTime.class),
                            replayed,false,false);
                }, alertId, keyHash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private AlertTransition deferOnce(UUID alertId, DeferCommand command,
            boolean respondAll, AuditActor auditActor) {
        AlertDetail detail = loadAlert(alertId, auditActor.customerId(), respondAll);
        staffAccess.require(auditActor, detail.alert().customerId(), "ALERT_MANAGEMENT", "ALERT_RESPOND",
                "ALERT", alertId.toString());
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
                Map.of("deferredUntil", command.deferredUntil().toString()), auditActor, now);
        return transition(alertId, detail.alert().state(), "DEFERRED", command.expectedVersion() + 1,
                null, command.deferredUntil(), now, false);
    }

    @Transactional
    public AuditTrail audit(UUID alertId, AuditActor actor, boolean readAll) {
        alert(alertId, actor, readAll);
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
                            Map<String, Object> detail, AuditActor actor, OffsetDateTime now) {
        UUID auditId = UUID.randomUUID();
        String detailJson = json(detail);
        String integrityHash = sha256(alertId + "|" + eventType + "|" + previousState + "|"
                + resultingState + "|" + detailJson + "|" + actor.principalId() + "|"
                + actor.customerId() + "|" + actor.sessionId() + "|" + actor.actorType() + "|" + now);
        jdbcTemplate.update("""
                insert into operational_alert_audit_event (
                    audit_event_id, alert_id, event_type, previous_state, resulting_state,
                    detail, integrity_hash, created_at, actor_principal_id, actor_customer_id,
                    actor_session_id, actor_type
                ) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """, auditId, alertId, eventType, previousState, resultingState, detailJson, integrityHash, now,
                actor.principalId(), actor.customerId(), actor.sessionId(), actor.actorType());
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
