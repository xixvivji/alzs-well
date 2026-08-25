package com.alzswell.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alzswell.fixture.application.SyntheticFixtureGenerationService;
import com.alzswell.fixture.application.SyntheticFixtureGenerationService.GenerationResult;
import com.alzswell.fixture.application.SyntheticFixtureProfile;
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
