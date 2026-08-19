package com.alzswell.detection.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.detection.api.DetectionErrorCode;
import com.alzswell.detection.api.SyntheticDatasetRequests.CreateDatasetCommand;
import com.alzswell.detection.api.SyntheticDatasetRequests.EvidenceInput;
import com.alzswell.detection.api.SyntheticDatasetRequests.FeatureObservation;
import com.alzswell.detection.api.SyntheticDatasetResponses.DatasetDetail;
import com.alzswell.detection.api.SyntheticDatasetResponses.DatasetIngestion;
import com.alzswell.detection.api.SyntheticDatasetResponses.DatasetSummary;
import com.alzswell.detection.api.SyntheticDatasetResponses.DatasetValidation;
import com.alzswell.detection.api.SyntheticDatasetResponses.DetectedSignal;
import com.alzswell.detection.api.SyntheticDatasetResponses.DetectionRun;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyntheticDatasetService {
    private static final String ALGORITHM_VERSION = "baseline-rules-v2.0.0";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SyntheticDatasetService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DatasetDetail create(CreateDatasetCommand command) {
        requireCustomer(command.customerId());
        UUID datasetId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String payload = toJson(command.observations());
        String payloadHash = sha256(payload);
        int evidenceCount = command.observations().stream().mapToInt(item -> item.evidence().size()).sum();
        jdbcTemplate.update("""
                insert into synthetic_detection_dataset (
                    dataset_id, customer_id, dataset_name, status, payload, payload_hash,
                    observation_count, evidence_count, created_at
                ) values (?, ?, ?, 'DRAFT', ?::jsonb, ?, ?, ?, ?)
                """, datasetId, command.customerId(), command.datasetName().trim(), payload, payloadHash,
                command.observations().size(), evidenceCount, now);
        return dataset(datasetId);
    }

    @Transactional(readOnly = true)
    public DatasetDetail dataset(UUID datasetId) {
        List<DatasetDetail> rows = jdbcTemplate.query("""
                select dataset_id, customer_id, dataset_name, status, payload::text, payload_hash,
                       observation_count, evidence_count, validation_errors::text, row_version,
                       created_at, validated_at, ingested_at
                  from synthetic_detection_dataset where dataset_id = ?
                """, (rs, rowNum) -> new DatasetDetail(
                        new DatasetSummary(rs.getObject("dataset_id", UUID.class), rs.getString("customer_id"),
                                rs.getString("dataset_name"), rs.getString("status"),
                                rs.getInt("observation_count"), rs.getInt("evidence_count"),
                                rs.getString("payload_hash"), rs.getLong("row_version"),
                                rs.getObject("created_at", OffsetDateTime.class),
                                rs.getObject("validated_at", OffsetDateTime.class),
                                rs.getObject("ingested_at", OffsetDateTime.class)),
                        observations(rs.getString("payload")), strings(rs.getString("validation_errors")), true
                ), datasetId);
        if (rows.size() != 1) throw new BusinessException(DetectionErrorCode.DATASET_NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional
    public DatasetValidation validate(UUID datasetId) {
        DatasetDetail detail = dataset(datasetId);
        if (!Set.of("DRAFT", "INVALID").contains(detail.dataset().status())) {
            throw new BusinessException(DetectionErrorCode.DATASET_STATE_CONFLICT);
        }
        List<String> errors = semanticErrors(detail.observations());
        String status = errors.isEmpty() ? "VALIDATED" : "INVALID";
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                update synthetic_detection_dataset
                   set status = ?, validation_errors = ?::jsonb, validated_at = ?,
                       row_version = row_version + 1
                 where dataset_id = ?
                """, status, toJson(errors), now, datasetId);
        DatasetDetail updated = dataset(datasetId);
        return new DatasetValidation(datasetId, status, errors, updated.dataset().version(), now);
    }

    @Transactional
    public DatasetIngestion ingest(UUID datasetId) {
        DatasetDetail detail = dataset(datasetId);
        if (!detail.dataset().status().equals("VALIDATED")) {
            throw new BusinessException(DetectionErrorCode.DATASET_STATE_CONFLICT);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update("""
                update synthetic_detection_dataset
                   set status = 'INGESTED', ingested_at = ?, row_version = row_version + 1
                 where dataset_id = ? and status = 'VALIDATED'
                """, now, datasetId);
        DatasetDetail updated = dataset(datasetId);
        return new DatasetIngestion(datasetId, updated.dataset().status(), updated.dataset().payloadHash(),
                updated.dataset().version(), now, false);
    }

    @Transactional
    public DetectionRun run(String customerId, UUID datasetId, String idempotencyKey) {
        requireCustomer(customerId);
        String keyHash = sha256(idempotencyKey);
        DetectionRun replay = findRun(customerId, keyHash, true);
        if (replay != null) {
            if (!replay.datasetId().equals(datasetId)) {
                throw new BusinessException(DetectionErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return replay;
        }
        DatasetDetail detail = dataset(datasetId);
        if (!detail.dataset().customerId().equals(customerId) || !detail.dataset().status().equals("INGESTED")) {
            throw new BusinessException(DetectionErrorCode.DATASET_STATE_CONFLICT);
        }
        List<DetectedSignal> signals = detect(detail.observations());
        UUID runId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String requestHash = sha256("POST|" + customerId + "|" + datasetId);
        String resultJson = toJson(signals);
        String resultHash = sha256(resultJson);
        int inserted = jdbcTemplate.update("""
                insert into synthetic_detection_run (
                    detection_run_id, dataset_id, customer_id, status, algorithm_version,
                    idempotency_key_hash, request_hash, input_payload_hash, result_payload,
                    result_hash, signal_count, started_at, completed_at
                ) values (?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                on conflict (customer_id, idempotency_key_hash) do nothing
                """, runId, datasetId, customerId, ALGORITHM_VERSION, keyHash, requestHash,
                detail.dataset().payloadHash(), resultJson, resultHash, signals.size(), now, now);
        if (inserted == 0) {
            DetectionRun concurrentReplay = requiredRun(customerId, keyHash, true);
            if (!concurrentReplay.datasetId().equals(datasetId)) {
                throw new BusinessException(DetectionErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return concurrentReplay;
        }
        return new DetectionRun(runId, datasetId, customerId, "COMPLETED", ALGORITHM_VERSION, signals,
                signals.size(), now, now, detail.dataset().payloadHash(), resultHash, requestHash,
                false, false, false);
    }

    @Transactional(readOnly = true)
    public DetectionRun run(UUID runId) {
        List<DetectionRun> rows = jdbcTemplate.query("""
                select detection_run_id, dataset_id, customer_id, status, algorithm_version,
                       result_payload::text, signal_count, started_at, completed_at,
                       input_payload_hash, result_hash, request_hash
                  from synthetic_detection_run where detection_run_id = ?
                """, (rs, rowNum) -> mapRun(rs, false), runId);
        if (rows.size() != 1) throw new BusinessException(DetectionErrorCode.DETECTION_RUN_NOT_FOUND);
        return rows.getFirst();
    }

    private List<String> semanticErrors(List<FeatureObservation> observations) {
        List<String> errors = new ArrayList<>();
        Set<String> featureCodes = new HashSet<>();
        Set<String> evidenceReferences = new HashSet<>();
        for (FeatureObservation observation : observations) {
            if (!featureCodes.add(observation.featureCode())) {
                errors.add("featureCode가 중복되었습니다: " + observation.featureCode());
            }
            if (observation.featureCode().equals("DUPLICATE_TRANSFER")
                    && observation.currentValue().intValue() < 2) {
                errors.add("DUPLICATE_TRANSFER currentValue는 2 이상이어야 합니다.");
            }
            for (EvidenceInput evidence : observation.evidence()) {
                if (!evidenceReferences.add(evidence.sourceReference())) {
                    errors.add("sourceReference가 중복되었습니다: " + evidence.sourceReference());
                }
                if ((evidence.amount() == null) != (evidence.currency() == null)) {
                    errors.add("amount와 currency는 함께 입력하거나 함께 생략해야 합니다: "
                            + evidence.sourceReference());
                }
            }
        }
        return List.copyOf(errors);
    }

    private List<DetectedSignal> detect(List<FeatureObservation> observations) {
        return observations.stream()
                .filter(item -> item.currentValue().compareTo(item.baselineValue()) > 0)
                .map(item -> new DetectedSignal(item.featureCode(), severity(item),
                        item.baselineValue().toPlainString(), item.currentValue().toPlainString(), item.unit(),
                        item.featureCode(), item.evidence().stream().map(EvidenceInput::sourceReference).toList()))
                .toList();
    }

    private String severity(FeatureObservation observation) {
        java.math.BigDecimal delta = observation.currentValue().subtract(observation.baselineValue());
        if (observation.featureCode().equals("REPEATED_CONFIRMATION") && delta.intValue() < 4) return "MEDIUM";
        return "HIGH";
    }

    private DetectionRun findRun(String customerId, String keyHash, boolean replayed) {
        List<DetectionRun> rows = jdbcTemplate.query("""
                select detection_run_id, dataset_id, customer_id, status, algorithm_version,
                       result_payload::text, signal_count, started_at, completed_at,
                       input_payload_hash, result_hash, request_hash
                  from synthetic_detection_run
                 where customer_id = ? and idempotency_key_hash = ?
                """, (rs, rowNum) -> mapRun(rs, replayed), customerId, keyHash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private DetectionRun requiredRun(String customerId, String keyHash, boolean replayed) {
        DetectionRun result = findRun(customerId, keyHash, replayed);
        if (result == null) throw new IllegalStateException("멱등 탐지 실행 결과를 조회할 수 없습니다.");
        return result;
    }

    private DetectionRun mapRun(java.sql.ResultSet rs, boolean replayed) throws java.sql.SQLException {
        return new DetectionRun(rs.getObject("detection_run_id", UUID.class),
                rs.getObject("dataset_id", UUID.class), rs.getString("customer_id"), rs.getString("status"),
                rs.getString("algorithm_version"), signals(rs.getString("result_payload")),
                rs.getInt("signal_count"), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class), rs.getString("input_payload_hash"),
                rs.getString("result_hash"), rs.getString("request_hash"), replayed, false, false);
    }

    private void requireCustomer(String customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_profile where customer_id = ?", Integer.class, customerId);
        if (count == null || count == 0) throw new BusinessException(DetectionErrorCode.CUSTOMER_NOT_FOUND);
    }

    private List<FeatureObservation> observations(String json) {
        return read(json, new TypeReference<>() {});
    }

    private List<String> strings(String json) {
        return read(json, new TypeReference<>() {});
    }

    private List<DetectedSignal> signals(String json) {
        return read(json, new TypeReference<>() {});
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 합성 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("합성 JSON을 직렬화할 수 없습니다.", exception);
        }
    }

    private String sha256(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
