package com.alzswell.compliance.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.compliance.api.ComplianceErrorCode;
import com.alzswell.compliance.api.ComplianceResponses.AuditEvent;
import com.alzswell.compliance.api.ComplianceResponses.AuditEventList;
import com.alzswell.compliance.api.ComplianceResponses.DataProvenance;
import com.alzswell.compliance.api.ComplianceResponses.DecisionTrace;
import com.alzswell.compliance.api.ComplianceResponses.ProvenanceNode;
import com.alzswell.compliance.api.ComplianceResponses.TraceStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceQueryService {
    private static final String EVENTS = """
            with events as (
              select 'DECISION:'||audit_id as event_key, 'DECISION' as source_type,
                     audit_id::text as source_id, event_type,
                     coalesce(actor_id,actor_type) as actor_subject, null::varchar as customer_id,
                     target_type,target_id,before_state,after_state,
                     event_payload || jsonb_build_object('policyVersion',policy_version,'algorithmVersion',algorithm_version),
                     event_hash as integrity_hash,occurred_at from decision_audit
              union all
              select 'ALERT:'||e.audit_event_id,'ALERT',e.audit_event_id::text,e.event_type,
                     coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_type),a.customer_id,
                     'ALERT',e.alert_id::text,e.previous_state,e.resulting_state,e.detail,e.integrity_hash,e.created_at
                from operational_alert_audit_event e join operational_alert a on a.alert_id=e.alert_id
              union all
              select 'CASE_REVIEW:'||e.review_event_id,'CASE_REVIEW',e.review_event_id::text,e.action_code,
                     e.reviewer_subject,c.customer_id,'CASE',e.case_id::text,e.previous_status,e.resulting_status,
                     jsonb_build_object('note',e.note,'requestHash',e.request_hash),e.request_hash,e.created_at
                from operational_case_review_event e join operational_protection_case c on c.case_id=e.case_id
              union all
              select 'CONSENT:'||e.event_id,'CONSENT',e.event_id::text,e.event_type,e.actor_id,c.customer_id,
                     'CONSENT',e.consent_id::text,null,e.status_snapshot,
                     jsonb_build_object('scopes',e.scope_snapshot,'reason',e.reason,'rowVersion',e.row_version),
                     null,e.occurred_at from customer_consent_event e join customer_consent c on c.consent_id=e.consent_id
              union all
              select 'TRUSTED_CONTACT:'||e.event_id,'TRUSTED_CONTACT',e.event_id::text,e.event_type,e.actor_id,c.customer_id,
                     'TRUSTED_CONTACT',e.contact_id::text,null,c.status,
                     jsonb_build_object('reason',e.reason,'rowVersion',e.row_version),null,e.occurred_at
                from trusted_contact_event e join trusted_contact c on c.contact_id=e.contact_id
              union all
              select 'CONSENT_ACCESS:'||evaluation_id,'CONSENT_ACCESS',evaluation_id::text,event_type,
                     coalesce(actor_principal_id::text,actor_customer_id,actor_type),customer_id,
                     'CONSENT',consent_id::text,null,decision,detail,request_hash,occurred_at
                from consent_access_audit_event
              union all
              select 'POLICY:'||event_id,'POLICY',event_id::text,event_type,actor_subject,null,
                     'DETECTION_POLICY',policy_id::text,from_status,to_status,'{}'::jsonb,rules_hash,occurred_at
                from detection_policy_event
              union all
              select 'FEATURE_FLAG:'||event_id,'FEATURE_FLAG',event_id::text,'DESIRED_VALUE_CHANGED',actor_subject,null,
                     'FEATURE_FLAG',flag_key,previous_desired_enabled::text,requested_enabled::text,
                     jsonb_build_object('approvalReference',approval_reference,'changeReason',change_reason),null,occurred_at
                from feature_flag_change_event
            )
            """;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ComplianceQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditEventList events(String sourceType, String eventType, String customerId,
                                 OffsetDateTime from, OffsetDateTime to, String cursor, int limit) {
        Cursor decoded = decode(cursor);
        StringBuilder sql = new StringBuilder(EVENTS).append(" select * from events where 1=1");
        List<Object> args = new ArrayList<>();
        add(sql, args, " and source_type=?", sourceType == null ? null : sourceType.toUpperCase(Locale.ROOT));
        add(sql, args, " and event_type=?", eventType);
        add(sql, args, " and customer_id=?", customerId);
        add(sql, args, " and occurred_at>=?", from);
        add(sql, args, " and occurred_at<=?", to);
        if (decoded != null) {
            sql.append(" and (occurred_at<? or (occurred_at=? and event_key<?))");
            args.add(decoded.occurredAt()); args.add(decoded.occurredAt()); args.add(decoded.eventId());
        }
        sql.append(" order by occurred_at desc,event_key desc limit ?");
        args.add(limit + 1);
        List<AuditEvent> rows = jdbcTemplate.query(sql.toString(), this::mapEvent, args.toArray());
        boolean hasNext = rows.size() > limit;
        List<AuditEvent> items = hasNext ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        String next = hasNext ? encode(items.getLast()) : null;
        return new AuditEventList(items, next, hasNext, items.size());
    }

    @Transactional(readOnly = true)
    public AuditEvent event(String eventId) {
        List<AuditEvent> rows = jdbcTemplate.query(EVENTS + " select * from events where event_key=?",
                this::mapEvent, eventId);
        if (rows.size() != 1) throw new BusinessException(ComplianceErrorCode.AUDIT_EVENT_NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public DecisionTrace decision(UUID decisionId) {
        List<AuditEvent> rows = jdbcTemplate.query(EVENTS + " select * from events where source_id=? "
                        + "and source_type in ('DECISION','ALERT','CASE_REVIEW','CONSENT_ACCESS') "
                        + "order by case source_type when 'DECISION' then 1 when 'ALERT' then 2 else 3 end limit 1",
                this::mapEvent, decisionId.toString());
        if (rows.isEmpty()) throw new BusinessException(ComplianceErrorCode.DECISION_TRACE_NOT_FOUND);
        AuditEvent event = rows.getFirst();
        String policy = text(event.detail(), "policyVersion");
        String algorithm = text(event.detail(), "algorithmVersion");
        TraceStep step = new TraceStep(event.sourceType(), event.sourceId(), event.afterState(),
                event.integrityHash(), event.detail(), event.occurredAt());
        return new DecisionTrace(decisionId, event.eventType(), event.customerId(), policy, algorithm,
                List.of(step), true, false, false);
    }

    @Transactional(readOnly = true)
    public DataProvenance provenance(String resourceType, UUID resourceId) {
        String type = resourceType.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "DETECTION_RUN" -> detectionRun(resourceId);
            case "SIGNAL" -> signal(resourceId);
            case "ALERT" -> alert(resourceId);
            case "CASE" -> protectionCase(resourceId);
            case "POLICY" -> policy(resourceId);
            default -> throw new BusinessException(ComplianceErrorCode.RESOURCE_TYPE_UNSUPPORTED);
        };
    }

    private DataProvenance detectionRun(UUID id) {
        return one("""
                select detection_run_id,dataset_id,algorithm_version,policy_version,policy_snapshot_hash,
                       input_payload_hash,result_hash,completed_at from synthetic_detection_run where detection_run_id=?
                """, id, (rs) -> new DataProvenance("DETECTION_RUN", id, List.of(
                new ProvenanceNode("SYNTHETIC_DATASET", rs.getObject("dataset_id", UUID.class).toString(), null,
                        rs.getString("input_payload_hash"), "INPUT"),
                new ProvenanceNode("DETECTION_POLICY", rs.getString("policy_version"),
                        rs.getString("policy_version"), rs.getString("policy_snapshot_hash"), "POLICY"),
                new ProvenanceNode("DETECTION_RESULT", id.toString(), rs.getString("algorithm_version"),
                        rs.getString("result_hash"), "OUTPUT")), true, false,
                rs.getObject("completed_at", OffsetDateTime.class)));
    }

    private DataProvenance signal(UUID id) {
        return one("""
                select signal_id,baseline_id,source_detection_run_id,algorithm_version,snapshot_hash,detected_at
                  from customer_detection_signal where signal_id=?
                """, id, rs -> new DataProvenance("SIGNAL", id, nodes(
                node("BASELINE", rs.getObject("baseline_id", UUID.class), null, null, "BASELINE"),
                node("DETECTION_RUN", rs.getObject("source_detection_run_id", UUID.class),
                        rs.getString("algorithm_version"), rs.getString("snapshot_hash"), "SOURCE")),
                true, false, rs.getObject("detected_at", OffsetDateTime.class)));
    }

    private DataProvenance alert(UUID id) {
        return one("select alert_id,signal_id,reason_code,updated_at from operational_alert where alert_id=?", id,
                rs -> new DataProvenance("ALERT", id, List.of(node("SIGNAL",
                        rs.getObject("signal_id", UUID.class), rs.getString("reason_code"), null, "SOURCE")),
                        true, false, rs.getObject("updated_at", OffsetDateTime.class)));
    }

    private DataProvenance protectionCase(UUID id) {
        return one("select case_id,alert_id,signal_id,case_version,updated_at from operational_protection_case where case_id=?",
                id, rs -> new DataProvenance("CASE", id, List.of(
                        node("ALERT", rs.getObject("alert_id", UUID.class), null, null, "SOURCE"),
                        node("SIGNAL", rs.getObject("signal_id", UUID.class), null, null, "EVIDENCE")),
                        true, false, rs.getObject("updated_at", OffsetDateTime.class)));
    }

    private DataProvenance policy(UUID id) {
        return one("select policy_id,version_code,rules_hash,based_on_policy_id,coalesce(published_at,created_at) observed_at "
                        + "from detection_policy_version where policy_id=?", id,
                rs -> new DataProvenance("POLICY", id, nodes(node("POLICY",
                        id, rs.getString("version_code"), rs.getString("rules_hash"), "CURRENT_VERSION"),
                        node("POLICY", rs.getObject("based_on_policy_id", UUID.class), null,
                                null, "BASED_ON")), true, false,
                        rs.getObject("observed_at", OffsetDateTime.class)));
    }

    private AuditEvent mapEvent(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuditEvent(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),
                json(rs.getString(11)),rs.getString(12),rs.getObject(13,OffsetDateTime.class),true);
    }

    private <T> T one(String sql, UUID id, SqlMapper<T> mapper) {
        List<T> rows = jdbcTemplate.query(sql, (rs,n) -> mapper.map(rs), id);
        if (rows.size() != 1) throw new BusinessException(ComplianceErrorCode.PROVENANCE_NOT_FOUND);
        return rows.getFirst();
    }

    private List<ProvenanceNode> nodes(ProvenanceNode... values) {
        return java.util.Arrays.stream(values).filter(java.util.Objects::nonNull).toList();
    }

    private ProvenanceNode node(String type, UUID id, String version, String hash, String relationship) {
        return id == null ? null : new ProvenanceNode(type,id.toString(),version,hash,relationship);
    }

    private void add(StringBuilder sql, List<Object> args, String fragment, Object value) {
        if (value != null) { sql.append(fragment); args.add(value); }
    }

    private JsonNode json(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("감사 detail을 읽을 수 없습니다.", exception); }
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asText() : null;
    }

    private String encode(AuditEvent event) {
        String raw = event.occurredAt().toInstant().toEpochMilli() + "|" + event.eventId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String value) {
        if (value == null) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneOffset.UTC), parts[1]);
        } catch (RuntimeException exception) {
            throw new BusinessException(ComplianceErrorCode.CURSOR_INVALID);
        }
    }

    private record Cursor(OffsetDateTime occurredAt, String eventId) {}
    @FunctionalInterface private interface SqlMapper<T> { T map(java.sql.ResultSet rs) throws java.sql.SQLException; }
}
