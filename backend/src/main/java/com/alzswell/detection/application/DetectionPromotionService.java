package com.alzswell.detection.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.detection.api.DetectionErrorCode;
import com.alzswell.detection.api.DetectionPromotionResponses.DetectionPromotion;
import com.alzswell.detection.api.SyntheticDatasetRequests.EvidenceInput;
import com.alzswell.detection.api.SyntheticDatasetRequests.FeatureObservation;
import com.alzswell.detection.api.SyntheticDatasetResponses.DetectedSignal;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionPromotionService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DetectionPromotionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DetectionPromotion promote(UUID detectionRunId, AuditActor actor) {
        RunSource source = lockRun(detectionRunId);
        DetectionPromotion replay = find(detectionRunId, true);
        if (replay != null) return replay;
        if (!source.status().equals("COMPLETED")) {
            throw new BusinessException(DetectionErrorCode.PROMOTION_STATE_CONFLICT);
        }

        List<FeatureObservation> observations = read(source.datasetPayload(), new TypeReference<>() {});
        List<DetectedSignal> detectedSignals = read(source.resultPayload(), new TypeReference<>() {});
        List<UUID> signalIds = new ArrayList<>();
        List<UUID> alertIds = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(clock);

        for (DetectedSignal detected : detectedSignals) {
            FeatureObservation observation = observations.stream()
                    .filter(item -> item.featureCode().equals(detected.featureCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(DetectionErrorCode.PROMOTION_SOURCE_INVALID));
            UUID baselineId = matchingBaseline(source.customerId(), observation);
            UUID signalId = UUID.randomUUID();
            String signalHash = sha256(source.resultHash() + "|" + detected.reasonCode());
            jdbcTemplate.update("""
                    insert into customer_detection_signal (
                        signal_id, customer_id, baseline_id, signal_type, severity, baseline_value,
                        current_value, unit, reason_code, status, algorithm_version, detected_at,
                        snapshot_hash, source_detection_run_id
                    ) values (?, ?, ?, 'BEHAVIOR_CHANGE', ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
                    """, signalId, source.customerId(), baselineId, detected.severity(),
                    observation.baselineValue(), observation.currentValue(), observation.unit(),
                    detected.reasonCode(), source.algorithmVersion(), now, signalHash, detectionRunId);
            insertEvidence(signalId, observation.evidence());

            UUID alertId = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into operational_alert (
                        alert_id, signal_id, customer_id, state, severity, reason_code,
                        alert_version, created_at, updated_at
                    ) values (?, ?, ?, 'AWAITING_CONTEXT', ?, ?, 1, ?, ?)
                    """, alertId, signalId, source.customerId(), detected.severity(),
                    detected.reasonCode(), now, now);
            writeAlertCreatedAudit(alertId, signalId, detectionRunId, actor, now);
            signalIds.add(signalId);
            alertIds.add(alertId);
        }

        UUID promotionId = UUID.randomUUID();
        String resultMaterial = detectionRunId + "|" + signalIds + "|" + alertIds;
        String promotionResultHash = sha256(resultMaterial);
        jdbcTemplate.update("""
                insert into detection_run_promotion (
                    promotion_id, detection_run_id, customer_id, status, signal_ids, alert_ids,
                    promoted_signal_count, promoted_alert_count, input_result_hash,
                    promotion_result_hash, promoted_at
                ) values (?, ?, ?, 'COMPLETED', ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """, promotionId, detectionRunId, source.customerId(), json(signalIds), json(alertIds),
                signalIds.size(), alertIds.size(), source.resultHash(), promotionResultHash, now);
        return new DetectionPromotion(promotionId, detectionRunId, source.customerId(), "COMPLETED",
                List.copyOf(signalIds), List.copyOf(alertIds), signalIds.size(), alertIds.size(),
                source.resultHash(), promotionResultHash, now, false, false, false);
    }

    @Transactional(readOnly = true)
    public DetectionPromotion promotion(UUID detectionRunId) {
        DetectionPromotion result = find(detectionRunId, false);
        if (result == null) throw new BusinessException(DetectionErrorCode.PROMOTION_NOT_FOUND);
        return result;
    }

    private RunSource lockRun(UUID runId) {
        List<RunSource> rows = jdbcTemplate.query("""
                select r.detection_run_id, r.customer_id, r.status, r.algorithm_version,
                       r.result_payload::text, r.result_hash, d.payload::text
                  from synthetic_detection_run r
                  join synthetic_detection_dataset d on d.dataset_id = r.dataset_id
                 where r.detection_run_id = ? for update of r
                """, (rs, rowNum) -> new RunSource(rs.getObject("detection_run_id", UUID.class),
                        rs.getString("customer_id"), rs.getString("status"),
                        rs.getString("algorithm_version"), rs.getString("result_payload"),
                        rs.getString("result_hash"), rs.getString("payload")), runId);
        if (rows.size() != 1) throw new BusinessException(DetectionErrorCode.DETECTION_RUN_NOT_FOUND);
        return rows.getFirst();
    }

    private UUID matchingBaseline(String customerId, FeatureObservation observation) {
        List<UUID> rows = jdbcTemplate.query("""
                select baseline_id from customer_baseline_snapshot
                 where customer_id = ? and feature_code = ? and baseline_value = ? and unit = ?
                """, (rs, rowNum) -> rs.getObject("baseline_id", UUID.class), customerId,
                observation.featureCode(), observation.baselineValue(), observation.unit());
        if (rows.size() != 1) throw new BusinessException(DetectionErrorCode.PROMOTION_BASELINE_MISMATCH);
        return rows.getFirst();
    }

    private void insertEvidence(UUID signalId, List<EvidenceInput> evidenceItems) {
        for (EvidenceInput evidence : evidenceItems) {
            jdbcTemplate.update("""
                    insert into customer_signal_evidence_snapshot (
                        evidence_id, signal_id, evidence_type, source_reference, occurred_at,
                        amount, currency, description, integrity_hash
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), signalId, evidence.evidenceType(), evidence.sourceReference(),
                    evidence.occurredAt(), evidence.amount(), evidence.currency(), evidence.description(),
                    sha256(signalId + "|" + evidence.sourceReference() + "|" + evidence.occurredAt()));
        }
    }

    private void writeAlertCreatedAudit(
            UUID alertId, UUID signalId, UUID runId, AuditActor actor, OffsetDateTime now) {
        String detail = json(java.util.Map.of("signalId", signalId, "detectionRunId", runId,
                "syntheticData", true));
        jdbcTemplate.update("""
                insert into operational_alert_audit_event (
                    audit_event_id, alert_id, event_type, previous_state, resulting_state,
                    detail, integrity_hash, created_at, actor_principal_id, actor_customer_id,
                    actor_session_id, actor_type
                ) values (?, ?, 'ALERT_CREATED', null, 'AWAITING_CONTEXT', ?::jsonb, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), alertId, detail,
                sha256(alertId + "|ALERT_CREATED|AWAITING_CONTEXT|" + detail + "|"
                        + actor.principalId() + "|" + actor.customerId() + "|"
                        + actor.sessionId() + "|" + actor.actorType() + "|" + now), now,
                actor.principalId(), actor.customerId(), actor.sessionId(), actor.actorType());
    }

    private DetectionPromotion find(UUID runId, boolean replayed) {
        List<DetectionPromotion> rows = jdbcTemplate.query("""
                select promotion_id, detection_run_id, customer_id, status, signal_ids::text,
                       alert_ids::text, promoted_signal_count, promoted_alert_count,
                       input_result_hash, promotion_result_hash, promoted_at
                  from detection_run_promotion where detection_run_id = ?
                """, (rs, rowNum) -> mapPromotion(rs, replayed), runId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private DetectionPromotion mapPromotion(ResultSet rs, boolean replayed) throws SQLException {
        return new DetectionPromotion(rs.getObject("promotion_id", UUID.class),
                rs.getObject("detection_run_id", UUID.class), rs.getString("customer_id"),
                rs.getString("status"), read(rs.getString("signal_ids"), new TypeReference<>() {}),
                read(rs.getString("alert_ids"), new TypeReference<>() {}),
                rs.getInt("promoted_signal_count"), rs.getInt("promoted_alert_count"),
                rs.getString("input_result_hash"), rs.getString("promotion_result_hash"),
                rs.getObject("promoted_at", OffsetDateTime.class), replayed, false, false);
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 승격 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("승격 결과를 직렬화할 수 없습니다.", exception);
        }
    }

    private String sha256(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record RunSource(
            UUID runId, String customerId, String status, String algorithmVersion,
            String resultPayload, String resultHash, String datasetPayload
    ) {}
}
