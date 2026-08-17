package com.alzswell.demo.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import com.alzswell.demo.api.P0WorkflowErrorCode;
import com.alzswell.demo.api.P0WorkflowRequests.CaseReviewCommand;
import com.alzswell.demo.api.P0WorkflowRequests.CaseNoteCommand;
import com.alzswell.demo.api.P0WorkflowRequests.FollowUpCommand;
import com.alzswell.demo.api.P0WorkflowRequests.FollowUpUpdateCommand;
import com.alzswell.demo.api.P0WorkflowRequests.ContextCommand;
import com.alzswell.demo.api.P0WorkflowRequests.GuidancePlanCommand;
import com.alzswell.demo.api.P0WorkflowResult;
import com.alzswell.demo.domain.DemoSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class P0WorkflowService {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$");
    private static final List<Pattern> SENSITIVE_NOTE_PATTERNS = List.of(
            Pattern.compile("(?iu)(치매|알츠하이머|주민\\s*등록(?:번호)?)"),
            Pattern.compile("(?iu)(?:계좌|카드)\\s*(?:번호)?\\s*[:#=\\-]?\\s*\\d[\\d .\\-]{5,}"),
            Pattern.compile("(?<!\\d)\\d{6}[- ]?\\d{7}(?!\\d)"),
            Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)"),
            Pattern.compile("(?iu)(?:\\+?82[- .]?)?0?1[016789][- .]?\\d{3,4}[- .]?\\d{4}"),
            Pattern.compile("(?iu)[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}"),
            Pattern.compile("(?iu)(?:이름|성명|name)\\s*[:=]\\s*[가-힣A-Z][가-힣A-Z .\\-]{1,40}")
    );
    private static final Pattern COMPACT_SENSITIVE_NOTE_PATTERN = Pattern.compile(
            "(?iu)(치매|알츠하이머|주민등록(?:번호)?|계좌번호|카드번호|전화번호|이메일|email|성명|이름|name)"
    );
    private static final String CUSTOMER_ROLE = "CUSTOMER_DEMO";
    private static final String STAFF_ROLE = "DEMO_STAFF";
    private static final String POLICY_VERSION_DEFAULT = "context-policy-v1.0.0";
    private static final List<String> REASON_CODES = List.of(
            "MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"
    );
    private static final Map<String, Integer> EXPECTED_SIGNAL_COUNTS = Map.of(
            "MISSED_RECURRING", 3,
            "DUPLICATE_TRANSFER", 2,
            "REPEATED_CONFIRMATION", 7
    );
    private static final Map<String, Integer> EXPECTED_WINDOWS = Map.of(
            "MISSED_RECURRING", 60 * 24 * 60 * 60,
            "DUPLICATE_TRANSFER", 10 * 60,
            "REPEATED_CONFIRMATION", 60 * 60
    );
    private static final Set<String> FOLLOW_UP_STATUSES = Set.of("COMPLETED", "CANCELLED");
    private static final List<String> CONTEXT_TYPES = List.of(
            "PAYMENT_PROVIDER_DELAY_VERIFIED",
            "ACCOUNT_CONNECTION_OUTAGE_VERIFIED",
            "DUPLICATE_TRANSFER_REFUNDED",
            "RESULT_SCREEN_DELAY_VERIFIED"
    );
    private static final List<String> CONTEXT_EVIDENCE_IDS = List.of(
            "PAYMENT_DELAY_SYN_001",
            "CONNECTION_OUTAGE_SYN_001",
            "TRANSFER_REFUND_SYN_001",
            "RESULT_DISPLAY_DELAY_SYN_001"
    );
    private static final Set<String> REVIEW_ACTIONS = Set.of(
            "START_REVIEW", "RESUME_REVIEW", "REQUIRE_FOLLOW_UP", "CLOSE_FALSE_POSITIVE"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DemoSessionService sessionService;
    private final DemoAuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String policyVersion;

    public P0WorkflowService(
            JdbcTemplate jdbcTemplate,
            DemoSessionService sessionService,
            DemoAuditWriter auditWriter,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.versions.policy:" + POLICY_VERSION_DEFAULT + "}") String policyVersion
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionService = sessionService;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.policyVersion = policyVersion;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> alertList(UUID sessionId, UUID demoRunId, String customerId) {
        DemoSession session = requireCurrentRun(sessionId, demoRunId);
        if (!DemoSessionService.CUSTOMER_ID.equals(customerId)) {
            throw new BusinessException(P0WorkflowErrorCode.SYNTHETIC_CUSTOMER_NOT_FOUND);
        }
        IncidentRow incident = requireIncident(sessionId, demoRunId, DemoSessionService.ALERT_ID, false);
        List<SignalRow> signals = signals(sessionId, demoRunId, incident.alertId());
        Map<String, Object> item = map(
                "alertId", incident.alertId(),
                "state", incident.state(),
                "incidentVersion", incident.incidentVersion(),
                "title", "정기납부·중복송금·거래확인 변화가 있어요",
                "summary", summary(signals),
                "reasonCodes", reasonCodes(signals),
                "evidencePhase", "T0_ALERT",
                "observedAt", incident.alertSnapshotAt(),
                "algorithmVersion", algorithmVersion(signals)
        );
        return map(
                "demoRunId", demoRunId,
                "customerId", customerId,
                "syntheticData", true,
                "items", List.of(item)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> alertDetail(UUID sessionId, UUID demoRunId, String alertId) {
        requireCurrentRun(sessionId, demoRunId);
        IncidentRow incident = requireIncident(sessionId, demoRunId, alertId, false);
        List<SignalRow> signals = signals(sessionId, demoRunId, alertId);
        return map(
                "demoRunId", demoRunId,
                "alertId", alertId,
                "customerId", incident.customerId(),
                "syntheticData", true,
                "state", incident.state(),
                "incidentVersion", incident.incidentVersion(),
                "preDecision", incident.preDecision(),
                "postDecision", incident.postDecision(),
                "reasonCodes", reasonCodes(signals),
                "t0AlertEvidence", t0Evidence(sessionId, demoRunId, incident, signals),
                "t1ContextEvidence", readNullableMap(incident.t1ContextEvidenceJson()),
                "trustedContactGate", readRequiredMap(incident.trustedContactGateJson()),
                "algorithmVersion", algorithmVersion(signals),
                "policyVersion", policyVersion
        );
    }

    @Transactional
    public P0WorkflowResult applyContext(
            UUID sessionId,
            UUID demoRunId,
            String alertId,
            String capabilityHash,
            String idempotencyKey,
            ContextCommand request
    ) {
        requireCurrentRun(sessionId, demoRunId);
        validateCommandHeaders(capabilityHash, idempotencyKey);
        validateContextCombination(request);
        String path = "/api/v1/demo/sessions/" + sessionId + "/alerts/" + alertId + "/context";
        String requestHash = requestHash("POST", path, map(
                "demoBranchCode", request.demoBranchCode(),
                "responseCode", request.responseCode()
        ));
        CommandScope scope = commandScope(sessionId, demoRunId, capabilityHash, CUSTOMER_ROLE, path, idempotencyKey);

        IncidentRow incident = requireIncident(sessionId, demoRunId, alertId, true);
        P0WorkflowResult replay = findReplay(scope, requestHash);
        if (replay != null) {
            return replay;
        }
        if (!"AWAITING_CONTEXT".equals(incident.state())) {
            throw new BusinessException(P0WorkflowErrorCode.ALERT_CONTEXT_ALREADY_SUBMITTED);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SignalRow> signals = signals(sessionId, demoRunId, alertId);
        boolean requestedNormalBranch = "FIN_MGMT_A_NORMAL_CONTEXT".equals(request.demoBranchCode());
        List<Map<String, Object>> evidenceRefs = requestedNormalBranch ? verifiedContextEvidence(now) : List.of();
        boolean structuralEvidenceMatched = requestedNormalBranch
                && strongSignalInvariantHolds(signals)
                && completeEvidencePackage(evidenceRefs);
        boolean closeNormal = requestedNormalBranch && structuralEvidenceMatched;

        String contextEventId = requestedNormalBranch ? "CTX_FIN_MGMT_A_001" : "CTX_FIN_MGMT_B_001";
        List<String> contextTypes = closeNormal ? CONTEXT_TYPES : List.of();
        List<String> contextEvidenceIds = closeNormal ? CONTEXT_EVIDENCE_IDS : List.of();
        Map<String, Object> t1Evidence = map(
                "phase", "T1_CONTEXT",
                "structuralEvidenceMatched", closeNormal,
                "contextTypes", contextTypes,
                "contextEvidenceIds", contextEvidenceIds,
                "contextEvidenceRefs", closeNormal ? evidenceRefs : List.of(),
                "observedAt", now
        );
        String postDecision = closeNormal ? "CLOSE_AS_NORMAL_CONTEXT" : "REQUIRE_BANK_REVIEW";
        String nextState = closeNormal ? "CLOSED_NORMAL" : "PENDING_BANK_REVIEW";
        Map<String, Object> gate = trustedContactGate(!closeNormal);
        long nextIncidentVersion = incident.incidentVersion() + 1;

        int updated = jdbcTemplate.update(
                """
                update alert_incident
                   set state = ?, incident_version = ?, post_decision = ?, response_code = ?,
                       demo_branch_code = ?, t1_context_evidence = cast(? as jsonb),
                       trusted_contact_gate = cast(? as jsonb), context_observed_at = ?, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and alert_id = ?
                   and state = 'AWAITING_CONTEXT' and incident_version = ?
                """,
                nextState, nextIncidentVersion, postDecision, request.responseCode(),
                request.demoBranchCode(), toJson(t1Evidence), toJson(gate), now, now,
                sessionId, demoRunId, alertId, incident.incidentVersion()
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.ALERT_CONTEXT_ALREADY_SUBMITTED);
        }
        jdbcTemplate.update(
                """
                insert into context_event (
                    demo_session_id, demo_run_id, context_event_id, alert_id, response_code,
                    demo_branch_code, structural_evidence_matched, context_types,
                    context_evidence_ids, context_evidence_refs, request_hash,
                    idempotency_key_hash, observed_at, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb),
                          cast(? as jsonb), ?, ?, ?, ?)
                """,
                sessionId, demoRunId, contextEventId, alertId, request.responseCode(),
                request.demoBranchCode(), closeNormal, toJson(contextTypes),
                toJson(contextEvidenceIds), toJson(closeNormal ? evidenceRefs : List.of()),
                requestHash, scope.idempotencyKeyHash(), now, now
        );
        jdbcTemplate.update(
                "update demo_run set context_package_hash = ? where demo_session_id = ? and demo_run_id = ?",
                sha256(toJson(t1Evidence)), sessionId, demoRunId
        );

        String caseId = null;
        if (!closeNormal) {
            caseId = DemoSessionService.CASE_ID;
            jdbcTemplate.update(
                    """
                    insert into protection_case (
                        demo_session_id, demo_run_id, case_id, alert_id, customer_id,
                        review_priority, review_task_status, case_version, customer_response_code,
                        created_at, updated_at
                    ) values (?, ?, ?, ?, ?, 'HIGH', 'PENDING', 1, ?, ?, ?)
                    """,
                    sessionId, demoRunId, caseId, alertId, incident.customerId(), request.responseCode(), now, now
            );
        }

        writeContextAudits(sessionId, demoRunId, alertId, incident.state(), nextState,
                closeNormal, requestHash, scope.idempotencyKeyHash(), contextEvidenceIds, now);

        Map<String, Object> data = map(
                "demoRunId", demoRunId,
                "contextEventId", contextEventId,
                "alertId", alertId,
                "caseId", caseId,
                "incidentVersion", nextIncidentVersion,
                "contextResponse", map(
                        "responseCode", request.responseCode(),
                        "demoBranchCode", request.demoBranchCode()
                ),
                "t1ContextEvidence", t1Evidence,
                "preDecision", incident.preDecision(),
                "postDecision", postDecision,
                "previousState", incident.state(),
                "currentState", nextState,
                "trustedContactGate", gate,
                "nextAction", closeNormal
                        ? map("type", "SHOW_CHECKLIST", "actionCode", "RECHECK_RECURRING_PAYMENT")
                        : map("type", "OPEN_BANK_REVIEW", "actionCode", "REVIEW_CASE"),
                "policyVersion", policyVersion,
                "command", map("requestHash", requestHash, "idempotencyReplayed", false)
        );
        P0WorkflowResult result = new P0WorkflowResult(
                closeNormal ? "ALERT_CONTEXT_APPLIED" : "ALERT_ESCALATED_TO_BANK_REVIEW",
                closeNormal
                        ? "생활맥락을 반영해 변화를 다시 확인했습니다."
                        : "추가 설명이 필요해 은행 검토로 연결했습니다.",
                data
        );
        saveCommand(scope, requestHash, result, now);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> alertAudit(
            UUID sessionId,
            UUID demoRunId,
            String alertId,
            String cursor,
            int limit
    ) {
        requireCurrentRun(sessionId, demoRunId);
        requireIncident(sessionId, demoRunId, alertId, false);
        validateLimit(limit);
        AuditCursor decoded = decodeAuditCursor(cursor);
        List<Object> args = new ArrayList<>(List.of(sessionId, demoRunId, alertId));
        StringBuilder sql = new StringBuilder("""
                select audit_id, demo_run_id, event_type, actor_type, policy_version,
                       algorithm_version, schema_version, event_payload::text payload,
                       trace_id, occurred_at
                  from decision_audit
                 where demo_session_id = ? and demo_run_id = ?
                   and event_payload ->> 'alertId' = ?
                """);
        if (decoded != null) {
            sql.append(" and (occurred_at < ? or (occurred_at = ? and audit_id < ?))");
            args.add(decoded.occurredAt());
            args.add(decoded.occurredAt());
            args.add(decoded.auditId());
        }
        sql.append(" order by occurred_at desc, audit_id desc limit ?");
        args.add(limit + 1);
        List<AuditRow> rows = jdbcTemplate.query(sql.toString(), this::auditRow, args.toArray());
        boolean hasMore = rows.size() > limit;
        List<AuditRow> page = hasMore ? rows.subList(0, limit) : rows;
        List<Map<String, Object>> items = page.stream().map(this::toAuditItem).toList();
        String nextCursor = hasMore ? encodeAuditCursor(page.getLast()) : null;
        return map("items", items, "nextCursor", nextCursor, "hasMore", hasMore);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> caseQueue(
            UUID sessionId,
            UUID demoRunId,
            String state,
            String reviewPriority,
            String cursor,
            int limit
    ) {
        DemoSession session = requireCurrentRun(sessionId, demoRunId);
        validateLimit(limit);
        validateCaseFilters(state, reviewPriority);
        CaseCursor decoded = decodeCaseCursor(cursor);
        List<Object> args = new ArrayList<>(List.of(sessionId, demoRunId));
        StringBuilder sql = new StringBuilder("""
                select c.demo_run_id, c.case_id, c.alert_id, c.customer_id, i.state,
                       c.review_priority, c.customer_response_code, i.trusted_contact_gate::text,
                       c.created_at, c.case_version
                  from protection_case c
                  join alert_incident i
                    on i.demo_session_id = c.demo_session_id
                   and i.demo_run_id = c.demo_run_id and i.alert_id = c.alert_id
                 where c.demo_session_id = ? and c.demo_run_id = ?
                """);
        if (state != null && !state.isBlank()) {
            sql.append(" and i.state = ?");
            args.add(state);
        }
        if (reviewPriority != null && !reviewPriority.isBlank()) {
            sql.append(" and c.review_priority = ?");
            args.add(reviewPriority);
        }
        if (decoded != null) {
            sql.append(" and (c.created_at > ? or (c.created_at = ? and c.case_id > ?))");
            args.add(decoded.createdAt());
            args.add(decoded.createdAt());
            args.add(decoded.caseId());
        }
        sql.append(" order by case c.review_priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end,")
                .append(" c.created_at asc, c.case_id asc limit ?");
        args.add(limit + 1);
        List<QueueRow> rows = jdbcTemplate.query(sql.toString(), this::queueRow, args.toArray());
        boolean hasMore = rows.size() > limit;
        List<QueueRow> page = hasMore ? rows.subList(0, limit) : rows;
        List<Map<String, Object>> items = page.stream().map(row -> map(
                "demoRunId", row.demoRunId(),
                "caseId", row.caseId(),
                "alertId", row.alertId(),
                "customerId", row.customerId(),
                "state", row.state(),
                "reviewPriority", row.reviewPriority(),
                "reasonCodes", REASON_CODES,
                "customerResponseCode", row.customerResponseCode(),
                "summary", "정기납부 누락·중복송금·반복확인을 본인이 확인하기 어렵고 정상 구조적 근거가 없습니다.",
                "trustedContactGate", readRequiredMap(row.trustedContactGateJson()),
                "createdAt", row.createdAt(),
                "caseVersion", row.caseVersion(),
                "sessionResetVersion", session.getResetVersion()
        )).toList();
        String nextCursor = hasMore ? encodeCaseCursor(page.getLast()) : null;
        return map("items", items, "nextCursor", nextCursor, "hasMore", hasMore);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> caseDetail(UUID sessionId, UUID demoRunId, String caseId) {
        DemoSession session = requireCurrentRun(sessionId, demoRunId);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, false);
        List<SignalRow> signals = signals(sessionId, demoRunId, row.alertId());
        Map<String, Object> guidance = guidanceSummary(sessionId, demoRunId, caseId);
        return map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "caseVersion", row.caseVersion(),
                "sessionResetVersion", session.getResetVersion(),
                "state", row.incidentState(),
                "reviewPriority", row.reviewPriority(),
                "alert", map(
                        "alertId", row.alertId(),
                        "preDecision", row.preDecision(),
                        "postDecision", row.postDecision(),
                        "reasonCodes", reasonCodes(signals),
                        "algorithmVersion", algorithmVersion(signals),
                        "policyVersion", policyVersion
                ),
                "customerContext", map(
                        "responseCode", row.customerResponseCode(),
                        "contextTypes", contextTypes(sessionId, demoRunId, row.alertId()),
                        "confirmedItems", List.of(),
                        "unconfirmedItems", List.of(
                                "최근 60일 정기납부 누락 3건",
                                "10분 내 중복송금 2회",
                                "완료 후 1시간 내 반복확인 7회"
                        )
                ),
                "timeline", timeline(sessionId, demoRunId, row),
                "suggestedQuestions", List.of(map(
                        "questionId", "Q_FIN_MGMT_001",
                        "text", "최근 누락된 정기납부와 두 차례 송금, 완료내역을 여러 번 확인한 이유를 함께 살펴봐도 될까요?",
                        "basisReasonCodes", REASON_CODES
                )),
                "protectionCandidates", protectionCandidates(),
                "consultationDraft", map(
                        "summary", "고객이 일부 거래를 확인하기 어려워 추가 사실확인이 필요합니다.",
                        "checklist", List.of(
                                "정기납부 처리상태 확인",
                                "중복송금 취소·환불 확인",
                                "거래 결과화면 지연 여부 확인"
                        ),
                        "generatedBy", "TEMPLATE",
                        "fallbackUsed", true
                ),
                "trustedContactGate", readRequiredMap(row.trustedContactGateJson()),
                "guidancePlan", guidance,
                "capabilities", map(
                        "externalMessage", false,
                        "transactionHold", false,
                        "limitChange", false,
                        "accountBlock", false
                ),
                "allowedActions", allowedActions(row.incidentState())
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> caseEvidence(UUID sessionId, UUID demoRunId, String caseId) {
        requireCurrentRun(sessionId, demoRunId);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, false);
        List<SignalRow> signalRows = signals(sessionId, demoRunId, row.alertId());
        List<Map<String, Object>> signalItems = signalRows.stream().map(signal -> map(
                "signalId", signalId(signal.reasonCode()),
                "reasonCode", signal.reasonCode(),
                "observedCount", signal.observedCount(),
                "windowSeconds", signal.windowSeconds(),
                "algorithmVersion", signal.algorithmVersion(),
                "detectedAt", signal.detectedAt(),
                "snapshotHash", signal.snapshotHash(),
                "evidenceIds", signalEvidenceIds(signal.reasonCode())
        )).toList();
        return map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "alertId", row.alertId(),
                "immutableT0", true,
                "signals", signalItems,
                "transactions", evidenceTransactions(sessionId, demoRunId),
                "contextEvidenceIds", contextEvidenceIds(sessionId, demoRunId, row.alertId()),
                "officialSources", protectionCandidates(),
                "provenance", map(
                        "syntheticData", true,
                        "sourceProvider", "SYNTHETIC_PROVIDER",
                        "externalFetchPerformed", false
                )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> caseTimeline(UUID sessionId, UUID demoRunId, String caseId) {
        requireCurrentRun(sessionId, demoRunId);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, false);
        Map<String, Object> audit = alertAudit(sessionId, demoRunId, row.alertId(), null, 100);
        return map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "alertId", row.alertId(),
                "currentState", row.incidentState(),
                "caseVersion", row.caseVersion(),
                "phases", timeline(sessionId, demoRunId, row),
                "auditTrail", audit.get("items"),
                "hasMore", audit.get("hasMore"),
                "externalActionCreated", false
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> caseNotes(UUID sessionId, UUID demoRunId, String caseId) {
        requireCurrentRun(sessionId, demoRunId);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, false);
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select note_id, case_version, note_text, created_by, created_at
                  from case_note
                 where demo_session_id = ? and demo_run_id = ? and case_id = ?
                 order by created_at asc, note_id asc
                """,
                (rs, rowNum) -> map(
                        "noteId", rs.getObject("note_id", UUID.class),
                        "caseVersion", rs.getLong("case_version"),
                        "noteText", rs.getString("note_text"),
                        "createdBy", rs.getString("created_by"),
                        "createdAt", rs.getObject("created_at", OffsetDateTime.class),
                        "isVisibleToCustomer", false
                ),
                sessionId, demoRunId, caseId
        );
        return map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "alertId", row.alertId(),
                "items", items,
                "count", items.size(),
                "externalDeliveryCreated", false
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> caseFollowUps(UUID sessionId, UUID demoRunId, String caseId) {
        requireCurrentRun(sessionId, demoRunId);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, false);
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select follow_up_id, status, reason, scheduled_at, result_note,
                       completed_at, created_at, updated_at, created_by
                  from follow_up_task
                 where demo_session_id = ? and demo_run_id = ? and case_id = ?
                 order by created_at asc, follow_up_id asc
                """,
                (rs, rowNum) -> map(
                        "followUpId", rs.getObject("follow_up_id", UUID.class),
                        "status", rs.getString("status"),
                        "reason", rs.getString("reason"),
                        "scheduledAt", rs.getObject("scheduled_at", OffsetDateTime.class),
                        "resultNote", rs.getString("result_note"),
                        "completedAt", rs.getObject("completed_at", OffsetDateTime.class),
                        "createdAt", rs.getObject("created_at", OffsetDateTime.class),
                        "updatedAt", rs.getObject("updated_at", OffsetDateTime.class),
                        "createdBy", rs.getString("created_by"),
                        "externalDeliveryCreated", false
                ),
                sessionId, demoRunId, caseId
        );
        OffsetDateTime nextFollowUpAt = items.stream()
                .filter(item -> "SCHEDULED".equals(item.get("status")))
                .map(item -> (OffsetDateTime) item.get("scheduledAt"))
                .min(OffsetDateTime::compareTo)
                .orElse(null);
        return map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "alertId", row.alertId(),
                "items", items,
                "count", items.size(),
                "nextFollowUpAt", nextFollowUpAt
        );
    }

    @Transactional
    public P0WorkflowResult reviewCase(
            UUID sessionId,
            UUID demoRunId,
            String caseId,
            String capabilityHash,
            String idempotencyKey,
            CaseReviewCommand request
    ) {
        requireCurrentRun(sessionId, demoRunId);
        validateCommandHeaders(capabilityHash, idempotencyKey);
        validateReviewRequest(request);
        String path = "/api/v1/demo/sessions/" + sessionId + "/cases/" + caseId + "/review";
        String requestHash = requestHash("POST", path, map(
                "action", request.action(),
                "caseVersion", request.caseVersion(),
                "followUpAt", request.followUpAt(),
                "note", request.note()
        ));
        CommandScope scope = commandScope(sessionId, demoRunId, capabilityHash, STAFF_ROLE, path, idempotencyKey);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, true);
        P0WorkflowResult replay = findReplay(scope, requestHash);
        if (replay != null) {
            return replay;
        }
        if (row.caseVersion() != request.caseVersion()) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }

        String nextState = reviewNextState(row.incidentState(), request.action());
        String taskStatus = switch (nextState) {
            case "IN_BANK_REVIEW" -> "IN_REVIEW";
            case "FOLLOW_UP_REQUIRED" -> "FOLLOW_UP";
            case "CLOSED_FALSE_POSITIVE" -> "COMPLETED";
            default -> throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        };
        OffsetDateTime now = OffsetDateTime.now(clock);
        if ("REQUIRE_FOLLOW_UP".equals(request.action())) {
            validateFollowUpAt(request.followUpAt(), now);
        }
        long nextVersion = row.caseVersion() + 1;
        updateIncidentState(sessionId, demoRunId, row.alertId(), row.incidentState(), nextState, now);
        int updated = jdbcTemplate.update(
                """
                update protection_case
                   set review_task_status = ?, case_version = ?, assigned_to = 'DEMO_STAFF',
                       latest_note = ?, follow_up_at = ?, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and case_id = ? and case_version = ?
                """,
                taskStatus, nextVersion, request.note(), request.followUpAt(), now,
                sessionId, demoRunId, caseId, request.caseVersion()
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }

        UUID followUpId = null;
        if ("REQUIRE_FOLLOW_UP".equals(request.action())) {
            followUpId = insertFollowUpTask(
                    sessionId, demoRunId, caseId, request.followUpAt(), request.note(), now
            );
        } else if ("RESUME_REVIEW".equals(request.action())) {
            followUpId = finishScheduledFollowUp(
                    sessionId, demoRunId, caseId, "COMPLETED", request.note(), now, true
            );
        } else if ("CLOSE_FALSE_POSITIVE".equals(request.action())
                && "FOLLOW_UP_REQUIRED".equals(row.incidentState())) {
            followUpId = finishScheduledFollowUp(
                    sessionId, demoRunId, caseId, "CANCELLED", request.note(), now, false
            );
        }

        String eventType = switch (request.action()) {
            case "START_REVIEW" -> "STAFF_REVIEW_STARTED";
            case "RESUME_REVIEW" -> "FOLLOW_UP_COMPLETED";
            case "REQUIRE_FOLLOW_UP" -> "FOLLOW_UP_SCHEDULED";
            case "CLOSE_FALSE_POSITIVE" -> "INCIDENT_CLOSED_FALSE_POSITIVE";
            default -> throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        };
        writeStaffAudit(sessionId, demoRunId, row.alertId(), caseId, eventType,
                row.incidentState(), nextState, requestHash, scope.idempotencyKeyHash(), now);

        Map<String, Object> data = map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "previousState", row.incidentState(),
                "currentState", nextState,
                "caseVersion", nextVersion,
                "reviewedBy", "DEMO_STAFF",
                "followUpAt", request.followUpAt(),
                "followUpId", followUpId,
                "externalExecutionCreated", false,
                "updatedAt", now,
                "command", map("requestHash", requestHash, "idempotencyReplayed", false)
        );
        P0WorkflowResult result = new P0WorkflowResult(
                "CASE_REVIEW_UPDATED", "행원 검토 상태를 변경했습니다.", data
        );
        saveCommand(scope, requestHash, result, now);
        return result;
    }

    @Transactional
    public P0WorkflowResult addCaseNote(
            UUID sessionId,
            UUID demoRunId,
            String caseId,
            String capabilityHash,
            String idempotencyKey,
            CaseNoteCommand request
    ) {
        requireCurrentRun(sessionId, demoRunId);
        validateCommandHeaders(capabilityHash, idempotencyKey);
        validateSafeNote(request.note());
        String path = "/api/v1/demo/sessions/" + sessionId + "/cases/" + caseId + "/notes";
        String requestHash = requestHash("POST", path, map(
                "caseVersion", request.caseVersion(), "note", request.note()
        ));
        CommandScope scope = commandScope(sessionId, demoRunId, capabilityHash, STAFF_ROLE, path, idempotencyKey);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, true);
        P0WorkflowResult replay = findReplay(scope, requestHash);
        if (replay != null) {
            return replay;
        }
        if (row.caseVersion() != request.caseVersion()) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        long nextVersion = row.caseVersion() + 1;
        UUID noteId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into case_note (
                    note_id, demo_session_id, demo_run_id, case_id, case_version,
                    note_text, created_by, request_hash, idempotency_key_hash, created_at
                ) values (?, ?, ?, ?, ?, ?, 'DEMO_STAFF', ?, ?, ?)
                """,
                noteId, sessionId, demoRunId, caseId, nextVersion, request.note(),
                requestHash, scope.idempotencyKeyHash(), now
        );
        int updated = jdbcTemplate.update(
                """
                update protection_case
                   set latest_note = ?, case_version = ?, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and case_id = ? and case_version = ?
                """,
                request.note(), nextVersion, now, sessionId, demoRunId, caseId, request.caseVersion()
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }
        writeStaffAudit(sessionId, demoRunId, row.alertId(), caseId, "CASE_NOTE_ADDED",
                row.incidentState(), row.incidentState(), requestHash, scope.idempotencyKeyHash(), now);

        Map<String, Object> data = map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "noteId", noteId,
                "caseVersion", nextVersion,
                "createdBy", "DEMO_STAFF",
                "createdAt", now,
                "customerVisible", false,
                "externalDeliveryCreated", false,
                "command", map("requestHash", requestHash, "idempotencyReplayed", false)
        );
        P0WorkflowResult result = new P0WorkflowResult(
                "CASE_NOTE_ADDED", "행원 내부 메모를 등록했습니다.", data
        );
        saveCommand(scope, requestHash, result, now);
        return result;
    }

    @Transactional
    public P0WorkflowResult scheduleFollowUp(
            UUID sessionId,
            UUID demoRunId,
            String caseId,
            String capabilityHash,
            String idempotencyKey,
            FollowUpCommand request
    ) {
        requireCurrentRun(sessionId, demoRunId);
        validateCommandHeaders(capabilityHash, idempotencyKey);
        validateSafeNote(request.reason());
        OffsetDateTime now = OffsetDateTime.now(clock);
        validateFollowUpAt(request.scheduledAt(), now);
        String path = "/api/v1/demo/sessions/" + sessionId + "/cases/" + caseId + "/follow-ups";
        String requestHash = requestHash("POST", path, map(
                "caseVersion", request.caseVersion(),
                "scheduledAt", request.scheduledAt(),
                "reason", request.reason()
        ));
        CommandScope scope = commandScope(sessionId, demoRunId, capabilityHash, STAFF_ROLE, path, idempotencyKey);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, true);
        P0WorkflowResult replay = findReplay(scope, requestHash);
        if (replay != null) {
            return replay;
        }
        if (row.caseVersion() != request.caseVersion()) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }
        if (!"IN_BANK_REVIEW".equals(row.incidentState())) {
            throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        }

        UUID followUpId = UUID.randomUUID();
        long nextVersion = row.caseVersion() + 1;
        updateIncidentState(sessionId, demoRunId, row.alertId(), row.incidentState(),
                "FOLLOW_UP_REQUIRED", now);
        int updated = jdbcTemplate.update(
                """
                update protection_case
                   set review_task_status = 'FOLLOW_UP', follow_up_at = ?, latest_note = ?,
                       case_version = ?, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and case_id = ? and case_version = ?
                """,
                request.scheduledAt(), request.reason(), nextVersion, now,
                sessionId, demoRunId, caseId, request.caseVersion()
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }
        insertFollowUpTask(followUpId, sessionId, demoRunId, caseId,
                request.scheduledAt(), request.reason(), now);
        writeStaffAudit(sessionId, demoRunId, row.alertId(), caseId, "FOLLOW_UP_SCHEDULED",
                row.incidentState(), "FOLLOW_UP_REQUIRED", requestHash, scope.idempotencyKeyHash(), now);

        Map<String, Object> data = map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "followUpId", followUpId,
                "status", "SCHEDULED",
                "scheduledAt", request.scheduledAt(),
                "caseVersion", nextVersion,
                "currentState", "FOLLOW_UP_REQUIRED",
                "deliveryAttempted", false,
                "externalDeliveryCreated", false,
                "createdAt", now,
                "command", map("requestHash", requestHash, "idempotencyReplayed", false)
        );
        P0WorkflowResult result = new P0WorkflowResult(
                "FOLLOW_UP_SCHEDULED", "내부 재확인 일정을 등록했습니다.", data
        );
        saveCommand(scope, requestHash, result, now);
        return result;
    }

    @Transactional
    public P0WorkflowResult updateFollowUp(
            UUID sessionId,
            UUID demoRunId,
            UUID followUpId,
            String capabilityHash,
            String idempotencyKey,
            FollowUpUpdateCommand request
    ) {
        requireCurrentRun(sessionId, demoRunId);
        validateCommandHeaders(capabilityHash, idempotencyKey);
        validateFollowUpUpdateRequest(request);
        String path = "/api/v1/demo/sessions/" + sessionId + "/staff/follow-ups/" + followUpId;
        String requestHash = requestHash("PATCH", path, map(
                "caseVersion", request.caseVersion(),
                "status", request.status(),
                "resultNote", request.resultNote(),
                "completedAt", request.completedAt()
        ));
        CommandScope scope = commandScope(
                sessionId, demoRunId, capabilityHash, STAFF_ROLE, "PATCH", path, idempotencyKey
        );
        FollowUpRow lookup = requireFollowUp(sessionId, demoRunId, followUpId, false);
        CaseRow caseRow = requireCase(sessionId, demoRunId, lookup.caseId(), true);
        FollowUpRow row = requireFollowUp(sessionId, demoRunId, followUpId, true);
        P0WorkflowResult replay = findReplay(scope, requestHash);
        if (replay != null) {
            return replay;
        }

        if (caseRow.caseVersion() != request.caseVersion()) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }
        if (!"FOLLOW_UP_REQUIRED".equals(caseRow.incidentState())) {
            throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        }
        if (!"SCHEDULED".equals(row.status())) {
            throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        }
        if (!FOLLOW_UP_STATUSES.contains(request.status())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "지원하지 않는 후속 일정 상태입니다.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime completedAt = "COMPLETED".equals(request.status()) ? now : null;
        long nextVersion = caseRow.caseVersion() + 1;
        int updated = jdbcTemplate.update(
                """
                update follow_up_task
                   set status = ?, result_note = ?, completed_at = ?, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and follow_up_id = ?
                   and status = 'SCHEDULED'
                """,
                request.status(), request.resultNote(), completedAt, now,
                sessionId, demoRunId, followUpId
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.FOLLOW_UP_NOT_FOUND);
        }

        updateIncidentState(sessionId, demoRunId, row.alertId(), caseRow.incidentState(),
                "IN_BANK_REVIEW", now);
        int caseUpdated = jdbcTemplate.update(
                """
                update protection_case
                   set review_task_status = 'IN_REVIEW', case_version = ?, latest_note = ?,
                       follow_up_at = null, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and case_id = ? and case_version = ?
                """,
                nextVersion, request.resultNote(), now,
                sessionId, demoRunId, row.caseId(), request.caseVersion()
        );
        if (caseUpdated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }

        String eventType = "COMPLETED".equals(request.status())
                ? "FOLLOW_UP_COMPLETED" : "FOLLOW_UP_CANCELLED";
        writeStaffAudit(sessionId, demoRunId, row.alertId(), row.caseId(), eventType,
                caseRow.incidentState(), "IN_BANK_REVIEW", requestHash,
                scope.idempotencyKeyHash(), now);

        Map<String, Object> data = map(
                "demoRunId", demoRunId,
                "followUpId", followUpId,
                "caseId", row.caseId(),
                "status", request.status(),
                "previousStatus", row.status(),
                "caseVersion", nextVersion,
                "previousState", caseRow.incidentState(),
                "currentState", "IN_BANK_REVIEW",
                "resultNote", request.resultNote(),
                "completedAt", completedAt,
                "externalDeliveryCreated", false,
                "updatedAt", now,
                "command", map("requestHash", requestHash, "idempotencyReplayed", false)
        );

        P0WorkflowResult result = new P0WorkflowResult(
                "FOLLOW_UP_UPDATED", "후속 일정 상태를 갱신했습니다.", data
        );
        saveCommand(scope, requestHash, result, now);
        return result;
    }

    @Transactional
    public P0WorkflowResult approveGuidancePlan(
            UUID sessionId,
            UUID demoRunId,
            String caseId,
            String capabilityHash,
            String idempotencyKey,
            GuidancePlanCommand request
    ) {
        requireCurrentRun(sessionId, demoRunId);
        validateCommandHeaders(capabilityHash, idempotencyKey);
        validateGuidanceRequest(request);
        String path = "/api/v1/demo/sessions/" + sessionId + "/cases/" + caseId + "/guidance-plan";
        String requestHash = requestHash("POST", path, map(
                "decision", request.decision(),
                "caseVersion", request.caseVersion(),
                "selectedActionCodes", request.selectedActionCodes(),
                "staffNote", request.staffNote()
        ));
        CommandScope scope = commandScope(sessionId, demoRunId, capabilityHash, STAFF_ROLE, path, idempotencyKey);
        CaseRow row = requireCase(sessionId, demoRunId, caseId, true);
        P0WorkflowResult replay = findReplay(scope, requestHash);
        if (replay != null) {
            return replay;
        }
        if (row.caseVersion() != request.caseVersion()) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }
        if (!"IN_BANK_REVIEW".equals(row.incidentState())) {
            throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        }
        requireKnownActionCodes(request.selectedActionCodes());

        OffsetDateTime now = OffsetDateTime.now(clock);
        long nextVersion = row.caseVersion() + 1;
        updateIncidentState(sessionId, demoRunId, row.alertId(), row.incidentState(),
                "GUIDANCE_PLAN_APPROVED", now);
        int updated = jdbcTemplate.update(
                """
                update protection_case
                   set review_task_status = 'GUIDANCE_APPROVED', case_version = ?,
                       assigned_to = 'DEMO_STAFF', latest_note = ?, follow_up_at = null, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and case_id = ? and case_version = ?
                """,
                nextVersion, request.staffNote(), now,
                sessionId, demoRunId, caseId, request.caseVersion()
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_VERSION_CONFLICT);
        }
        jdbcTemplate.update(
                """
                insert into guidance_plan (
                    demo_session_id, demo_run_id, case_id, plan_version, status,
                    selected_action_codes, approved_by, approved_at,
                    delivered, delivered_at, external_execution_created
                ) values (?, ?, ?, 1, 'APPROVED', cast(? as jsonb), 'DEMO_STAFF', ?, false, null, false)
                """,
                sessionId, demoRunId, caseId, toJson(request.selectedActionCodes()), now
        );
        writeStaffAudit(sessionId, demoRunId, row.alertId(), caseId, "GUIDANCE_PLAN_APPROVED",
                row.incidentState(), "GUIDANCE_PLAN_APPROVED", requestHash,
                scope.idempotencyKeyHash(), now);

        Map<String, Object> data = map(
                "demoRunId", demoRunId,
                "caseId", caseId,
                "previousState", row.incidentState(),
                "currentState", "GUIDANCE_PLAN_APPROVED",
                "caseVersion", nextVersion,
                "guidancePlanStatus", "APPROVED",
                "approvedActionCodes", request.selectedActionCodes(),
                "externalExecutionCreated", false,
                "guidanceDelivered", false,
                "approvedAt", now,
                "deliveredAt", null,
                "command", map("requestHash", requestHash, "idempotencyReplayed", false)
        );
        P0WorkflowResult result = new P0WorkflowResult(
                "GUIDANCE_PLAN_APPROVED",
                "상담 안내 계획을 승인했습니다. 실제 계좌 조치는 실행되지 않았습니다.",
                data
        );
        saveCommand(scope, requestHash, result, now);
        return result;
    }

    private DemoSession requireCurrentRun(UUID sessionId, UUID demoRunId) {
        if (demoRunId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "X-Demo-Run-Id가 필요합니다.");
        }
        DemoSession session = sessionService.requireFinancialFixture(sessionId);
        if (!session.getDemoRunId().equals(demoRunId)) {
            throw new BusinessException(P0WorkflowErrorCode.DEMO_RUN_STALE);
        }
        return session;
    }

    private IncidentRow requireIncident(UUID sessionId, UUID demoRunId, String alertId, boolean lock) {
        if (!DemoSessionService.ALERT_ID.equals(alertId)) {
            throw new BusinessException(P0WorkflowErrorCode.ALERT_NOT_FOUND);
        }
        String sql = """
                select alert_id, customer_id, state, incident_version, pre_decision, post_decision,
                       t1_context_evidence::text, trusted_contact_gate::text,
                       alert_snapshot_at, context_observed_at
                  from alert_incident
                 where demo_session_id = ? and demo_run_id = ? and alert_id = ?
                """ + (lock ? " for update" : "");
        List<IncidentRow> rows = jdbcTemplate.query(sql, this::incidentRow, sessionId, demoRunId, alertId);
        if (rows.size() != 1) {
            throw new BusinessException(P0WorkflowErrorCode.ALERT_NOT_FOUND);
        }
        return rows.getFirst();
    }

    private CaseRow requireCase(UUID sessionId, UUID demoRunId, String caseId, boolean lock) {
        String sql = """
                select c.case_id, c.alert_id, c.customer_id, c.review_priority,
                       c.review_task_status, c.case_version, c.customer_response_code,
                       c.created_at, c.updated_at, c.follow_up_at,
                       i.state, i.incident_version, i.pre_decision, i.post_decision,
                       i.trusted_contact_gate::text, i.alert_snapshot_at, i.context_observed_at
                  from protection_case c
                  join alert_incident i
                    on i.demo_session_id = c.demo_session_id
                   and i.demo_run_id = c.demo_run_id and i.alert_id = c.alert_id
                 where c.demo_session_id = ? and c.demo_run_id = ? and c.case_id = ?
                """ + (lock ? " for update of c, i" : "");
        List<CaseRow> rows = jdbcTemplate.query(sql, this::caseRow, sessionId, demoRunId, caseId);
        if (rows.size() != 1) {
            throw new BusinessException(P0WorkflowErrorCode.CASE_NOT_FOUND);
        }
        return rows.getFirst();
    }

    private FollowUpRow requireFollowUp(UUID sessionId, UUID demoRunId, UUID followUpId, boolean lock) {
        String sql = """
                select f.follow_up_id, f.case_id, f.status, f.scheduled_at, f.reason,
                       f.result_note, f.completed_at, f.created_at, f.updated_at,
                       c.alert_id
                  from follow_up_task f
                  join protection_case c
                    on c.demo_session_id = f.demo_session_id
                   and c.demo_run_id = f.demo_run_id
                   and c.case_id = f.case_id
                 where f.demo_session_id = ? and f.demo_run_id = ? and f.follow_up_id = ?
                """ + (lock ? " for update" : "");
        List<FollowUpRow> rows = jdbcTemplate.query(sql, this::followUpRow, sessionId, demoRunId, followUpId);
        if (rows.size() != 1) {
            throw new BusinessException(P0WorkflowErrorCode.FOLLOW_UP_NOT_FOUND);
        }
        return rows.getFirst();
    }

    private UUID insertFollowUpTask(
            UUID sessionId,
            UUID demoRunId,
            String caseId,
            OffsetDateTime scheduledAt,
            String reason,
            OffsetDateTime now
    ) {
        UUID followUpId = UUID.randomUUID();
        insertFollowUpTask(followUpId, sessionId, demoRunId, caseId, scheduledAt, reason, now);
        return followUpId;
    }

    private void insertFollowUpTask(
            UUID followUpId,
            UUID sessionId,
            UUID demoRunId,
            String caseId,
            OffsetDateTime scheduledAt,
            String reason,
            OffsetDateTime now
    ) {
        jdbcTemplate.update(
                """
                insert into follow_up_task (
                    follow_up_id, demo_session_id, demo_run_id, case_id, status,
                    scheduled_at, reason, created_by, external_delivery_created, created_at, updated_at
                ) values (?, ?, ?, ?, 'SCHEDULED', ?, ?, 'DEMO_STAFF', false, ?, ?)
                """,
                followUpId, sessionId, demoRunId, caseId, scheduledAt, reason, now, now
        );
    }

    private UUID finishScheduledFollowUp(
            UUID sessionId,
            UUID demoRunId,
            String caseId,
            String status,
            String resultNote,
            OffsetDateTime now,
            boolean required
    ) {
        List<UUID> scheduled = jdbcTemplate.queryForList(
                """
                select follow_up_id
                  from follow_up_task
                 where demo_session_id = ? and demo_run_id = ? and case_id = ?
                   and status = 'SCHEDULED'
                 order by scheduled_at, follow_up_id
                 for update
                """,
                UUID.class,
                sessionId, demoRunId, caseId
        );
        if (scheduled.isEmpty()) {
            if (required) {
                throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
            }
            return null;
        }
        if (scheduled.size() != 1) {
            throw new IllegalStateException("사건에는 예약 상태의 후속조치가 하나만 존재해야 합니다.");
        }
        UUID followUpId = scheduled.getFirst();
        OffsetDateTime completedAt = "COMPLETED".equals(status) ? now : null;
        int updated = jdbcTemplate.update(
                """
                update follow_up_task
                   set status = ?, result_note = ?, completed_at = ?, updated_at = ?
                 where follow_up_id = ? and demo_session_id = ? and demo_run_id = ?
                   and status = 'SCHEDULED'
                """,
                status, resultNote, completedAt, now, followUpId, sessionId, demoRunId
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        }
        return followUpId;
    }

    private List<SignalRow> signals(UUID sessionId, UUID demoRunId, String alertId) {
        List<SignalRow> rows = jdbcTemplate.query(
                """
                select reason_code, observed_count, window_seconds, algorithm_version,
                       detected_at, snapshot_hash
                  from synthetic_signal
                 where demo_session_id = ? and demo_run_id = ? and alert_id = ?
                 order by case reason_code
                    when 'MISSED_RECURRING' then 1
                    when 'DUPLICATE_TRANSFER' then 2
                    when 'REPEATED_CONFIRMATION' then 3 else 9 end
                """,
                (rs, rowNum) -> new SignalRow(
                        rs.getString("reason_code"), rs.getInt("observed_count"),
                        rs.getInt("window_seconds"), rs.getString("algorithm_version"),
                        rs.getObject("detected_at", OffsetDateTime.class), rs.getString("snapshot_hash")
                ),
                sessionId, demoRunId, alertId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(P0WorkflowErrorCode.ALERT_NOT_FOUND);
        }
        return rows;
    }

    private Map<String, Object> t0Evidence(
            UUID sessionId,
            UUID demoRunId,
            IncidentRow incident,
            List<SignalRow> signals
    ) {
        List<Map<String, Object>> signalItems = signals.stream().map(signal -> map(
                "signalId", signalId(signal.reasonCode()),
                "reasonCode", signal.reasonCode(),
                "readiness", "READY",
                "baselineValue", baselineValue(signal.reasonCode()),
                "currentValue", signal.observedCount(),
                "unit", signalUnit(signal.reasonCode()),
                "evidenceIds", signalEvidenceIds(signal.reasonCode())
        )).toList();
        return map(
                "phase", "T0_ALERT",
                "snapshotHash", signals.getFirst().snapshotHash(),
                "alertSnapshotAt", incident.alertSnapshotAt(),
                "alertEvidenceIds", signals.stream().map(row -> signalId(row.reasonCode())).toList(),
                "immutable", true,
                "signals", signalItems,
                "evidenceTransactions", evidenceTransactions(sessionId, demoRunId)
        );
    }

    private List<Map<String, Object>> evidenceTransactions(UUID sessionId, UUID demoRunId) {
        return jdbcTemplate.query(
                """
                select t.transaction_id, a.institution_id, a.account_type, t.transaction_type,
                       t.occurred_at, t.posted_at, t.amount, t.currency,
                       t.counterparty_display_name, t.transaction_status
                  from synthetic_transaction t
                  join synthetic_account a
                    on a.demo_session_id = t.demo_session_id and a.demo_run_id = t.demo_run_id
                   and a.account_id = t.account_id
                 where t.demo_session_id = ? and t.demo_run_id = ?
                   and t.transaction_id in ('TX_DUP_A_001', 'TX_DUP_A_002', 'TX_DUP_B_001', 'TX_DUP_B_002')
                 order by t.occurred_at, t.transaction_id
                """,
                (rs, rowNum) -> map(
                        "transactionId", rs.getString("transaction_id"),
                        "institutionCode", rs.getString("institution_id"),
                        "accountType", rs.getString("account_type"),
                        "transactionType", rs.getString("transaction_type"),
                        "occurredAt", rs.getObject("occurred_at", OffsetDateTime.class),
                        "postedAt", rs.getObject("posted_at", OffsetDateTime.class),
                        "amount", decimal(rs.getBigDecimal("amount")),
                        "currency", rs.getString("currency"),
                        "counterpartyDisplayName", rs.getString("counterparty_display_name"),
                        "channel", "MOBILE_BANKING",
                        "status", rs.getString("transaction_status")
                ),
                sessionId, demoRunId
        );
    }

    private List<Map<String, Object>> verifiedContextEvidence(OffsetDateTime ingestedAt) {
        return List.of(
                contextEvidence("PAYMENT_DELAY_SYN_001", "PAYMENT_PROVIDER_DELAY_VERIFIED",
                        "2026-06-01T00:00:00Z", "2026-07-31T23:59:59Z",
                        "PAYMENT_PROVIDER_EVENT", ingestedAt),
                contextEvidence("CONNECTION_OUTAGE_SYN_001", "ACCOUNT_CONNECTION_OUTAGE_VERIFIED",
                        "2026-06-01T00:00:00Z", "2026-07-31T23:59:59Z",
                        "SYSTEM_EVENT", ingestedAt),
                contextEvidence("TRANSFER_REFUND_SYN_001", "DUPLICATE_TRANSFER_REFUNDED",
                        "2026-07-10T10:08:00Z", "2026-07-10T10:08:03Z",
                        "SYSTEM_EVENT", ingestedAt),
                contextEvidence("RESULT_DISPLAY_DELAY_SYN_001", "RESULT_SCREEN_DELAY_VERIFIED",
                        "2026-07-15T10:00:00Z", "2026-07-15T11:00:00Z",
                        "SYSTEM_EVENT", ingestedAt)
        );
    }

    private Map<String, Object> contextEvidence(
            String id,
            String type,
            String effectiveAt,
            String observedAt,
            String sourceType,
            OffsetDateTime ingestedAt
    ) {
        return map(
                "contextEvidenceId", id,
                "contextType", type,
                "effectiveAt", OffsetDateTime.parse(effectiveAt),
                "observedAt", OffsetDateTime.parse(observedAt),
                "ingestedAt", ingestedAt,
                "sourceType", sourceType,
                "version", "1",
                "integrityHash", sha256(id + "|" + type + "|" + effectiveAt + "|" + observedAt + "|1")
        );
    }

    private boolean completeEvidencePackage(List<Map<String, Object>> refs) {
        if (refs.size() != 4) {
            return false;
        }
        Set<String> types = new LinkedHashSet<>();
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> ref : refs) {
            types.add(String.valueOf(ref.get("contextType")));
            ids.add(String.valueOf(ref.get("contextEvidenceId")));
            if (ref.get("effectiveAt") == null || ref.get("observedAt") == null
                    || ref.get("ingestedAt") == null || ref.get("sourceType") == null
                    || ref.get("version") == null || ref.get("integrityHash") == null) {
                return false;
            }
        }
        return types.equals(new LinkedHashSet<>(CONTEXT_TYPES))
                && ids.equals(new LinkedHashSet<>(CONTEXT_EVIDENCE_IDS));
    }

    private boolean strongSignalInvariantHolds(List<SignalRow> signals) {
        if (signals.size() != 3) {
            return false;
        }
        return signals.stream().allMatch(signal ->
                EXPECTED_SIGNAL_COUNTS.getOrDefault(signal.reasonCode(), -1) == signal.observedCount()
                        && EXPECTED_WINDOWS.getOrDefault(signal.reasonCode(), -1) == signal.windowSeconds()
        );
    }

    private void writeContextAudits(
            UUID sessionId,
            UUID demoRunId,
            String alertId,
            String fromState,
            String toState,
            boolean closeNormal,
            String requestHash,
            String idempotencyKeyHash,
            List<String> evidenceIds,
            OffsetDateTime occurredAt
    ) {
        Map<String, Object> base = map(
                "alertId", alertId,
                "actorType", "POLICY_ENGINE",
                "fromState", fromState,
                "toState", toState,
                "resultCode", closeNormal ? "STRUCTURAL_CONTEXT_VERIFIED" : "BANK_REVIEW_REQUIRED",
                "evidenceIds", evidenceIds,
                "requestHash", requestHash,
                "idempotencyKeyHash", idempotencyKeyHash
        );
        auditWriter.write(sessionId, demoRunId, "CONTEXT_EVALUATED", base, occurredAt);
        if (closeNormal) {
            auditWriter.write(sessionId, demoRunId, "INCIDENT_CLOSED_NORMAL", base, occurredAt);
            return;
        }
        auditWriter.write(sessionId, demoRunId, "BANK_REVIEW_QUEUED", base, occurredAt);
        Map<String, Object> blocked = new LinkedHashMap<>(base);
        blocked.put("eventType", "CONSENT_ACTION_BLOCKED");
        blocked.put("actorType", "SYSTEM");
        blocked.put("resultCode", "BLOCKED_BY_CONSENT");
        blocked.put("evidenceIds", List.of("CONSENT_TRUSTED_CONTACT_001"));
        blocked.put("dispatchAttempted", false);
        blocked.put("externalDeliveryRequested", false);
        blocked.put("externalDeliveryCreated", false);
        auditWriter.write(sessionId, demoRunId, "CONSENT_ACTION_BLOCKED", blocked, occurredAt);
    }

    private void writeStaffAudit(
            UUID sessionId,
            UUID demoRunId,
            String alertId,
            String caseId,
            String eventType,
            String fromState,
            String toState,
            String requestHash,
            String idempotencyKeyHash,
            OffsetDateTime occurredAt
    ) {
        auditWriter.write(sessionId, demoRunId, eventType, map(
                "alertId", alertId,
                "caseId", caseId,
                "actorType", "DEMO_STAFF",
                "actorId", "DEMO_STAFF",
                "fromState", fromState,
                "toState", toState,
                "requestHash", requestHash,
                "idempotencyKeyHash", idempotencyKeyHash,
                "externalExecutionCreated", false
        ), occurredAt);
    }

    private List<Map<String, Object>> timeline(UUID sessionId, UUID demoRunId, CaseRow row) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(map(
                "phase", "T0_ALERT",
                "type", "ALERT_CREATED",
                "title", "변화 알림 생성",
                "occurredAt", row.alertSnapshotAt(),
                "evidenceIds", List.of(
                        "SIG_MISSED_RECURRING_001",
                        "SIG_DUPLICATE_TRANSFER_001",
                        "SIG_REPEATED_CONFIRMATION_001"
                )
        ));
        timeline.add(map(
                "phase", "T1_CONTEXT",
                "type", "CONTEXT_EVALUATED",
                "title", "검증된 정상 구조적 근거 없음",
                "occurredAt", row.contextObservedAt(),
                "evidenceIds", contextEvidenceIds(sessionId, demoRunId, row.alertId())
        ));
        return List.copyOf(timeline);
    }

    private List<Map<String, Object>> protectionCandidates() {
        return jdbcTemplate.query(
                """
                select action_code, title, eligibility_summary, issuer, source_url,
                       effective_from, checked_at, execution_type
                  from protection_action_catalog
                 where action_code in ('SAFE_BLOCK_INFO', 'BANK_CONSULTATION')
                 order by display_order
                """,
                (rs, rowNum) -> map(
                        "actionCode", rs.getString("action_code"),
                        "title", rs.getString("title"),
                        "eligibilitySummary", rs.getString("eligibility_summary"),
                        "source", map(
                                "issuer", rs.getString("issuer"),
                                "url", rs.getString("source_url"),
                                "effectiveFrom", rs.getObject("effective_from", LocalDate.class),
                                "checkedAt", rs.getObject("checked_at", LocalDate.class)
                        ),
                        "executionType", rs.getString("execution_type")
                )
        );
    }

    private Map<String, Object> guidanceSummary(UUID sessionId, UUID demoRunId, String caseId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select status, approved_at, delivered, delivered_at
                  from guidance_plan
                 where demo_session_id = ? and demo_run_id = ? and case_id = ?
                """,
                (rs, rowNum) -> map(
                        "status", rs.getString("status"),
                        "approvedAt", rs.getObject("approved_at", OffsetDateTime.class),
                        "delivered", rs.getBoolean("delivered"),
                        "deliveredAt", rs.getObject("delivered_at", OffsetDateTime.class)
                ), sessionId, demoRunId, caseId
        );
        return rows.isEmpty()
                ? map("status", "NOT_APPROVED", "approvedAt", null, "delivered", false, "deliveredAt", null)
                : rows.getFirst();
    }

    private List<Map<String, Object>> allowedActions(String state) {
        return switch (state) {
            case "PENDING_BANK_REVIEW" -> List.of(
                    allowedAction("START_REVIEW", true, null),
                    allowedAction("APPROVE_GUIDANCE_PLAN", false, "REVIEW_NOT_STARTED")
            );
            case "IN_BANK_REVIEW" -> List.of(
                    allowedAction("START_REVIEW", false, "REVIEW_ALREADY_STARTED"),
                    allowedAction("APPROVE_GUIDANCE_PLAN", true, null)
            );
            default -> List.of(
                    allowedAction("START_REVIEW", false, "INVALID_STATE"),
                    allowedAction("APPROVE_GUIDANCE_PLAN", false, "INVALID_STATE")
            );
        };
    }

    private Map<String, Object> allowedAction(String action, boolean enabled, String disabledReason) {
        return map("action", action, "enabled", enabled, "disabledReasonCode", disabledReason);
    }

    private void validateContextCombination(ContextCommand request) {
        boolean normal = "KNOWN_AND_INTENTIONAL".equals(request.responseCode())
                && "FIN_MGMT_A_NORMAL_CONTEXT".equals(request.demoBranchCode());
        boolean review = "UNABLE_TO_CONFIRM".equals(request.responseCode())
                && "FIN_MGMT_B_NO_CONTEXT".equals(request.demoBranchCode());
        if (!normal && !review) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "responseCode와 demoBranchCode 조합이 올바르지 않습니다.");
        }
    }

    private void validateFollowUpUpdateRequest(FollowUpUpdateCommand request) {
        if (!FOLLOW_UP_STATUSES.contains(request.status())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "지원하지 않는 후속 일정 상태입니다.");
        }
        if (request.completedAt() != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "completedAt은 서버가 기록하므로 요청에 포함할 수 없습니다.");
        }
        validateSafeNote(request.resultNote());
    }

    private void validateReviewRequest(CaseReviewCommand request) {
        if (!REVIEW_ACTIONS.contains(request.action())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "지원하지 않는 review action입니다.");
        }
        validateSafeNote(request.note());
        if ("REQUIRE_FOLLOW_UP".equals(request.action()) && request.followUpAt() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "REQUIRE_FOLLOW_UP에는 followUpAt이 필요합니다.");
        }
        if (Set.of("REQUIRE_FOLLOW_UP", "RESUME_REVIEW").contains(request.action())
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "후속조치 예약·완료에는 내부 기록이 필요합니다.");
        }
        if (!"REQUIRE_FOLLOW_UP".equals(request.action()) && request.followUpAt() != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "followUpAt은 REQUIRE_FOLLOW_UP에서만 사용할 수 있습니다.");
        }
        if ("CLOSE_FALSE_POSITIVE".equals(request.action())
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "오탐 종결에는 검토 근거가 필요합니다.");
        }
    }

    private void validateGuidanceRequest(GuidancePlanCommand request) {
        if (!"APPROVE_GUIDANCE_PLAN".equals(request.decision())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "지원하지 않는 안내계획 결정입니다.");
        }
        validateSafeNote(request.staffNote());
        if (new LinkedHashSet<>(request.selectedActionCodes()).size() != request.selectedActionCodes().size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "중복된 actionCode가 있습니다.");
        }
    }

    private void validateSafeNote(String note) {
        if (note == null) {
            return;
        }
        String normalized = Normalizer.normalize(note, Normalizer.Form.NFKC);
        boolean hiddenCharacters = normalized.codePoints().anyMatch(character ->
                Character.isISOControl(character)
                        || Character.getType(character) == Character.FORMAT
                        || Character.getType(character) == Character.PRIVATE_USE
        );
        String compact = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Z}\\p{P}\\p{S}]", "");
        long digitCount = normalized.codePoints().filter(Character::isDigit).count();
        boolean sensitivePattern = SENSITIVE_NOTE_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(normalized).find());
        if (hiddenCharacters
                || digitCount >= 6
                || sensitivePattern
                || COMPACT_SENSITIVE_NOTE_PATTERN.matcher(compact).find()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "메모에는 고객 식별정보·계좌번호·질병 추정 표현을 입력할 수 없습니다.");
        }
    }

    private void validateFollowUpAt(OffsetDateTime scheduledAt, OffsetDateTime now) {
        if (scheduledAt == null || !scheduledAt.isAfter(now) || scheduledAt.isAfter(now.plusDays(365))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "scheduledAt은 현재 이후 365일 이내여야 합니다.");
        }
    }

    private void validateCaseFilters(String state, String priority) {
        if (state != null && !state.isBlank() && !Set.of(
                "PENDING_BANK_REVIEW", "IN_BANK_REVIEW", "FOLLOW_UP_REQUIRED",
                "GUIDANCE_PLAN_APPROVED", "CLOSED_FALSE_POSITIVE"
        ).contains(state)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "state 필터가 올바르지 않습니다.");
        }
        if (priority != null && !priority.isBlank() && !Set.of("HIGH", "MEDIUM", "LOW").contains(priority)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "reviewPriority 필터가 올바르지 않습니다.");
        }
    }

    private void requireKnownActionCodes(List<String> actionCodes) {
        String placeholders = String.join(",", java.util.Collections.nCopies(actionCodes.size(), "?"));
        List<String> found = jdbcTemplate.queryForList(
                "select action_code from protection_action_catalog where action_code in (" + placeholders + ")",
                String.class,
                actionCodes.toArray()
        );
        if (found.size() != actionCodes.size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "지원하지 않는 actionCode가 있습니다.");
        }
    }

    private String reviewNextState(String currentState, String action) {
        if ("START_REVIEW".equals(action) && "PENDING_BANK_REVIEW".equals(currentState)) {
            return "IN_BANK_REVIEW";
        }
        if ("RESUME_REVIEW".equals(action) && "FOLLOW_UP_REQUIRED".equals(currentState)) {
            return "IN_BANK_REVIEW";
        }
        if ("REQUIRE_FOLLOW_UP".equals(action) && "IN_BANK_REVIEW".equals(currentState)) {
            return "FOLLOW_UP_REQUIRED";
        }
        if ("CLOSE_FALSE_POSITIVE".equals(action)
                && Set.of("IN_BANK_REVIEW", "FOLLOW_UP_REQUIRED").contains(currentState)) {
            return "CLOSED_FALSE_POSITIVE";
        }
        throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
    }

    private void updateIncidentState(
            UUID sessionId,
            UUID demoRunId,
            String alertId,
            String previousState,
            String nextState,
            OffsetDateTime now
    ) {
        int updated = jdbcTemplate.update(
                """
                update alert_incident
                   set state = ?, incident_version = incident_version + 1, updated_at = ?
                 where demo_session_id = ? and demo_run_id = ? and alert_id = ? and state = ?
                """,
                nextState, now, sessionId, demoRunId, alertId, previousState
        );
        if (updated != 1) {
            throw new BusinessException(P0WorkflowErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private void validateCommandHeaders(String capabilityHash, String idempotencyKey) {
        if (capabilityHash == null || capabilityHash.isBlank()) {
            throw new BusinessException(P0WorkflowErrorCode.ALERT_NOT_FOUND);
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "Idempotency-Key는 8~64자의 영문, 숫자, '.', '_', ':', '-'만 사용할 수 있습니다.");
        }
    }

    private CommandScope commandScope(
            UUID sessionId,
            UUID demoRunId,
            String capabilityHash,
            String role,
            String method,
            String path,
            String idempotencyKey
    ) {
        return new CommandScope(
                sessionId, demoRunId, capabilityHash, role, method, path, sha256(idempotencyKey)
        );
    }

    private CommandScope commandScope(
            UUID sessionId,
            UUID demoRunId,
            String capabilityHash,
            String role,
            String path,
            String idempotencyKey
    ) {
        return commandScope(sessionId, demoRunId, capabilityHash, role, "POST", path, idempotencyKey);
    }

    private P0WorkflowResult findReplay(CommandScope scope, String requestHash) {
        List<StoredCommand> rows = jdbcTemplate.query(
                """
                select request_hash, response_code, response_message, response_payload::text
                  from workflow_command_result
                 where demo_session_id = ? and demo_run_id = ? and capability_hash = ?
                   and capability_role = ? and http_method = ? and operation_path = ?
                   and idempotency_key_hash = ?
                """,
                (rs, rowNum) -> new StoredCommand(
                        rs.getString("request_hash"), rs.getString("response_code"),
                        rs.getString("response_message"), rs.getString("response_payload")
                ),
                scope.sessionId(), scope.demoRunId(), scope.capabilityHash(), scope.capabilityRole(),
                scope.httpMethod(), scope.operationPath(), scope.idempotencyKeyHash()
        );
        if (rows.isEmpty()) {
            return null;
        }
        StoredCommand stored = rows.getFirst();
        if (!MessageDigest.isEqual(
                stored.requestHash().getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BusinessException(P0WorkflowErrorCode.IDEMPOTENCY_CONFLICT);
        }
        Map<String, Object> data = readRequiredMap(stored.responsePayload());
        Object commandValue = data.get("command");
        if (commandValue instanceof Map<?, ?> command) {
            Map<String, Object> replayedCommand = new LinkedHashMap<>();
            command.forEach((key, value) -> replayedCommand.put(String.valueOf(key), value));
            replayedCommand.put("idempotencyReplayed", true);
            data.put("command", replayedCommand);
        }
        return new P0WorkflowResult(stored.responseCode(), stored.responseMessage(), data);
    }

    private void saveCommand(
            CommandScope scope,
            String requestHash,
            P0WorkflowResult result,
            OffsetDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                insert into workflow_command_result (
                    command_record_id, demo_session_id, demo_run_id, capability_hash,
                    capability_role, http_method, operation_path, idempotency_key_hash,
                    request_hash, response_code, response_message, response_payload, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """,
                UUID.randomUUID(), scope.sessionId(), scope.demoRunId(), scope.capabilityHash(),
                scope.capabilityRole(), scope.httpMethod(), scope.operationPath(), scope.idempotencyKeyHash(),
                requestHash, result.code(), result.message(), toJson(result.data()), createdAt
        );
    }

    private String requestHash(String method, String path, Map<String, Object> body) {
        return sha256(method + "\n" + path + "\n\napplication/json\n" + toJson(body));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "limit은 1~100이어야 합니다.");
        }
    }

    private Map<String, Object> trustedContactGate(boolean evaluated) {
        return map(
                "gateEvaluated", evaluated,
                "consentSnapshotId", "CONSENT_TRUSTED_CONTACT_001",
                "consentStatus", "NOT_GRANTED",
                "recipientAccepted", false,
                "triggerMatched", evaluated,
                "fieldScopeMatched", false,
                "validityMatched", false,
                "deliveryEnabled", false,
                "resultCode", evaluated ? "BLOCKED_BY_CONSENT" : null,
                "dispatchAttempted", false,
                "externalDeliveryRequested", false,
                "externalDeliveryCreated", false
        );
    }

    private String summary(List<SignalRow> signals) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        signals.forEach(signal -> counts.put(signal.reasonCode(), signal.observedCount()));
        return "최근 60일 정기납부 누락 " + counts.getOrDefault("MISSED_RECURRING", 0)
                + "건, 10분 내 중복송금 " + counts.getOrDefault("DUPLICATE_TRANSFER", 0)
                + "회, 완료 후 1시간 내 반복확인 " + counts.getOrDefault("REPEATED_CONFIRMATION", 0)
                + "회를 확인해 주세요.";
    }

    private List<String> reasonCodes(List<SignalRow> signals) {
        return signals.stream().map(SignalRow::reasonCode).toList();
    }

    private String algorithmVersion(List<SignalRow> signals) {
        return signals.getFirst().algorithmVersion();
    }

    private String signalId(String reasonCode) {
        return "SIG_" + reasonCode + "_001";
    }

    private int baselineValue(String reasonCode) {
        return "REPEATED_CONFIRMATION".equals(reasonCode) ? 1 : 0;
    }

    private String signalUnit(String reasonCode) {
        return switch (reasonCode) {
            case "MISSED_RECURRING" -> "COUNT_60D";
            case "DUPLICATE_TRANSFER" -> "COUNT_10M";
            case "REPEATED_CONFIRMATION" -> "COUNT_1H";
            default -> "COUNT";
        };
    }

    private List<String> signalEvidenceIds(String reasonCode) {
        return switch (reasonCode) {
            case "MISSED_RECURRING" -> List.of("OBLIGATION_UTILITY_001", "OBLIGATION_INSURANCE_001",
                    "OBLIGATION_TELECOM_001");
            case "DUPLICATE_TRANSFER" -> List.of("TX_DUP_A_001", "TX_DUP_A_002",
                    "TX_DUP_B_001", "TX_DUP_B_002");
            case "REPEATED_CONFIRMATION" -> List.of("INT_CONFIRM_OBS_001", "INT_CONFIRM_OBS_007");
            default -> List.of();
        };
    }

    private List<String> contextTypes(UUID sessionId, UUID demoRunId, String alertId) {
        List<String> json = jdbcTemplate.queryForList(
                """
                select context_types::text from context_event
                 where demo_session_id = ? and demo_run_id = ? and alert_id = ?
                """,
                String.class, sessionId, demoRunId, alertId
        );
        return json.isEmpty() ? List.of() : readStringList(json.getFirst());
    }

    private List<String> contextEvidenceIds(UUID sessionId, UUID demoRunId, String alertId) {
        List<String> json = jdbcTemplate.queryForList(
                """
                select context_evidence_ids::text from context_event
                 where demo_session_id = ? and demo_run_id = ? and alert_id = ?
                """,
                String.class, sessionId, demoRunId, alertId
        );
        return json.isEmpty() ? List.of() : readStringList(json.getFirst());
    }

    private IncidentRow incidentRow(ResultSet rs, int rowNum) throws SQLException {
        return new IncidentRow(
                rs.getString("alert_id"), rs.getString("customer_id"), rs.getString("state"),
                rs.getLong("incident_version"), rs.getString("pre_decision"), rs.getString("post_decision"),
                rs.getString("t1_context_evidence"), rs.getString("trusted_contact_gate"),
                rs.getObject("alert_snapshot_at", OffsetDateTime.class),
                rs.getObject("context_observed_at", OffsetDateTime.class)
        );
    }

    private CaseRow caseRow(ResultSet rs, int rowNum) throws SQLException {
        return new CaseRow(
                rs.getString("case_id"), rs.getString("alert_id"), rs.getString("customer_id"),
                rs.getString("review_priority"), rs.getString("review_task_status"),
                rs.getLong("case_version"), rs.getString("customer_response_code"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("follow_up_at", OffsetDateTime.class), rs.getString("state"),
                rs.getLong("incident_version"), rs.getString("pre_decision"), rs.getString("post_decision"),
                rs.getString("trusted_contact_gate"),
                rs.getObject("alert_snapshot_at", OffsetDateTime.class),
                rs.getObject("context_observed_at", OffsetDateTime.class)
        );
    }

    private FollowUpRow followUpRow(ResultSet rs, int rowNum) throws SQLException {
        return new FollowUpRow(
                rs.getObject("follow_up_id", UUID.class), rs.getString("case_id"), rs.getString("status"),
                rs.getObject("scheduled_at", OffsetDateTime.class), rs.getString("reason"), rs.getString("result_note"),
                rs.getObject("completed_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getString("alert_id")
        );
    }

    private QueueRow queueRow(ResultSet rs, int rowNum) throws SQLException {
        return new QueueRow(
                rs.getObject("demo_run_id", UUID.class), rs.getString("case_id"), rs.getString("alert_id"),
                rs.getString("customer_id"), rs.getString("state"), rs.getString("review_priority"),
                rs.getString("customer_response_code"), rs.getString("trusted_contact_gate"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getLong("case_version")
        );
    }

    private AuditRow auditRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditRow(
                rs.getObject("audit_id", UUID.class), rs.getObject("demo_run_id", UUID.class),
                rs.getString("event_type"), rs.getString("actor_type"), rs.getString("policy_version"),
                rs.getString("algorithm_version"), rs.getString("schema_version"), rs.getString("payload"),
                rs.getString("trace_id"), rs.getObject("occurred_at", OffsetDateTime.class)
        );
    }

    private Map<String, Object> toAuditItem(AuditRow row) {
        Map<String, Object> payload = readRequiredMap(row.payloadJson());
        return map(
                "auditId", row.auditId(),
                "demoRunId", row.demoRunId(),
                "eventType", row.eventType(),
                "actorType", payload.getOrDefault("actorType", row.actorType()),
                "fromState", payload.get("fromState"),
                "toState", payload.get("toState"),
                "resultCode", payload.get("resultCode"),
                "evidenceIds", payload.getOrDefault("evidenceIds", List.of()),
                "algorithmVersion", row.algorithmVersion(),
                "policyVersion", row.policyVersion(),
                "schemaVersion", row.schemaVersion(),
                "requestHash", payload.get("requestHash"),
                "idempotencyKeyHash", payload.get("idempotencyKeyHash"),
                "traceId", row.traceId(),
                "occurredAt", row.occurredAt()
        );
    }

    private AuditCursor decodeAuditCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf('|');
            return new AuditCursor(
                    OffsetDateTime.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1))
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "cursor가 올바르지 않습니다.");
        }
    }

    private String encodeAuditCursor(AuditRow row) {
        return encodeCursor(row.occurredAt() + "|" + row.auditId());
    }

    private CaseCursor decodeCaseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf('|');
            if (separator < 1 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException("cursor payload is incomplete");
            }
            return new CaseCursor(OffsetDateTime.parse(decoded.substring(0, separator)),
                    decoded.substring(separator + 1));
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "cursor가 올바르지 않습니다.");
        }
    }

    private String encodeCaseCursor(QueueRow row) {
        return encodeCursor(row.createdAt() + "|" + row.caseId());
    }

    private String encodeCursor(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("워크플로 데이터를 직렬화할 수 없습니다.", exception);
        }
    }

    private Map<String, Object> readNullableMap(String json) {
        return json == null ? null : readRequiredMap(json);
    }

    private Map<String, Object> readRequiredMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("워크플로 데이터를 읽을 수 없습니다.", exception);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("워크플로 목록 데이터를 읽을 수 없습니다.", exception);
        }
    }

    private static Map<String, Object> map(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("map key/value 개수가 맞지 않습니다.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record IncidentRow(
            String alertId,
            String customerId,
            String state,
            long incidentVersion,
            String preDecision,
            String postDecision,
            String t1ContextEvidenceJson,
            String trustedContactGateJson,
            OffsetDateTime alertSnapshotAt,
            OffsetDateTime contextObservedAt
    ) {
    }

    private record SignalRow(
            String reasonCode,
            int observedCount,
            int windowSeconds,
            String algorithmVersion,
            OffsetDateTime detectedAt,
            String snapshotHash
    ) {
    }

    private record CaseRow(
            String caseId,
            String alertId,
            String customerId,
            String reviewPriority,
            String reviewTaskStatus,
            long caseVersion,
            String customerResponseCode,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime followUpAt,
            String incidentState,
            long incidentVersion,
            String preDecision,
            String postDecision,
            String trustedContactGateJson,
            OffsetDateTime alertSnapshotAt,
            OffsetDateTime contextObservedAt
    ) {
    }

    private record QueueRow(
            UUID demoRunId,
            String caseId,
            String alertId,
            String customerId,
            String state,
            String reviewPriority,
            String customerResponseCode,
            String trustedContactGateJson,
            OffsetDateTime createdAt,
            long caseVersion
    ) {
    }

    private record AuditRow(
            UUID auditId,
            UUID demoRunId,
            String eventType,
            String actorType,
            String policyVersion,
            String algorithmVersion,
            String schemaVersion,
            String payloadJson,
            String traceId,
            OffsetDateTime occurredAt
    ) {
    }

    private record CommandScope(
            UUID sessionId,
            UUID demoRunId,
            String capabilityHash,
            String capabilityRole,
            String httpMethod,
            String operationPath,
            String idempotencyKeyHash
    ) {
    }

    private record StoredCommand(
            String requestHash,
            String responseCode,
            String responseMessage,
            String responsePayload
    ) {
    }

    private record FollowUpRow(
            UUID followUpId,
            String caseId,
            String status,
            OffsetDateTime scheduledAt,
            String reason,
            String resultNote,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String alertId
    ) {
    }

    private record AuditCursor(OffsetDateTime occurredAt, UUID auditId) {
    }

    private record CaseCursor(OffsetDateTime createdAt, String caseId) {
    }
}
