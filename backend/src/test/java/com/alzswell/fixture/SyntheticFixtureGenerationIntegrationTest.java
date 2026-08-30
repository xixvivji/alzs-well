package com.alzswell.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alzswell.fixture.application.SyntheticFixtureGenerationService;
import com.alzswell.fixture.application.SyntheticFixtureGenerationService.GenerationResult;
import com.alzswell.fixture.application.SyntheticFixtureProfile;
import com.alzswell.fixture.application.SyntheticFixtureQualityService;
import com.alzswell.common.security.AuditActor;
import com.alzswell.detection.application.DetectionPromotionService;
import com.alzswell.detection.application.SyntheticDatasetService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SyntheticFixtureGenerationIntegrationTest {
    private static final String FIXTURE_VERSION = "synthetic-v3.0.0";
    private static final long SEED = 20_260_825L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired SyntheticFixtureGenerationService service;
    @Autowired SyntheticDatasetService detectionRuns;
    @Autowired DetectionPromotionService promotions;
    @Autowired SyntheticFixtureQualityService qualityService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void generatesDeterministicSmokeDatasetAndReplaysCompletedRun() {
        GenerationResult created = service.generate(
                SyntheticFixtureProfile.SMOKE, FIXTURE_VERSION, SEED, 4, false);

        assertThat(created.status()).isEqualTo("SUCCEEDED");
        assertThat(created.actualCustomerCount()).isEqualTo(10);
        assertThat(created.actualAccountCount()).isEqualTo(20);
        assertThat(created.actualTransactionCount()).isEqualTo(600);
        assertThat(created.manifestHash()).hasSize(64);
        assertThat(created.syntheticData()).isTrue();
        assertThat(created.externalActionsCreated()).isFalse();
        assertThat(created.replayed()).isFalse();

        Integer scenarioCustomers = jdbc.queryForObject("""
                select count(*) from synthetic_fixture_customer where run_id=?
                """, Integer.class, created.runId());
        Integer normalCustomers = jdbc.queryForObject("""
                select count(*) from synthetic_fixture_customer
                 where run_id=? and expected_signal_count=0 and scenario_code='NORMAL'
                """, Integer.class, created.runId());
        Integer unsafeProviders = jdbc.queryForObject("""
                select count(*) from financial_transaction_snapshot t
                  join synthetic_fixture_customer f on f.customer_id=t.customer_id
                 where f.run_id=? and t.provider_mode<>'SYNTHETIC_PROVIDER'
                """, Integer.class, created.runId());
        assertThat(scenarioCustomers).isEqualTo(10);
        assertThat(normalCustomers).isEqualTo(2);
        assertThat(unsafeProviders).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from customer_baseline_snapshot b
                  join synthetic_fixture_customer f on f.customer_id=b.customer_id
                 where f.run_id=?
                """, Integer.class, created.runId())).isEqualTo(10);

        Map<String, Object> fixture = jdbc.queryForMap("""
                select customer_id,dataset_id from synthetic_fixture_customer
                 where run_id=? and scenario_code='DUPLICATE_TRANSFER'
                 order by customer_index limit 1
                """, created.runId());
        String customerId = (String) fixture.get("customer_id");
        UUID datasetId = (UUID) fixture.get("dataset_id");
        var detectionRun = detectionRuns.run(customerId, datasetId, "fixture-promotion-smoke-0001");
        var promotion = promotions.promote(detectionRun.detectionRunId(),
                new AuditActor(null, customerId, null, "STAFF"));
        assertThat(promotion.promotedSignalCount()).isEqualTo(1);
        assertThat(promotion.promotedAlertCount()).isEqualTo(1);

        GenerationResult replayed = service.generate(
                SyntheticFixtureProfile.SMOKE, FIXTURE_VERSION, SEED, 4, false);
        assertThat(replayed.runId()).isEqualTo(created.runId());
        assertThat(replayed.manifestHash()).isEqualTo(created.manifestHash());
        assertThat(replayed.replayed()).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from synthetic_fixture_generation_run where run_id=?",
                Integer.class, created.runId()))
                .isEqualTo(1);
    }

    @Test
    void generatesDemoDatasetInMultipleBatches() {
        GenerationResult result = service.generate(
                SyntheticFixtureProfile.DEMO, FIXTURE_VERSION, SEED + 1, 7, false);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.actualCustomerCount()).isEqualTo(50);
        assertThat(result.actualAccountCount()).isEqualTo(100);
        assertThat(result.actualTransactionCount()).isEqualTo(12_000);
        assertThat(jdbc.queryForObject("""
                select count(*) from synthetic_fixture_customer
                 where run_id=? and expected_signal_count=1
                """, Integer.class, result.runId())).isEqualTo(38);
    }

    @Test
    void evaluatesAllFixtureCustomersAgainstTheActiveDetectionPolicy() {
        GenerationResult result = service.generate(
                SyntheticFixtureProfile.SMOKE, FIXTURE_VERSION, SEED + 2, 4, false);

        SyntheticFixtureQualityService.QualityReport quality = qualityService.evaluate(result.runId());

        assertThat(quality.status()).isEqualTo("PASSED");
        assertThat(quality.policyStable()).isTrue();
        assertThat(quality.evaluatedCustomerCount()).isEqualTo(10);
        assertThat(quality.expectedSignalCount()).isEqualTo(8);
        assertThat(quality.actualSignalCount()).isEqualTo(8);
        assertThat(quality.truePositiveCount()).isEqualTo(8);
        assertThat(quality.trueNegativeCount()).isEqualTo(2);
        assertThat(quality.falsePositiveCount()).isZero();
        assertThat(quality.falseNegativeCount()).isZero();
        assertThat(quality.precisionScore()).isEqualByComparingTo("1.000000");
        assertThat(quality.recallScore()).isEqualByComparingTo("1.000000");
        assertThat(quality.reportHash()).hasSize(64);
        assertThat(quality.replayed()).isFalse();

        SyntheticFixtureQualityService.QualityReport replay = qualityService.evaluate(result.runId());
        assertThat(replay.reportHash()).isEqualTo(quality.reportHash());
        assertThat(replay.replayed()).isTrue();
        assertThat(jdbc.queryForObject("""
                select count(*) from synthetic_fixture_quality_report where run_id=?
                """, Integer.class, result.runId())).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                delete from synthetic_fixture_quality_report where run_id=?
                """, result.runId())).hasMessageContaining("append-only");
    }

    @Test
    void loadProfileStaysWithinTheDailyIntegrationRange() {
        assertThat(SyntheticFixtureProfile.LOAD.customerCount()).isEqualTo(250);
        assertThat(SyntheticFixtureProfile.LOAD.accountCount()).isEqualTo(500);
        assertThat(SyntheticFixtureProfile.LOAD.transactionCount()).isEqualTo(75_000);
    }

    @Test
    void rejectsInvalidGenerationContractBeforeWriting() {
        assertThatThrownBy(() -> service.generate(
                SyntheticFixtureProfile.SMOKE, "fin-mgmt-ab-v2.0.0", SEED, 10, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generate(
                SyntheticFixtureProfile.SMOKE, FIXTURE_VERSION, 0, 10, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generate(
                SyntheticFixtureProfile.SMOKE, FIXTURE_VERSION, SEED, 101, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
