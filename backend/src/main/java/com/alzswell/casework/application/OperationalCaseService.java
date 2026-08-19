package com.alzswell.casework.application;

import com.alzswell.casework.api.CaseworkErrorCode;
import com.alzswell.casework.api.CaseworkRequests.AssignmentCommand;
import com.alzswell.casework.api.CaseworkRequests.GuidancePlanCommand;
import com.alzswell.casework.api.CaseworkRequests.NoteCommand;
import com.alzswell.casework.api.CaseworkRequests.ReviewCommand;
import com.alzswell.casework.api.CaseworkResponses.CaseDetail;
import com.alzswell.casework.api.CaseworkResponses.CaseEvidence;
import com.alzswell.casework.api.CaseworkResponses.CaseNote;
import com.alzswell.casework.api.CaseworkResponses.CaseNotes;
import com.alzswell.casework.api.CaseworkResponses.CaseQueue;
import com.alzswell.casework.api.CaseworkResponses.CaseSummary;
import com.alzswell.casework.api.CaseworkResponses.CaseTimeline;
import com.alzswell.casework.api.CaseworkResponses.CaseTransition;
import com.alzswell.casework.api.CaseworkResponses.EvidenceItem;
import com.alzswell.casework.api.CaseworkResponses.GuidancePlan;
import com.alzswell.casework.api.CaseworkResponses.TimelineEvent;
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
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalCaseService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OperationalCaseService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CaseQueue queue(String status, String priority, UUID cursor, int limit) {
        List<CaseSummary> items;
        if (cursor == null) {
            items = jdbcTemplate.query("""
                    select * from operational_protection_case
                     where (cast(? as varchar) is null or task_status = ?)
                       and (cast(? as varchar) is null or review_priority = ?)
                     order by case review_priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end,
                              created_at, case_id
                     limit ?
                    """, this::mapSummary, status, status, priority, priority, limit);
        } else {
            CursorPoint point = cursor(cursor);
            items = jdbcTemplate.query("""
                    select * from operational_protection_case
                     where (cast(? as varchar) is null or task_status = ?)
                       and (cast(? as varchar) is null or review_priority = ?)
                       and (
                         case review_priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end > ?
                         or (case review_priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end = ?
                             and (created_at > ? or (created_at = ? and case_id > ?)))
                       )
                     order by case review_priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end,
                              created_at, case_id
                     limit ?
                    """, this::mapSummary, status, status, priority, priority, point.priorityRank(),
                    point.priorityRank(), point.createdAt(), point.createdAt(), point.caseId(), limit);
        }
        UUID nextCursor = items.size() == limit ? items.getLast().caseId() : null;
        return new CaseQueue(items, items.size(), nextCursor);
    }

    @Transactional(readOnly = true)
    public CaseDetail detail(UUID caseId) {
        List<CaseDetail> rows = jdbcTemplate.query("""
                select c.*, a.state as alert_state, a.reason_code, a.severity,
                       ce.response_code, gp.guidance_plan_id, gp.selected_action_codes::text
                  from operational_protection_case c
                  join operational_alert a on a.alert_id = c.alert_id
                  left join lateral (
                    select response_code from operational_alert_context_event
                     where alert_id = a.alert_id order by created_at desc limit 1
                  ) ce on true
                  left join operational_guidance_plan gp on gp.case_id = c.case_id
                 where c.case_id = ?
                """, (rs, rowNum) -> new CaseDetail(mapSummary(rs, rowNum), rs.getString("alert_state"),
                        rs.getString("reason_code"), rs.getString("severity"), rs.getString("response_code"),
                        rs.getObject("guidance_plan_id", UUID.class),
                        rs.getString("selected_action_codes") == null ? List.of()
                                : strings(rs.getString("selected_action_codes")),
                        false, false), caseId);
        if (rows.size() != 1) throw new BusinessException(CaseworkErrorCode.CASE_NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional
    public CaseTransition assign(UUID caseId, AssignmentCommand command, String actor) {
        CaseSummary current = detail(caseId).caseSummary();
        if (current.taskStatus().equals("COMPLETED") || current.version() != command.expectedVersion()) {
            throw new BusinessException(CaseworkErrorCode.CASE_STATE_CONFLICT);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = jdbcTemplate.update("""
                update operational_protection_case
                   set assigned_team = ?, assigned_to = ?, case_version = case_version + 1, updated_at = ?
                 where case_id = ? and case_version = ? and task_status <> 'COMPLETED'
                """, command.assignedTeam(), command.assignedTo(), now, caseId, command.expectedVersion());
        if (updated != 1) throw new BusinessException(CaseworkErrorCode.CASE_STATE_CONFLICT);
        jdbcTemplate.update("""
                insert into operational_case_activity (
                    activity_id, case_id, activity_type, actor_subject, detail, occurred_at
                ) values (?, ?, 'CASE_ASSIGNED', ?, ?::jsonb, ?)
                """, UUID.randomUUID(), caseId, actor,
                json(java.util.Map.of("assignedTeam", command.assignedTeam(),
                        "assignedTo", command.assignedTo())), now);
        return transition(caseId, current.taskStatus(), current.taskStatus(), command.expectedVersion() + 1,
                "ASSIGN", now, false);
    }

    @Transactional(readOnly = true)
    public CaseEvidence evidence(UUID caseId) {
        CaseDetail caseDetail = detail(caseId);
        CaseSummary summary = caseDetail.caseSummary();
        List<EvidenceItem> items = jdbcTemplate.query("""
                select evidence_id, evidence_type, source_reference, occurred_at, amount, currency,
                       description, integrity_hash
                  from customer_signal_evidence_snapshot
                 where signal_id = ? order by occurred_at, evidence_id
                """, (rs, rowNum) -> {
                    java.math.BigDecimal amount = rs.getBigDecimal("amount");
                    return new EvidenceItem(rs.getObject("evidence_id", UUID.class),
                            rs.getString("evidence_type"), rs.getString("source_reference"),
                            rs.getObject("occurred_at", OffsetDateTime.class),
                            amount == null ? null : amount.toPlainString(), rs.getString("currency"),
                            rs.getString("description"), rs.getString("integrity_hash"));
                }, summary.signalId());
        List<String[]> signal = jdbcTemplate.query("""
                select baseline_value::text, current_value::text, unit
                  from customer_detection_signal where signal_id = ?
                """, (rs, rowNum) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)},
                summary.signalId());
        if (signal.size() != 1) throw new BusinessException(CaseworkErrorCode.CASE_NOT_FOUND);
        return new CaseEvidence(caseId, summary.signalId(), caseDetail.reasonCode(), signal.getFirst()[0],
                signal.getFirst()[1], signal.getFirst()[2], items, items.size(), true);
    }

    @Transactional(readOnly = true)
    public CaseTimeline timeline(UUID caseId) {
        CaseDetail caseDetail = detail(caseId);
        UUID alertId = caseDetail.caseSummary().alertId();
        List<TimelineEvent> items = jdbcTemplate.query("""
                select event_type, actor_subject, previous_state, resulting_state, summary, occurred_at
                  from (
                    select 'CASE_CREATED'::varchar as event_type, null::varchar as actor_subject,
                           null::varchar as previous_state, 'PENDING'::varchar as resulting_state,
                           '운영형 행원 사건 생성'::varchar as summary, created_at as occurred_at
                      from operational_protection_case where case_id = ?
                    union all
                    select event_type, null::varchar, previous_state, resulting_state,
                           '고객 경보 상태 변경'::varchar, created_at
                      from operational_alert_audit_event where alert_id = ?
                    union all
                    select activity_type, actor_subject, null::varchar, null::varchar,
                           '담당 팀·행원 배정'::varchar, occurred_at
                      from operational_case_activity where case_id = ?
                    union all
                    select action_code, reviewer_subject, previous_status, resulting_status,
                           '행원 검토 상태 변경'::varchar, created_at
                      from operational_case_review_event where case_id = ?
                    union all
                    select 'GUIDANCE_APPROVED'::varchar, approved_by, 'IN_REVIEW'::varchar,
                           'GUIDANCE_APPROVED'::varchar, '고객 안내계획 승인'::varchar, approved_at
                      from operational_guidance_plan where case_id = ?
                    union all
                    select 'INTERNAL_NOTE_ADDED'::varchar, created_by, null::varchar, null::varchar,
                           '행원 내부 메모 등록'::varchar, created_at
                      from operational_case_note where case_id = ?
                  ) timeline order by occurred_at, event_type
                """, (rs, rowNum) -> new TimelineEvent(rs.getString("event_type"),
                        rs.getString("actor_subject"), rs.getString("previous_state"),
                        rs.getString("resulting_state"), rs.getString("summary"),
                        rs.getObject("occurred_at", OffsetDateTime.class)),
                caseId, alertId, caseId, caseId, caseId, caseId);
        return new CaseTimeline(caseId, items, items.size());
    }

    @Transactional(readOnly = true)
    public CaseNotes notes(UUID caseId) {
        detail(caseId);
        List<CaseNote> items = jdbcTemplate.query("""
                select note_id, case_id, note_text, created_by, integrity_hash, created_at
                  from operational_case_note where case_id = ? order by created_at, note_id
                """, (rs, rowNum) -> mapNote(rs, false), caseId);
        return new CaseNotes(caseId, items, items.size());
    }

    @Transactional
    public CaseNote addNote(UUID caseId, NoteCommand command, String idempotencyKey, String actor) {
        lockCase(caseId);
        String keyHash = sha256(idempotencyKey);
        String normalized = command.noteText().trim();
        String requestHash = sha256(caseId + "|" + normalized);
        CaseNote replay = findNote(caseId, keyHash, requestHash, true);
        if (replay != null) return replay;
        UUID noteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String integrityHash = sha256(caseId + "|" + noteId + "|" + actor + "|" + normalized + "|" + now);
        jdbcTemplate.update("""
                insert into operational_case_note (
                    note_id, case_id, note_text, created_by, request_hash,
                    idempotency_key_hash, integrity_hash, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, noteId, caseId, normalized, actor, requestHash, keyHash, integrityHash, now);
        return new CaseNote(noteId, caseId, normalized, actor, integrityHash, now, false);
    }

    @Transactional
    public CaseTransition review(UUID caseId, ReviewCommand command, String idempotencyKey, String reviewer) {
        CaseSummary current = detail(caseId).caseSummary();
        String keyHash = sha256(idempotencyKey);
        String requestHash = sha256(caseId + "|" + command.actionCode() + "|" + command.note()
                + "|" + command.expectedVersion());
        CaseTransition replay = findReviewReplay(caseId, keyHash, requestHash, current.version());
        if (replay != null) return replay;
        String next = nextStatus(current, command);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = jdbcTemplate.update("""
                update operational_protection_case
                   set task_status = ?, case_version = case_version + 1, updated_at = ?
                 where case_id = ? and case_version = ? and task_status = ?
                """, next, now, caseId, command.expectedVersion(), current.taskStatus());
        if (updated != 1) {
            CaseTransition concurrent = findReviewReplay(caseId, keyHash, requestHash,
                    detail(caseId).caseSummary().version());
            if (concurrent != null) return concurrent;
            throw new BusinessException(CaseworkErrorCode.CASE_STATE_CONFLICT);
        }
        jdbcTemplate.update("""
                insert into operational_case_review_event (
                    review_event_id, case_id, action_code, previous_status, resulting_status,
                    reviewer_subject, note, request_hash, idempotency_key_hash, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), caseId, command.actionCode(), current.taskStatus(), next,
                reviewer, command.note(), requestHash, keyHash, now);
        return transition(caseId, current.taskStatus(), next, command.expectedVersion() + 1,
                command.actionCode(), now, false);
    }

    @Transactional
    public GuidancePlan approveGuidance(UUID caseId, GuidancePlanCommand command, String approver) {
        CaseSummary current = detail(caseId).caseSummary();
        if (current.version() != command.expectedVersion() || !current.taskStatus().equals("IN_REVIEW")
                || current.assignedTo() == null) {
            throw new BusinessException(CaseworkErrorCode.CASE_STATE_CONFLICT);
        }
        Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from operational_guidance_plan where case_id = ?", Integer.class, caseId);
        if (existing != null && existing > 0) {
            throw new BusinessException(CaseworkErrorCode.GUIDANCE_ALREADY_APPROVED);
        }
        List<String> actions = command.selectedActionCodes().stream().distinct().sorted().toList();
        UUID planId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = jdbcTemplate.update("""
                update operational_protection_case
                   set task_status = 'GUIDANCE_APPROVED', case_version = case_version + 1, updated_at = ?
                 where case_id = ? and case_version = ? and task_status = 'IN_REVIEW'
                   and assigned_to is not null
                """, now, caseId, command.expectedVersion());
        if (updated != 1) throw new BusinessException(CaseworkErrorCode.CASE_STATE_CONFLICT);
        jdbcTemplate.update("""
                insert into operational_guidance_plan (
                    guidance_plan_id, case_id, selected_action_codes, approved_by, approved_at,
                    delivered, external_execution_created
                ) values (?, ?, ?::jsonb, ?, ?, false, false)
                """, planId, caseId, json(actions), approver, now);
        return new GuidancePlan(planId, caseId, actions, approver, now, command.expectedVersion() + 1,
                false, false);
    }

    private String nextStatus(CaseSummary current, ReviewCommand command) {
        if (current.version() != command.expectedVersion()) {
            throw new BusinessException(CaseworkErrorCode.CASE_STATE_CONFLICT);
        }
        if (command.actionCode().equals("START_REVIEW") && current.taskStatus().equals("PENDING")
                && current.assignedTo() != null) {
            return "IN_REVIEW";
        }
        if (command.actionCode().equals("COMPLETE_REVIEW")
                && List.of("IN_REVIEW", "GUIDANCE_APPROVED").contains(current.taskStatus())) {
            return "COMPLETED";
        }
        if (command.actionCode().equals("REOPEN_REVIEW") && current.taskStatus().equals("COMPLETED")) {
            return "IN_REVIEW";
        }
        throw new BusinessException(CaseworkErrorCode.CASE_STATE_CONFLICT);
    }

    private CaseTransition findReviewReplay(UUID caseId, String keyHash, String requestHash, long version) {
        List<CaseTransition> rows = jdbcTemplate.query("""
                select action_code, previous_status, resulting_status, request_hash, created_at
                  from operational_case_review_event
                 where case_id = ? and idempotency_key_hash = ?
                """, (rs, rowNum) -> {
                    if (!secureHashEquals(rs.getString("request_hash"), requestHash)) {
                        throw new BusinessException(CaseworkErrorCode.REVIEW_IDEMPOTENCY_CONFLICT);
                    }
                    return transition(caseId, rs.getString("previous_status"), rs.getString("resulting_status"),
                            version, rs.getString("action_code"),
                            rs.getObject("created_at", OffsetDateTime.class), true);
                }, caseId, keyHash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private CaseNote findNote(UUID caseId, String keyHash, String requestHash, boolean replayed) {
        List<CaseNote> rows = jdbcTemplate.query("""
                select note_id, case_id, note_text, created_by, request_hash, integrity_hash, created_at
                  from operational_case_note where case_id = ? and idempotency_key_hash = ?
                """, (rs, rowNum) -> {
                    if (!secureHashEquals(rs.getString("request_hash"), requestHash)) {
                        throw new BusinessException(CaseworkErrorCode.NOTE_IDEMPOTENCY_CONFLICT);
                    }
                    return mapNote(rs, replayed);
                }, caseId, keyHash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private CaseNote mapNote(ResultSet rs, boolean replayed) throws SQLException {
        return new CaseNote(rs.getObject("note_id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("note_text"), rs.getString("created_by"), rs.getString("integrity_hash"),
                rs.getObject("created_at", OffsetDateTime.class), replayed);
    }

    private void lockCase(UUID caseId) {
        List<UUID> rows = jdbcTemplate.query(
                "select case_id from operational_protection_case where case_id = ? for update",
                (rs, rowNum) -> rs.getObject("case_id", UUID.class), caseId);
        if (rows.size() != 1) throw new BusinessException(CaseworkErrorCode.CASE_NOT_FOUND);
    }

    private CursorPoint cursor(UUID caseId) {
        List<CursorPoint> rows = jdbcTemplate.query("""
                select case_id, created_at,
                       case review_priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end as rank
                  from operational_protection_case where case_id = ?
                """, (rs, rowNum) -> new CursorPoint(rs.getObject("case_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getInt("rank")), caseId);
        if (rows.size() != 1) throw new BusinessException(CaseworkErrorCode.CASE_NOT_FOUND);
        return rows.getFirst();
    }

    private CaseSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new CaseSummary(rs.getObject("case_id", UUID.class), rs.getObject("alert_id", UUID.class),
                rs.getObject("signal_id", UUID.class), rs.getString("customer_id"),
                rs.getString("review_priority"), rs.getString("task_status"), rs.getLong("case_version"),
                rs.getString("assigned_team"), rs.getString("assigned_to"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private CaseTransition transition(UUID id, String previous, String current, long version,
                                      String action, OffsetDateTime changedAt, boolean replayed) {
        return new CaseTransition(id, previous, current, version, action, changedAt, replayed, false, false);
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("안내계획 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("안내계획 JSON을 직렬화할 수 없습니다.", exception);
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

    private record CursorPoint(UUID caseId, OffsetDateTime createdAt, int priorityRank) {}
}
