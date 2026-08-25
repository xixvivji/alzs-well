package com.alzswell.fixture.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SyntheticFixtureGenerationService {
    private static final Pattern FIXTURE_VERSION = Pattern.compile("^synthetic-v[0-9]+\\.[0-9]+\\.[0-9]+$");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final boolean syntheticDataOnly;
    private final boolean syntheticProviderOnly;
    private final boolean externalActionsEnabled;

    public SyntheticFixtureGenerationService(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${app.guardrails.synthetic-data-only:true}") boolean syntheticDataOnly,
            @Value("${app.guardrails.synthetic-provider-only:true}") boolean syntheticProviderOnly,
            @Value("${app.guardrails.external-actions-enabled:false}") boolean externalActionsEnabled
    ) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
        this.syntheticDataOnly = syntheticDataOnly;
        this.syntheticProviderOnly = syntheticProviderOnly;
        this.externalActionsEnabled = externalActionsEnabled;
    }

    public GenerationResult generate(
            SyntheticFixtureProfile profile,
            String fixtureVersion,
            long seed,
            int batchSize,
            boolean resumeRunning
    ) {
        validate(profile, fixtureVersion, seed, batchSize);
        String datasetKey = fixtureVersion + ":" + profile.name() + ":" + seed;
        UUID runId = UUID.nameUUIDFromBytes(datasetKey.getBytes(StandardCharsets.UTF_8));
        UUID leaseId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        int inserted = jdbc.update("""
                insert into synthetic_fixture_generation_run(
                    run_id,dataset_key,fixture_version,profile,seed,lease_id,status,
                    expected_customer_count,expected_account_count,expected_transaction_count,started_at
                ) values(?,?,?,?,?,?,'RUNNING',?,?,?,?)
                on conflict(fixture_version,profile,seed) do nothing
                """, runId, datasetKey, fixtureVersion, profile.name(), seed, leaseId,
                profile.customerCount(), profile.accountCount(), profile.transactionCount(), now);

        StoredRun stored = requiredRun(runId);
        if ("SUCCEEDED".equals(stored.status())) {
            return stored.result(true);
        }
        if (inserted == 0) {
            if ("RUNNING".equals(stored.status()) && !resumeRunning) {
                throw new IllegalStateException("동일 합성 데이터 생성 실행이 이미 진행 중입니다.");
            }
            jdbc.update("""
                    update synthetic_fixture_generation_run
                       set status='RUNNING',actual_customer_count=0,actual_account_count=0,
                           actual_transaction_count=0,manifest_hash=null,error_code=null,
                           lease_id=?,started_at=?,completed_at=null
                     where run_id=?
                    """, leaseId, now, runId);
        }

        try {
            for (int first = 1; first <= profile.customerCount(); first += batchSize) {
                int last = Math.min(profile.customerCount(), first + batchSize - 1);
                int batchFirst = first;
                transactions.executeWithoutResult(ignored -> jdbc.queryForObject(
                        "select seed_synthetic_fixture_batch(?,?,?,?,?)",
                        Integer.class, runId, leaseId, batchFirst, last, profile.transactionsPerCustomer()));
            }
            Counts counts = counts(runId);
            if (counts.customers() != profile.customerCount()
                    || counts.accounts() != profile.accountCount()
                    || counts.transactions() != profile.transactionCount()) {
                fail(runId, leaseId, "COUNT_MISMATCH", counts);
                throw new IllegalStateException("합성 데이터 생성 건수가 계약과 일치하지 않습니다.");
            }
            String manifestHash = sha256(String.join("|", List.of(
                    datasetKey,
                    Integer.toString(counts.customers()),
                    Integer.toString(counts.accounts()),
                    Integer.toString(counts.transactions()),
                    "synthetic-provider-only",
                    "external-actions:false"
            )));
            OffsetDateTime completedAt = OffsetDateTime.now(clock);
            int completed = jdbc.update("""
                    update synthetic_fixture_generation_run
                       set status='SUCCEEDED',actual_customer_count=?,actual_account_count=?,
                           actual_transaction_count=?,manifest_hash=?,error_code=null,completed_at=?
                     where run_id=? and lease_id=? and status='RUNNING'
                    """, counts.customers(), counts.accounts(), counts.transactions(), manifestHash,
                    completedAt, runId, leaseId);
            if (completed != 1) {
                throw new IllegalStateException("합성 데이터 생성 실행을 완료 상태로 전환하지 못했습니다.");
            }
            return requiredRun(runId).result(false);
        } catch (RuntimeException failure) {
            StoredRun failedRun = requiredRun(runId);
            if (!"FAILED".equals(failedRun.status()) && !"SUCCEEDED".equals(failedRun.status())) {
                fail(runId, leaseId, failure.getClass().getSimpleName(), counts(runId));
            }
            throw failure;
        }
    }

    private void validate(SyntheticFixtureProfile profile, String fixtureVersion, long seed, int batchSize) {
        if (profile == null || fixtureVersion == null || !FIXTURE_VERSION.matcher(fixtureVersion).matches()) {
            throw new IllegalArgumentException("합성 fixture 버전 형식이 올바르지 않습니다.");
        }
        if (seed <= 0 || batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("합성 seed 또는 batchSize가 허용 범위를 벗어났습니다.");
        }
        if (!syntheticDataOnly || !syntheticProviderOnly || externalActionsEnabled) {
            throw new IllegalStateException("합성 전용 안전 가드레일이 활성화된 환경에서만 생성할 수 있습니다.");
        }
    }

    private Counts counts(UUID runId) {
        return jdbc.queryForObject("""
                select (select count(*) from synthetic_fixture_customer where run_id=?) customer_count,
                       (select count(*) from customer_account_snapshot a
                          join synthetic_fixture_customer f on f.customer_id=a.customer_id
                         where f.run_id=?) account_count,
                       (select count(*) from financial_transaction_snapshot t
                          join synthetic_fixture_customer f on f.customer_id=t.customer_id
                         where f.run_id=?) transaction_count
                """, (rs, rowNumber) -> new Counts(
                        rs.getInt("customer_count"),
                        rs.getInt("account_count"),
                        rs.getInt("transaction_count")
                ), runId, runId, runId);
    }

    private void fail(UUID runId, UUID leaseId, String errorCode, Counts counts) {
        jdbc.update("""
                update synthetic_fixture_generation_run
                   set status='FAILED',actual_customer_count=?,actual_account_count=?,
                       actual_transaction_count=?,manifest_hash=null,error_code=?,completed_at=?
                 where run_id=? and lease_id=? and status='RUNNING'
                """, counts.customers(), counts.accounts(), counts.transactions(),
                normalizeErrorCode(errorCode), OffsetDateTime.now(clock), runId, leaseId);
    }

    private StoredRun requiredRun(UUID runId) {
        return jdbc.queryForObject("""
                select run_id,dataset_key,fixture_version,profile,seed,status,
                       expected_customer_count,expected_account_count,expected_transaction_count,
                       actual_customer_count,actual_account_count,actual_transaction_count,
                       manifest_hash,started_at,completed_at,synthetic_data,external_actions_created
                  from synthetic_fixture_generation_run where run_id=?
                """, (rs, rowNumber) -> new StoredRun(
                        rs.getObject("run_id", UUID.class), rs.getString("dataset_key"),
                        rs.getString("fixture_version"), SyntheticFixtureProfile.valueOf(rs.getString("profile")),
                        rs.getLong("seed"), rs.getString("status"), rs.getInt("expected_customer_count"),
                        rs.getInt("expected_account_count"), rs.getInt("expected_transaction_count"),
                        rs.getInt("actual_customer_count"), rs.getInt("actual_account_count"),
                        rs.getInt("actual_transaction_count"), rs.getString("manifest_hash"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class), rs.getBoolean("synthetic_data"),
                        rs.getBoolean("external_actions_created")
                ), runId);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", impossible);
        }
    }

    private String normalizeErrorCode(String errorCode) {
        String normalized = errorCode == null ? "UNKNOWN" : errorCode.replaceAll("[^A-Za-z0-9_]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 80));
    }

    private record Counts(int customers, int accounts, int transactions) {}

    private record StoredRun(
            UUID runId,
            String datasetKey,
            String fixtureVersion,
            SyntheticFixtureProfile profile,
            long seed,
            String status,
            int expectedCustomerCount,
            int expectedAccountCount,
            int expectedTransactionCount,
            int actualCustomerCount,
            int actualAccountCount,
            int actualTransactionCount,
            String manifestHash,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            boolean syntheticData,
            boolean externalActionsCreated
    ) {
        GenerationResult result(boolean replayed) {
            return new GenerationResult(runId, datasetKey, fixtureVersion, profile, seed, status,
                    expectedCustomerCount, expectedAccountCount, expectedTransactionCount,
                    actualCustomerCount, actualAccountCount, actualTransactionCount, manifestHash,
                    startedAt, completedAt, syntheticData, externalActionsCreated, replayed);
        }
    }

    public record GenerationResult(
            UUID runId,
            String datasetKey,
            String fixtureVersion,
            SyntheticFixtureProfile profile,
            long seed,
            String status,
            int expectedCustomerCount,
            int expectedAccountCount,
            int expectedTransactionCount,
            int actualCustomerCount,
            int actualAccountCount,
            int actualTransactionCount,
            String manifestHash,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            boolean syntheticData,
            boolean externalActionsCreated,
            boolean replayed
    ) {}
}
