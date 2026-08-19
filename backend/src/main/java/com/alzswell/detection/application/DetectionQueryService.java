package com.alzswell.detection.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.detection.api.DetectionErrorCode;
import com.alzswell.detection.api.DetectionResponses.BaselineCalculation;
import com.alzswell.detection.api.DetectionResponses.BaselineDetail;
import com.alzswell.detection.api.DetectionResponses.BaselineFeature;
import com.alzswell.detection.api.DetectionResponses.BaselineFeatureList;
import com.alzswell.detection.api.DetectionResponses.BaselineList;
import com.alzswell.detection.api.DetectionResponses.BaselineSummary;
import com.alzswell.detection.api.DetectionResponses.DatePeriod;
import com.alzswell.detection.api.DetectionResponses.SignalDetail;
import com.alzswell.detection.api.DetectionResponses.SignalEvidence;
import com.alzswell.detection.api.DetectionResponses.SignalEvidenceList;
import com.alzswell.detection.api.DetectionResponses.SignalList;
import com.alzswell.detection.api.DetectionResponses.SignalSummary;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionQueryService {
    private static final String ALGORITHM_VERSION = "baseline-rules-v2.0.0";
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public DetectionQueryService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BaselineList baselines(String customerId) {
        requireCustomer(customerId);
        List<BaselineSummary> items = jdbcTemplate.query("""
                select baseline_id, customer_id, feature_code, baseline_value, current_value,
                       unit, readiness, comparison_text, algorithm_version, calculated_at, row_version
                  from customer_baseline_snapshot
                 where customer_id = ? order by feature_code, baseline_id
                """, this::mapBaselineSummary, customerId);
        return new BaselineList(items, items.size());
    }

    @Transactional(readOnly = true)
    public BaselineDetail baseline(String customerId, UUID baselineId) {
        requireCustomer(customerId);
        List<BaselineDetail> rows = jdbcTemplate.query("""
                select baseline_id, customer_id, feature_code, baseline_value, current_value,
                       unit, readiness, comparison_text, algorithm_version, calculated_at,
                       row_version, baseline_from, baseline_to, observation_from, observation_to,
                       snapshot_hash
                  from customer_baseline_snapshot
                 where customer_id = ? and baseline_id = ?
                """, (rs, rowNum) -> new BaselineDetail(
                        baselineSummary(rs.getObject("baseline_id", UUID.class), rs.getString("customer_id"),
                                rs.getString("feature_code"), rs.getBigDecimal("baseline_value").toPlainString(),
                                rs.getBigDecimal("current_value").toPlainString(), rs.getString("unit"),
                                rs.getString("readiness"), rs.getString("comparison_text"),
                                rs.getString("algorithm_version"),
                                rs.getObject("calculated_at", OffsetDateTime.class), rs.getLong("row_version")),
                        new DatePeriod(rs.getObject("baseline_from", LocalDate.class),
                                rs.getObject("baseline_to", LocalDate.class)),
                        new DatePeriod(rs.getObject("observation_from", LocalDate.class),
                                rs.getObject("observation_to", LocalDate.class)),
                        rs.getString("snapshot_hash")
                ), customerId, baselineId);
        return exactlyOne(rows, DetectionErrorCode.BASELINE_NOT_FOUND);
    }

    @Transactional(readOnly = true)
    public BaselineFeatureList baselineFeatures(String customerId, UUID baselineId) {
        baseline(customerId, baselineId);
        List<BaselineFeature> items = jdbcTemplate.query("""
                select feature_id, feature_code, feature_value, unit, observed_from, observed_to,
                       sample_count, snapshot_hash
                  from customer_baseline_feature_snapshot
                 where baseline_id = ? order by feature_code, feature_id
                """, (rs, rowNum) -> new BaselineFeature(
                        rs.getObject("feature_id", UUID.class), rs.getString("feature_code"),
                        rs.getBigDecimal("feature_value").toPlainString(), rs.getString("unit"),
                        new DatePeriod(rs.getObject("observed_from", LocalDate.class),
                                rs.getObject("observed_to", LocalDate.class)),
                        rs.getInt("sample_count"), rs.getString("snapshot_hash")
                ), baselineId);
        return new BaselineFeatureList(baselineId, items, items.size());
    }

    @Transactional
    public BaselineCalculation calculate(String customerId, String idempotencyKey) {
        requireCustomer(customerId);
        String idempotencyKeyHash = sha256(idempotencyKey);
        BaselineCalculation replay = findCalculation(customerId, idempotencyKeyHash, true);
        if (replay != null) return replay;
        List<String> hashes = jdbcTemplate.queryForList("""
                select snapshot_hash from customer_baseline_snapshot where customer_id = ?
                union all
                select snapshot_hash from customer_detection_signal where customer_id = ?
                order by snapshot_hash
                """, String.class, customerId, customerId);
        if (hashes.isEmpty()) throw new BusinessException(DetectionErrorCode.SNAPSHOT_NOT_READY);

        int baselineCount = count("customer_baseline_snapshot", customerId);
        int signalCount = count("customer_detection_signal", customerId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID calculationId = UUID.randomUUID();
        String inputHash = sha256(String.join("|", hashes));
        String requestHash = sha256("POST|/api/v1/customers/" + customerId + "/baseline-calculations");
        String resultHash = sha256(customerId + "|" + ALGORITHM_VERSION + "|" + inputHash);
        int inserted = jdbcTemplate.update("""
                insert into baseline_calculation_job (
                    calculation_id, customer_id, status, algorithm_version, idempotency_key_hash,
                    request_hash, input_snapshot_hash,
                    result_snapshot_hash, baselines_evaluated, signals_evaluated, reused_current_snapshot,
                    requested_at, completed_at
                ) values (?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                on conflict (customer_id, idempotency_key_hash) do nothing
                """, calculationId, customerId, ALGORITHM_VERSION, idempotencyKeyHash, requestHash,
                inputHash, resultHash, baselineCount, signalCount, now, now);
        if (inserted == 0) return findRequiredCalculation(customerId, idempotencyKeyHash, true);
        return new BaselineCalculation(calculationId, customerId, "COMPLETED", ALGORITHM_VERSION,
                baselineCount, signalCount, true, now, now, resultHash, requestHash, false, false);
    }

    @Transactional(readOnly = true)
    public SignalList signals(String customerId, String severity, String status) {
        requireCustomer(customerId);
        List<SignalSummary> items;
        if (severity != null && status != null) {
            items = jdbcTemplate.query("""
                    select signal_id, customer_id, baseline_id, signal_type, severity, baseline_value,
                           current_value, unit, reason_code, status, algorithm_version, detected_at
                      from customer_detection_signal
                     where customer_id = ? and severity = ? and status = ?
                     order by detected_at desc, signal_id desc
                    """, this::mapSignalSummary, customerId, severity, status);
        } else if (severity != null) {
            items = jdbcTemplate.query("""
                    select signal_id, customer_id, baseline_id, signal_type, severity, baseline_value,
                           current_value, unit, reason_code, status, algorithm_version, detected_at
                      from customer_detection_signal
                     where customer_id = ? and severity = ?
                     order by detected_at desc, signal_id desc
                    """, this::mapSignalSummary, customerId, severity);
        } else if (status != null) {
            items = jdbcTemplate.query("""
                    select signal_id, customer_id, baseline_id, signal_type, severity, baseline_value,
                           current_value, unit, reason_code, status, algorithm_version, detected_at
                      from customer_detection_signal
                     where customer_id = ? and status = ?
                     order by detected_at desc, signal_id desc
                    """, this::mapSignalSummary, customerId, status);
        } else {
            items = jdbcTemplate.query("""
                    select signal_id, customer_id, baseline_id, signal_type, severity, baseline_value,
                           current_value, unit, reason_code, status, algorithm_version, detected_at
                      from customer_detection_signal
                     where customer_id = ? order by detected_at desc, signal_id desc
                    """, this::mapSignalSummary, customerId);
        }
        return new SignalList(items, items.size());
    }

    @Transactional(readOnly = true)
    public SignalDetail signal(UUID signalId, String actorCustomerId, boolean readAll) {
        List<SignalDetail> rows = readAll
                ? jdbcTemplate.query("""
                        select signal_id, customer_id, baseline_id, signal_type, severity, baseline_value,
                               current_value, unit, reason_code, status, algorithm_version, detected_at, snapshot_hash
                          from customer_detection_signal where signal_id = ?
                        """, this::mapSignalDetail, signalId)
                : jdbcTemplate.query("""
                        select signal_id, customer_id, baseline_id, signal_type, severity, baseline_value,
                               current_value, unit, reason_code, status, algorithm_version, detected_at, snapshot_hash
                          from customer_detection_signal where signal_id = ? and customer_id = ?
                        """, this::mapSignalDetail, signalId, actorCustomerId);
        return exactlyOne(rows, DetectionErrorCode.SIGNAL_NOT_FOUND);
    }

    @Transactional(readOnly = true)
    public SignalEvidenceList evidence(UUID signalId, String actorCustomerId, boolean readAll) {
        signal(signalId, actorCustomerId, readAll);
        List<SignalEvidence> items = jdbcTemplate.query("""
                select evidence_id, evidence_type, source_reference, occurred_at, amount, currency,
                       description, integrity_hash
                  from customer_signal_evidence_snapshot
                 where signal_id = ? order by occurred_at, evidence_id
                """, (rs, rowNum) -> {
                    java.math.BigDecimal amount = rs.getBigDecimal("amount");
                    return new SignalEvidence(rs.getObject("evidence_id", UUID.class),
                            rs.getString("evidence_type"), rs.getString("source_reference"),
                            rs.getObject("occurred_at", OffsetDateTime.class),
                            amount == null ? null : amount.toPlainString(), rs.getString("currency"),
                            rs.getString("description"), rs.getString("integrity_hash"));
                }, signalId);
        return new SignalEvidenceList(signalId, items, items.size());
    }

    private BaselineSummary mapBaselineSummary(ResultSet rs, int rowNum) throws SQLException {
        return baselineSummary(rs.getObject("baseline_id", UUID.class), rs.getString("customer_id"),
                rs.getString("feature_code"), rs.getBigDecimal("baseline_value").toPlainString(),
                rs.getBigDecimal("current_value").toPlainString(), rs.getString("unit"),
                rs.getString("readiness"), rs.getString("comparison_text"),
                rs.getString("algorithm_version"), rs.getObject("calculated_at", OffsetDateTime.class),
                rs.getLong("row_version"));
    }

    private SignalSummary mapSignalSummary(ResultSet rs, int rowNum) throws SQLException {
        return signalSummary(rs.getObject("signal_id", UUID.class), rs.getString("customer_id"),
                rs.getObject("baseline_id", UUID.class), rs.getString("signal_type"),
                rs.getString("severity"), rs.getBigDecimal("baseline_value").toPlainString(),
                rs.getBigDecimal("current_value").toPlainString(), rs.getString("unit"),
                rs.getString("reason_code"), rs.getString("status"),
                rs.getString("algorithm_version"), rs.getObject("detected_at", OffsetDateTime.class));
    }

    private SignalDetail mapSignalDetail(ResultSet rs, int rowNum) throws SQLException {
        return new SignalDetail(mapSignalSummary(rs, rowNum), rs.getString("snapshot_hash"));
    }

    private BaselineSummary baselineSummary(UUID id, String customerId, String featureCode,
                                             String baselineValue, String currentValue, String unit,
                                             String readiness, String comparisonText, String algorithmVersion,
                                             OffsetDateTime calculatedAt, long version) {
        return new BaselineSummary(id, customerId, featureCode, baselineValue, currentValue, unit,
                readiness, comparisonText, algorithmVersion, calculatedAt, version);
    }

    private SignalSummary signalSummary(UUID id, String customerId, UUID baselineId, String signalType,
                                         String severity, String baselineValue, String currentValue, String unit,
                                         String reasonCode, String status, String algorithmVersion,
                                         OffsetDateTime detectedAt) {
        return new SignalSummary(id, customerId, baselineId, signalType, severity, baselineValue,
                currentValue, unit, reasonCode, status, algorithmVersion, detectedAt);
    }

    private void requireCustomer(String customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_profile where customer_id = ?", Integer.class, customerId);
        if (count == null || count == 0) throw new BusinessException(DetectionErrorCode.CUSTOMER_NOT_FOUND);
    }

    private int count(String table, String customerId) {
        String sql = switch (table) {
            case "customer_baseline_snapshot" ->
                    "select count(*) from customer_baseline_snapshot where customer_id = ?";
            case "customer_detection_signal" ->
                    "select count(*) from customer_detection_signal where customer_id = ?";
            default -> throw new IllegalArgumentException("허용되지 않은 집계 테이블입니다.");
        };
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, customerId);
        return result == null ? 0 : result;
    }

    private BaselineCalculation findCalculation(String customerId, String idempotencyKeyHash, boolean replayed) {
        List<BaselineCalculation> rows = jdbcTemplate.query("""
                select calculation_id, customer_id, status, algorithm_version, baselines_evaluated,
                       signals_evaluated, reused_current_snapshot, requested_at, completed_at,
                       result_snapshot_hash, request_hash
                  from baseline_calculation_job
                 where customer_id = ? and idempotency_key_hash = ?
                """, (rs, rowNum) -> new BaselineCalculation(
                        rs.getObject("calculation_id", UUID.class), rs.getString("customer_id"),
                        rs.getString("status"), rs.getString("algorithm_version"),
                        rs.getInt("baselines_evaluated"), rs.getInt("signals_evaluated"),
                        rs.getBoolean("reused_current_snapshot"),
                        rs.getObject("requested_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class),
                        rs.getString("result_snapshot_hash"), rs.getString("request_hash"), replayed, false
                ), customerId, idempotencyKeyHash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private BaselineCalculation findRequiredCalculation(
            String customerId, String idempotencyKeyHash, boolean replayed) {
        BaselineCalculation calculation = findCalculation(customerId, idempotencyKeyHash, replayed);
        if (calculation == null) throw new IllegalStateException("멱등 계산 결과를 조회할 수 없습니다.");
        return calculation;
    }

    private <T> T exactlyOne(List<T> rows, DetectionErrorCode errorCode) {
        if (rows.size() != 1) throw new BusinessException(errorCode);
        return rows.getFirst();
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
