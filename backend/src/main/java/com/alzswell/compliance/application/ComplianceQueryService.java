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
              select 'ALERT_APPEAL:'||p.appeal_id,'ALERT_APPEAL',p.appeal_id::text,'APPEAL_SUBMITTED',
                     p.actor_customer_id,p.customer_id,'ALERT',p.alert_id::text,p.previous_state,p.resulting_state,
                     jsonb_build_object('reasonCode',p.reason_code,'caseId',p.case_id,'status',p.status),
                     p.integrity_hash,p.created_at from operational_alert_appeal p
              union all
              select 'CASE_REVIEW:'||e.review_event_id,'CASE_REVIEW',e.review_event_id::text,e.action_code,
                     e.reviewer_subject,c.customer_id,'CASE',e.case_id::text,e.previous_status,e.resulting_status,
                     jsonb_build_object('note',e.note,'requestHash',e.request_hash),e.request_hash,e.created_at
                from operational_case_review_event e join operational_protection_case c on c.case_id=e.case_id
              union all
              select 'CASE_OVERRIDE:'||e.override_event_id,'CASE_OVERRIDE',e.override_event_id::text,
                     'POLICY_OVERRIDE_REVIEW',e.reviewer_principal_id::text,c.customer_id,'CASE',e.case_id::text,
                     e.previous_status,e.resulting_status,
                     jsonb_build_object('reasonCode',e.reason_code,'policyVersion',e.policy_version),
                     e.integrity_hash,e.created_at
                from operational_case_override_event e join operational_protection_case c on c.case_id=e.case_id
              union all
              select 'CONSENT:'||e.event_id,'CONSENT',e.event_id::text,e.event_type,e.actor_id,c.customer_id,
                     'CONSENT',e.consent_id::text,null,e.status_snapshot,
                     jsonb_build_object('scopes',e.scope_snapshot,'reason',e.reason,'rowVersion',e.row_version),
                     null,e.occurred_at from customer_consent_event e join customer_consent c on c.consent_id=e.consent_id
              union all
              select 'TRUSTED_CONTACT:'||e.event_id,'TRUSTED_CONTACT',e.event_id::text,e.event_type,e.actor_id,c.customer_id,
                     'TRUSTED_CONTACT',e.contact_id::text,null,e.status_snapshot,
                     jsonb_build_object('reason',e.reason,'rowVersion',e.row_version,'scopes',e.scope_snapshot,
                         'snapshotAccuracy',e.snapshot_accuracy),null,e.occurred_at
                from trusted_contact_event e join trusted_contact c on c.contact_id=e.contact_id
              union all
              select 'CONSENT_ACCESS:'||evaluation_id,'CONSENT_ACCESS',evaluation_id::text,event_type,
                     coalesce(actor_principal_id::text,actor_customer_id,actor_type),customer_id,
                     'CONSENT',consent_id::text,null,decision,
                     detail || jsonb_build_object('policyVersion',policy_version),request_hash,occurred_at
                from consent_access_audit_event
              union all
              select 'PRIVACY_REQUEST:'||e.event_id,'PRIVACY_REQUEST',e.event_id::text,e.event_type,
                     coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_type),r.customer_id,
                     'PRIVACY_REQUEST',e.request_id::text,null,e.status_snapshot,e.detail,null,e.occurred_at
                from customer_privacy_request_event e join customer_privacy_request r on r.request_id=e.request_id
              union all
              select 'AUDIT_EXPORT:'||e.event_id,'AUDIT_EXPORT',e.event_id::text,e.event_type,
                     coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_type),r.actor_customer_id,
                     'AUDIT_EXPORT',e.request_id::text,null,e.status_snapshot,e.detail,null,e.occurred_at
                from audit_export_request_event e join audit_export_request r on r.request_id=e.request_id
              union all
              select 'FINANCIAL_INTENT:'||e.event_id,'FINANCIAL_INTENT',e.event_id::text,e.event_type,
                     coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_type),i.customer_id,
                     'FINANCIAL_INTENT',e.intent_id::text,null,e.status_snapshot,
                     e.detail || jsonb_build_object('version',e.version),null,e.occurred_at
                from financial_intent_event e join financial_intent i on i.intent_id=e.intent_id
              union all
              select 'CASE_NOTE:'||n.note_id,'CASE_NOTE',n.note_id::text,'INTERNAL_NOTE_ADDED',n.created_by,c.customer_id,
                     'CASE',n.case_id::text,null,null,jsonb_build_object('note',n.note_text),n.integrity_hash,n.created_at
                from operational_case_note n join operational_protection_case c on c.case_id=n.case_id
              union all
              select 'CASE_FOLLOW_UP:'||e.follow_up_event_id,'CASE_FOLLOW_UP',e.follow_up_event_id::text,e.event_type,
                     e.actor_subject,c.customer_id,'CASE',e.case_id::text,e.previous_status,e.resulting_status,
                     e.detail,e.integrity_hash,e.created_at
                from operational_case_follow_up_event e join operational_protection_case c on c.case_id=e.case_id
              union all
              select 'CASE_ASSIGNMENT:'||e.activity_id,'CASE_ASSIGNMENT',e.activity_id::text,e.activity_type,
                     e.actor_subject,c.customer_id,'CASE',e.case_id::text,e.previous_status,e.resulting_status,
                     e.detail || jsonb_build_object('snapshotAccuracy',e.snapshot_accuracy),null,e.occurred_at
                from operational_case_activity e join operational_protection_case c on c.case_id=e.case_id
              union all
              select 'GUIDANCE_PLAN:'||g.guidance_plan_id,'GUIDANCE_PLAN',g.guidance_plan_id::text,
                     'GUIDANCE_PLAN_APPROVED',g.approved_by,c.customer_id,'CASE',g.case_id::text,null,
                     'GUIDANCE_APPROVED',jsonb_build_object('selectedActionCodes',g.selected_action_codes,
                         'delivered',g.delivered,'externalExecutionCreated',g.external_execution_created),null,g.approved_at
                from operational_guidance_plan g join operational_protection_case c on c.case_id=g.case_id
              union all
              select 'RECURRING_REMINDER:'||e.event_id,'RECURRING_REMINDER',e.event_id::text,
                     'REMINDER_SETTING_UPDATED',coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_type),
                     p.customer_id,'RECURRING_PAYMENT',e.recurring_payment_id::text,null,
                     e.enabled_snapshot::text,jsonb_build_object('enabled',e.enabled_snapshot,
                         'leadDays',e.lead_days_snapshot,'version',e.version_snapshot),null,e.occurred_at
                from recurring_payment_reminder_event e join recurring_payment p using(recurring_payment_id)
              union all
              select 'ACCOUNT_DISPLAY:'||e.event_id,'ACCOUNT_DISPLAY',e.event_id::text,
                     'ACCOUNT_DISPLAY_SETTING_UPDATED',
                     coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_id,e.actor_type),
                     e.customer_id,'ACCOUNT',e.account_id::text,
                     null,e.row_version::text,jsonb_build_object('alias',e.alias_snapshot,
                         'displayOrder',e.display_order_snapshot,'hidden',e.hidden_snapshot,
                         'actorSessionId',e.actor_session_id,'actorType',e.actor_type),null,e.occurred_at
                from account_display_setting_event e
              union all
              select 'TRANSACTION_PREFERENCE:'||e.event_id,'TRANSACTION_PREFERENCE',e.event_id::text,
                     e.event_type,coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_id,e.actor_type),
                     e.customer_id,'TRANSACTION',e.transaction_id::text,null,
                     e.row_version::text,jsonb_build_object('category',e.category_snapshot,
                         'note',e.note_snapshot,'actorSessionId',e.actor_session_id,
                         'actorType',e.actor_type),null,e.occurred_at
                from customer_transaction_preference_event e
              union all
              select 'WATCHLIST:'||e.event_id,'WATCHLIST',e.event_id::text,e.event_type,
                     coalesce(e.actor_principal_id::text,e.actor_id::text,e.actor_customer_id,e.actor_type),e.customer_id,
                     'CUSTOMER_WATCHLIST',e.customer_id,(e.version-1)::text,e.version::text,
                     jsonb_build_object('instrumentIds',e.instrument_ids,'version',e.version,
                         'actorSessionId',e.actor_session_id,'actorType',e.actor_type,
                         'integrityHashVersion',e.event_hash_version),
                     'sha256:'||e.event_hash,e.occurred_at
                from customer_watchlist_event e
              union all
              select 'STAFF_ACCESS:'||e.event_id,'STAFF_ACCESS',e.event_id::text,e.event_type,
                     coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_type),e.customer_id_snapshot,
                     'STAFF_ACCESS_GRANT',e.grant_id::text,null,e.status_snapshot,
                     e.detail || jsonb_build_object('scopes',e.scopes_snapshot,
                         'purposeCode',e.purpose_code_snapshot,'staffPrincipalId',e.staff_principal_id_snapshot,
                         'snapshotAccuracy',e.snapshot_accuracy),null,e.occurred_at
                from staff_access_grant_event e
              union all
              select 'STAFF_ACCESS_DECISION:'||e.evaluation_id,'STAFF_ACCESS_DECISION',e.evaluation_id::text,
                     'STAFF_ACCESS_EVALUATED',coalesce(e.actor_principal_id::text,e.actor_customer_id,e.actor_type),
                     e.customer_id,coalesce(e.resource_type,'STAFF_ACCESS_POLICY'),
                     coalesce(e.resource_id,e.evaluation_id::text),null,e.decision_code,
                     jsonb_build_object('grantId',e.grant_id,'staffPrincipalId',e.staff_principal_id,
                         'purposeCode',e.purpose_code,'scopeCode',e.scope_code,'allowed',e.allowed),null,e.occurred_at
                from staff_access_decision_audit_event e
              union all
              select 'POLICY:'||event_id,'POLICY',event_id::text,event_type,actor_subject,null,
                     'DETECTION_POLICY',policy_id::text,from_status,to_status,'{}'::jsonb,rules_hash,occurred_at
                from detection_policy_event
              union all
              select 'FEATURE_FLAG:'||event_id,'FEATURE_FLAG',event_id::text,'DESIRED_VALUE_CHANGED',actor_subject,null,
                     'FEATURE_FLAG',flag_key,previous_desired_enabled::text,requested_enabled::text,
                     jsonb_build_object('approvalReference',approval_reference,'changeReason',change_reason),null,occurred_at
                from feature_flag_change_event
              union all
              select 'KNOWLEDGE_GOVERNANCE:'||event_id,'KNOWLEDGE_GOVERNANCE',event_id::text,event_type,
                     actor_subject,null,'KNOWLEDGE_DOCUMENT',document_id||':'||version_label,null,
                     state_snapshot->>'lifecycleStatus',state_snapshot ||
                       jsonb_build_object('approvalReference',approval_reference),integrity_hash,occurred_at
                from knowledge_governance_event
              union all
              select 'KNOWLEDGE_IMPORT:'||import_id,'KNOWLEDGE_IMPORT',import_id::text,'INGESTION_IMPORTED',
                     imported_by,null,'KNOWLEDGE_DOCUMENT',document_id||':'||version_label,null,'SEARCHABLE',
                     jsonb_build_object('ingestionRunId',ingestion_run_id,'sourceHash',source_hash,'asOf',as_of,
                         'extractorVersion',extractor_version,'chunkerVersion',chunker_version,
                         'chunkCount',chunk_count,'payloadHash',payload_hash),integrity_hash,imported_at
                from knowledge_ingestion_import
              union all
              select 'KNOWLEDGE_ACCESS:'||access_event_id,'KNOWLEDGE_ACCESS',access_event_id::text,event_type,
                     actor_subject,null,
                     case when event_type='PASSAGE_DETAIL' then 'KNOWLEDGE_PASSAGE'
                          when event_type='SEARCH' then 'KNOWLEDGE_SEARCH' else 'KNOWLEDGE_DOCUMENT' end,
                     coalesce(requested_resource_id,access_event_id::text),null,outcome,
                     detail || jsonb_build_object('permissionCode',permission_code,'principalRoles',principal_roles,
                         'requesterAudiences',requester_audiences,'queryHash',query_hash,'asOf',as_of,
                         'returnedResourceIds',returned_resource_ids),integrity_hash,occurred_at
                from knowledge_access_audit_event
              union all
              select 'AUTH_SESSION:'||event_id,'AUTH_SESSION',event_id::text,event_type,
                     principal_id::text,null,'AUTH_SESSION',target_session_id::text,null,outcome,
                     jsonb_build_object('actorSessionId',actor_session_id,'reasonCode',reason_code),
                     integrity_hash,occurred_at
                from auth_session_event
              union all
              select 'TRANSFER_TEMPLATE:'||event_id,'TRANSFER_TEMPLATE',event_id::text,event_type,
                     actor_subject,customer_id,'TRANSFER_TEMPLATE',template_id::text,null,status_snapshot,
                     jsonb_build_object('sourceAccountId',source_account_id,'beneficiaryId',beneficiary_id,
                         'templateName',template_name,'amount',amount,'currency',currency,
                         'purposeCode',purpose_code,'version',version_snapshot),integrity_hash,occurred_at
                from customer_transfer_template_event
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
        Instant instant = event.occurredAt().toInstant();
        String raw = "v2|" + instant.getEpochSecond() + "|" + instant.getNano() + "|" + event.eventId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String value) {
        if (value == null) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            if (raw.startsWith("v2|")) {
                String[] parts = raw.split("\\|", 4);
                if (parts.length != 4) throw new IllegalArgumentException("cursor parts");
                Instant instant = Instant.ofEpochSecond(Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
                return new Cursor(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC), parts[3]);
            }
            String[] parts = raw.split("\\|", 2);
            return new Cursor(OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneOffset.UTC), parts[1]);
        } catch (RuntimeException exception) {
            throw new BusinessException(ComplianceErrorCode.CURSOR_INVALID);
        }
    }

    private record Cursor(OffsetDateTime occurredAt, String eventId) {}
    @FunctionalInterface private interface SqlMapper<T> { T map(java.sql.ResultSet rs) throws java.sql.SQLException; }
}
