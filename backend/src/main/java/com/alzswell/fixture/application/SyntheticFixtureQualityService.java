package com.alzswell.fixture.application;

import com.alzswell.detection.api.SyntheticDatasetResponses.DetectionRun;
import com.alzswell.detection.application.DetectionPolicyService;
import com.alzswell.detection.application.SyntheticDatasetService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyntheticFixtureQualityService {
    private final JdbcTemplate jdbc;
    private final SyntheticDatasetService datasets;
    private final DetectionPolicyService policies;
    private final Clock clock;

    public SyntheticFixtureQualityService(
            JdbcTemplate jdbc,
            SyntheticDatasetService datasets,
            DetectionPolicyService policies,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.datasets = datasets;
        this.policies = policies;
        this.clock = clock;
    }

    public QualityReport evaluate(UUID runId) {
        FixtureRun fixture = requiredFixture(runId);
        DetectionPolicyService.ActivePolicy policy = policies.activePolicy();
        QualityReport replay = find(runId, policy.versionCode(),
                DetectionPolicyService.ALGORITHM_VERSION, true);
        if (replay != null) {
            return replay;
        }

        List<FixtureCustomer> customers = jdbc.query("""
                select customer_index,customer_id,dataset_id,expected_signal_count
                  from synthetic_fixture_customer
                 where run_id=? order by customer_index
                """, (rs, rowNumber) -> new FixtureCustomer(
                        rs.getInt("customer_index"), rs.getString("customer_id"),
                        rs.getObject("dataset_id", UUID.class), rs.getInt("expected_signal_count")
                ), runId);
        if (customers.size() != fixture.customerCount()) {
            throw new IllegalStateException("합성 fixture 고객 manifest가 완료 건수와 일치하지 않습니다.");
        }

        Metrics metrics = new Metrics();
        boolean policyStable = true;
        for (FixtureCustomer customer : customers) {
            DetectionRun detection = datasets.run(customer.customerId(), customer.datasetId(),
                    commandId(runId, policy.versionCode(), customer.customerIndex()));
            policyStable &= policy.versionCode().equals(detection.policyVersion())
                    && policy.rulesHash().equals(detection.policySnapshotHash())
                    && DetectionPolicyService.ALGORITHM_VERSION.equals(detection.algorithmVersion())
                    && !detection.advisoryAiUsed() && !detection.externalExecutionCreated();
            metrics.add(customer.expectedSignalCount(), detection.signalCount());
        }

        DetectionPolicyService.ActivePolicy completedPolicy = policies.activePolicy();
        policyStable &= policy.versionCode().equals(completedPolicy.versionCode())
                && policy.rulesHash().equals(completedPolicy.rulesHash());
        String status = policyStable && metrics.matchesExactly() ? "PASSED" : "FAILED";
        OffsetDateTime evaluatedAt = OffsetDateTime.now(clock);
        String reportHash = sha256(String.join("|",
                runId.toString(), fixture.manifestHash(), policy.versionCode(), policy.rulesHash(),
                DetectionPolicyService.ALGORITHM_VERSION, status, Boolean.toString(policyStable),
                metrics.material()));
        int inserted = jdbc.update("""
                insert into synthetic_fixture_quality_report(
                    run_id,policy_version,algorithm_version,policy_snapshot_hash,status,policy_stable,
                    evaluated_customer_count,expected_signal_count,actual_signal_count,
                    true_positive_count,true_negative_count,false_positive_count,false_negative_count,
                    precision_score,recall_score,report_hash,evaluated_at
                ) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict(run_id,policy_version,algorithm_version) do nothing
                """, runId, policy.versionCode(), DetectionPolicyService.ALGORITHM_VERSION,
                policy.rulesHash(), status, policyStable, metrics.evaluatedCustomers,
                metrics.expectedSignals, metrics.actualSignals, metrics.truePositives,
                metrics.trueNegatives, metrics.falsePositives, metrics.falseNegatives,
                metrics.precision(), metrics.recall(), reportHash, evaluatedAt);
        if (inserted == 0) {
            return required(runId, policy.versionCode(), DetectionPolicyService.ALGORITHM_VERSION, true);
        }
        return required(runId, policy.versionCode(), DetectionPolicyService.ALGORITHM_VERSION, false);
    }

    private FixtureRun requiredFixture(UUID runId) {
        List<FixtureRun> rows = jdbc.query("""
                select actual_customer_count,manifest_hash,status
                  from synthetic_fixture_generation_run where run_id=?
                """, (rs, rowNumber) -> new FixtureRun(
                        rs.getInt("actual_customer_count"), rs.getString("manifest_hash"),
                        rs.getString("status")
                ), runId);
        if (rows.size() != 1 || !"SUCCEEDED".equals(rows.getFirst().status())) {
            throw new IllegalStateException("완료된 합성 fixture만 탐지 품질을 평가할 수 있습니다.");
        }
        return rows.getFirst();
    }

    private QualityReport required(UUID runId, String policyVersion, String algorithmVersion,
                                   boolean replayed) {
        QualityReport report = find(runId, policyVersion, algorithmVersion, replayed);
        if (report == null) {
            throw new IllegalStateException("합성 fixture 탐지 품질 보고서를 조회할 수 없습니다.");
        }
        return report;
    }

    private QualityReport find(UUID runId, String policyVersion, String algorithmVersion,
                               boolean replayed) {
        List<QualityReport> rows = jdbc.query("""
                select run_id,policy_version,algorithm_version,policy_snapshot_hash,status,policy_stable,
                       evaluated_customer_count,expected_signal_count,actual_signal_count,
                       true_positive_count,true_negative_count,false_positive_count,false_negative_count,
                       precision_score,recall_score,report_hash,evaluated_at
                  from synthetic_fixture_quality_report
                 where run_id=? and policy_version=? and algorithm_version=?
                """, (rs, rowNumber) -> new QualityReport(
                        rs.getObject("run_id", UUID.class), rs.getString("policy_version"),
                        rs.getString("algorithm_version"), rs.getString("policy_snapshot_hash"),
                        rs.getString("status"), rs.getBoolean("policy_stable"),
                        rs.getInt("evaluated_customer_count"), rs.getInt("expected_signal_count"),
                        rs.getInt("actual_signal_count"), rs.getInt("true_positive_count"),
                        rs.getInt("true_negative_count"), rs.getInt("false_positive_count"),
                        rs.getInt("false_negative_count"), rs.getBigDecimal("precision_score"),
                        rs.getBigDecimal("recall_score"), rs.getString("report_hash"),
                        rs.getObject("evaluated_at", OffsetDateTime.class), replayed
                ), runId, policyVersion, algorithmVersion);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String commandId(UUID runId, String policyVersion, int customerIndex) {
        return "fixture-quality:" + runId + ":" + policyVersion + ":" + customerIndex;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", impossible);
        }
    }

    private record FixtureRun(int customerCount, String manifestHash, String status) {}

    private record FixtureCustomer(
            int customerIndex, String customerId, UUID datasetId, int expectedSignalCount
    ) {}

    private static final class Metrics {
        private int evaluatedCustomers;
        private int expectedSignals;
        private int actualSignals;
        private int truePositives;
        private int trueNegatives;
        private int falsePositives;
        private int falseNegatives;

        void add(int expected, int actual) {
            evaluatedCustomers++;
            expectedSignals += expected;
            actualSignals += actual;
            if (expected == 1 && actual == 1) {
                truePositives++;
            } else if (expected == 0 && actual == 0) {
                trueNegatives++;
            } else if (actual > expected) {
                falsePositives++;
            } else {
                falseNegatives++;
            }
        }

        boolean matchesExactly() {
            return falsePositives == 0 && falseNegatives == 0 && expectedSignals == actualSignals;
        }

        BigDecimal precision() {
            return ratio(truePositives, truePositives + falsePositives);
        }

        BigDecimal recall() {
            return ratio(truePositives, truePositives + falseNegatives);
        }

        String material() {
            return evaluatedCustomers + "|" + expectedSignals + "|" + actualSignals + "|"
                    + truePositives + "|" + trueNegatives + "|" + falsePositives + "|"
                    + falseNegatives + "|" + precision() + "|" + recall();
        }

        private BigDecimal ratio(int numerator, int denominator) {
            if (denominator == 0) {
                return BigDecimal.ONE.setScale(6);
            }
            return BigDecimal.valueOf(numerator)
                    .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
        }
    }

    public record QualityReport(
            UUID runId,
            String policyVersion,
            String algorithmVersion,
            String policySnapshotHash,
            String status,
            boolean policyStable,
            int evaluatedCustomerCount,
            int expectedSignalCount,
            int actualSignalCount,
            int truePositiveCount,
            int trueNegativeCount,
            int falsePositiveCount,
            int falseNegativeCount,
            BigDecimal precisionScore,
            BigDecimal recallScore,
            String reportHash,
            OffsetDateTime evaluatedAt,
            boolean replayed
    ) {}
}
