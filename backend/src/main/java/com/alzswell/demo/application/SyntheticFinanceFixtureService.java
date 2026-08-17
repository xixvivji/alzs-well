package com.alzswell.demo.application;

import com.alzswell.demo.domain.DemoSession;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SyntheticFinanceFixtureService {

    private static final String CONSENT_ID = "CONSENT_SYN_001";
    private static final String SOURCE_PROVIDER = "SYNTHETIC_PROVIDER";
    private static final String DATA_FRESHNESS = "FIXED_SNAPSHOT";
    private static final String ALGORITHM_VERSION = "baseline-rules-v2.0.0";
    private static final OffsetDateTime SOURCE_UPDATED_AT = OffsetDateTime.parse("2026-07-31T23:59:59Z");
    private static final LocalDate BASELINE_FROM = LocalDate.of(2025, 8, 1);
    private static final LocalDate BASELINE_TO = LocalDate.of(2026, 4, 30);
    private static final LocalDate OBSERVATION_FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate OBSERVATION_TO = LocalDate.of(2026, 7, 31);

    private final JdbcTemplate jdbcTemplate;

    public SyntheticFinanceFixtureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String createSnapshotHash(DemoSession session, String scenarioId, String fixtureVersion) {
        StringBuilder canonical = new StringBuilder()
                .append("fixtureVersion=").append(fixtureVersion).append('\n')
                .append("scenarioId=").append(scenarioId).append('\n')
                .append("scenarioSeed=").append(Long.toUnsignedString(session.getScenarioSeed())).append('\n')
                .append("customerId=").append(DemoSessionService.CUSTOMER_ID).append('\n');
        accountSeeds().forEach(seed -> canonical.append("account|").append(seed.canonical()).append('\n'));
        transactionSeeds().forEach(seed -> canonical.append("transaction|").append(seed.canonical()).append('\n'));
        interactionSeeds().forEach(seed -> canonical.append("interaction|").append(seed.canonical()).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    public void restore(DemoSession session) {
        List<TransactionSeed> transactions = transactionSeeds();
        List<InteractionSeed> interactions = interactionSeeds();
        insertConsent(session);
        insertConnections(session);
        insertConnectionScopes(session);
        insertAccounts(session);
        insertTransactions(session, transactions);
        insertInteractions(session, interactions);
        insertDerivedSignalsAndBaselines(session, transactions, interactions);
        insertFinancialProfile(session);
        insertAssetTrend(session);
    }

    public boolean isComplete(DemoSession session) {
        try {
            Integer complete = jdbcTemplate.queryForObject("""
                select case when
                    (select count(*) from synthetic_connection where demo_session_id = ? and demo_run_id = ?) = c.expected_connection_count
                    and (select count(*) from synthetic_account where demo_session_id = ? and demo_run_id = ?) = c.expected_account_count
                    and (select count(*) from synthetic_transaction where demo_session_id = ? and demo_run_id = ?) = c.expected_transaction_count
                    and (select count(*) from synthetic_baseline where demo_session_id = ? and demo_run_id = ?) = c.expected_baseline_count
                    and (select count(*) from synthetic_asset_trend where demo_session_id = ? and demo_run_id = ?) = c.expected_trend_count
                    and (select count(*) from synthetic_interaction_event where demo_session_id = ? and demo_run_id = ?) = c.expected_interaction_count
                    and (select count(*) from synthetic_signal where demo_session_id = ? and demo_run_id = ?) = c.expected_signal_count
                    and (select count(*) from synthetic_consent where demo_session_id = ? and demo_run_id = ?) = 1
                    and (select count(*) from synthetic_financial_profile where demo_session_id = ? and demo_run_id = ?) = 1
                    and not exists (
                        select 1 from (
                            select snapshot_hash from synthetic_consent where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_connection where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_account where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_transaction where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_baseline where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_financial_profile where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_asset_trend where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_interaction_event where demo_session_id = ? and demo_run_id = ?
                            union all select snapshot_hash from synthetic_signal where demo_session_id = ? and demo_run_id = ?
                        ) snapshot where snapshot_hash <> ?
                    ) then 1 else 0 end
                  from demo_fixture_catalog c
                 where c.scenario_id = ? and c.enabled = true
                """, Integer.class,
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSessionId(), session.getDemoRunId(),
                session.getSessionId(), session.getDemoRunId(), session.getSnapshotHash(), session.getScenarioId());
            return Integer.valueOf(1).equals(complete);
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private void insertConsent(DemoSession session) {
        jdbcTemplate.update("""
                insert into synthetic_consent (
                    demo_session_id, demo_run_id, consent_id, customer_id, purpose, granted,
                    granted_at, expires_at, revocable, trusted_contact_granted, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, session.getSessionId(), session.getDemoRunId(), CONSENT_ID,
                DemoSessionService.CUSTOMER_ID, "FINANCIAL_LIFE_CHANGE_ANALYSIS", true,
                session.getCreatedAt(), session.getExpiresAt(), true, false, session.getSnapshotHash());
    }

    private void insertConnections(DemoSession session) {
        String sql = """
                insert into synthetic_connection (
                    demo_session_id, demo_run_id, connection_id, customer_id, institution_id, institution_name,
                    institution_type, connection_status, source_provider, source_updated_at,
                    data_freshness, consent_id, display_order, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, List.of(
                connection(session, "CONN_SYN_BANK_001", "SYNTHETIC_BANK", "안심은행", "BANK", 1),
                connection(session, "CONN_SYN_SECURITIES_001", "SYNTHETIC_SECURITIES", "안심증권", "SECURITIES", 2)
        ));
    }

    private Object[] connection(DemoSession session, String id, String institutionId,
                                String name, String type, int order) {
        return new Object[]{session.getSessionId(), session.getDemoRunId(), id,
                DemoSessionService.CUSTOMER_ID, institutionId, name, type, "CONNECTED_SYNTHETIC",
                SOURCE_PROVIDER, SOURCE_UPDATED_AT, DATA_FRESHNESS, CONSENT_ID, order, session.getSnapshotHash()};
    }

    private void insertConnectionScopes(DemoSession session) {
        String sql = """
                insert into synthetic_connection_scope (
                    demo_session_id, demo_run_id, connection_id, scope_code, display_order
                ) values (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, List.of(
                scope(session, "CONN_SYN_BANK_001", "ACCOUNT", 1),
                scope(session, "CONN_SYN_BANK_001", "BALANCE", 2),
                scope(session, "CONN_SYN_BANK_001", "TRANSACTION", 3),
                scope(session, "CONN_SYN_BANK_001", "RECURRING_PAYMENT", 4),
                scope(session, "CONN_SYN_SECURITIES_001", "INVESTMENT_ACCOUNT", 1),
                scope(session, "CONN_SYN_SECURITIES_001", "POSITION", 2),
                scope(session, "CONN_SYN_SECURITIES_001", "TRADE_HISTORY", 3)
        ));
    }

    private Object[] scope(DemoSession session, String connectionId, String code, int order) {
        return new Object[]{session.getSessionId(), session.getDemoRunId(), connectionId, code, order};
    }

    private void insertAccounts(DemoSession session) {
        String sql = """
                insert into synthetic_account (
                    demo_session_id, demo_run_id, account_id, customer_id, institution_id, account_type,
                    display_name, masked_account_number, current_balance, available_balance,
                    currency, connection_id, consent_id, source_provider, source_updated_at,
                    data_freshness, display_order, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        List<Object[]> rows = accountSeeds().stream().map(seed -> new Object[]{
                session.getSessionId(), session.getDemoRunId(), seed.accountId(), DemoSessionService.CUSTOMER_ID,
                seed.institutionId(), seed.accountType(), seed.displayName(), seed.maskedNumber(),
                seed.balance(), seed.balance(), "KRW", seed.connectionId(), CONSENT_ID, SOURCE_PROVIDER,
                SOURCE_UPDATED_AT, DATA_FRESHNESS, seed.order(), session.getSnapshotHash()
        }).toList();
        jdbcTemplate.batchUpdate(sql, rows);
    }

    private List<AccountSeed> accountSeeds() {
        return List.of(
                new AccountSeed("SYN_ACCOUNT_BANK_001", "SYNTHETIC_BANK", "DEMAND_DEPOSIT",
                        "생활비 통장", "***-***-1234", new BigDecimal("9250000"), "CONN_SYN_BANK_001", 1),
                new AccountSeed("SYN_ACCOUNT_BANK_002", "SYNTHETIC_BANK", "SAVINGS",
                        "정기생활 저축", "***-***-5678", new BigDecimal("12500000"), "CONN_SYN_BANK_001", 2),
                new AccountSeed("SYN_ACCOUNT_BANK_003", "SYNTHETIC_BANK", "DEMAND_DEPOSIT",
                        "비상금 통장", "***-***-9012", new BigDecimal("7000000"), "CONN_SYN_BANK_001", 3),
                new AccountSeed("SYN_ACCOUNT_SECURITIES_001", "SYNTHETIC_SECURITIES", "INVESTMENT",
                        "합성 투자계좌", "***-***-3456", new BigDecimal("19500000"), "CONN_SYN_SECURITIES_001", 4)
        );
    }

    private void insertTransactions(DemoSession session, List<TransactionSeed> seeds) {
        String sql = """
                insert into synthetic_transaction (
                    demo_session_id, demo_run_id, transaction_id, account_id, occurred_at, posted_at,
                    direction, transaction_type, amount, currency, balance_after,
                    counterparty_display_name, category, transaction_status,
                    source_provider, data_freshness, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        List<Object[]> rows = seeds.stream().map(seed -> new Object[]{
                session.getSessionId(), session.getDemoRunId(), seed.transactionId(), seed.accountId(),
                seed.occurredAt(), seed.occurredAt().plusSeconds(3), seed.direction(), seed.type(),
                seed.amount(), "KRW", seed.balanceAfter(), seed.counterparty(), seed.category(),
                "POSTED", SOURCE_PROVIDER, DATA_FRESHNESS, session.getSnapshotHash()
        }).toList();
        jdbcTemplate.batchUpdate(sql, rows);
    }

    private List<TransactionSeed> transactionSeeds() {
        List<TransactionSeed> rows = new ArrayList<>();
        addRecurringSeries(rows, "UTILITY", "합성공과금", "100000", 2, Set.of(YearMonth.of(2026, 6)));
        addRecurringSeries(rows, "INSURANCE", "합성보험료", "120000", 10, Set.of(YearMonth.of(2026, 7)));
        addRecurringSeries(rows, "TELECOM", "합성통신비", "80000", 20, Set.of(YearMonth.of(2026, 7)));
        rows.add(transaction("TX_SALARY_071", "SYN_ACCOUNT_BANK_001", "2026-07-01T00:10:00Z",
                "IN", "DEPOSIT", "3200000", "28250000", "합성급여", "INCOME"));
        rows.add(transaction("TX_DUP_A_001", "SYN_ACCOUNT_BANK_001", "2026-07-10T10:00:00Z",
                "OUT", "TRANSFER_OUT", "1200000", "17050000", "합성수취인 A", "LIVING"));
        rows.add(transaction("TX_DUP_A_002", "SYN_ACCOUNT_BANK_001", "2026-07-10T10:07:00Z",
                "OUT", "TRANSFER_OUT", "1200000", "15850000", "합성수취인 A", "LIVING"));
        rows.add(transaction("TX_DUP_B_001", "SYN_ACCOUNT_BANK_001", "2026-07-20T09:00:00Z",
                "OUT", "TRANSFER_OUT", "850000", "15000000", "합성수취인 B", "FAMILY_SUPPORT"));
        rows.add(transaction("TX_DUP_B_002", "SYN_ACCOUNT_BANK_001", "2026-07-20T09:08:00Z",
                "OUT", "TRANSFER_OUT", "850000", "14150000", "합성수취인 B", "FAMILY_SUPPORT"));
        rows.add(transaction("TX_LIVING_072", "SYN_ACCOUNT_BANK_001", "2026-07-20T12:00:00Z",
                "OUT", "CARD_PAYMENT", "500000", "13650000", "합성생활비", "LIVING"));
        rows.add(transaction("TX_GROCERY_071", "SYN_ACCOUNT_BANK_001", "2026-07-22T12:00:00Z",
                "OUT", "CARD_PAYMENT", "180000", "13470000", "합성식료품", "LIVING"));
        rows.add(transaction("TX_EMERGENCY_071", "SYN_ACCOUNT_BANK_003", "2026-07-18T05:00:00Z",
                "OUT", "TRANSFER_OUT", "120000", "7000000", "합성생활이체", "LIVING"));
        rows.add(transaction("TX_DIVIDEND_071", "SYN_ACCOUNT_SECURITIES_001", "2026-07-25T06:00:00Z",
                "IN", "INVESTMENT_INCOME", "180000", "19500000", "합성배당", "INVESTMENT"));
        return List.copyOf(rows);
    }

    private void addRecurringSeries(List<TransactionSeed> rows, String code, String payee,
                                    String amount, int day, Set<YearMonth> missingMonths) {
        for (int index = 0; index < 12; index++) {
            YearMonth month = YearMonth.of(2025, 8).plusMonths(index);
            if (missingMonths.contains(month)) {
                continue;
            }
            rows.add(transaction("TX_REC_" + code + "_" + month.toString().replace("-", ""),
                    "SYN_ACCOUNT_BANK_002",
                    month.atDay(day).atTime(1, 0).atOffset(ZoneOffset.UTC).toString(),
                    "OUT", "AUTOPAY", amount, "12500000", payee, "RECURRING_LIVING"));
        }
    }

    private TransactionSeed transaction(String id, String accountId, String occurredAt, String direction,
                                        String type, String amount, String balanceAfter,
                                        String counterparty, String category) {
        return new TransactionSeed(id, accountId, OffsetDateTime.parse(occurredAt), direction, type,
                new BigDecimal(amount), new BigDecimal(balanceAfter), counterparty, category);
    }

    private void insertInteractions(DemoSession session, List<InteractionSeed> seeds) {
        String sql = """
                insert into synthetic_interaction_event (
                    demo_session_id, demo_run_id, interaction_id, customer_id, occurred_at,
                    event_type, subject_reference, source_provider, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, seeds.stream().map(seed -> new Object[]{
                session.getSessionId(), session.getDemoRunId(), seed.interactionId(),
                DemoSessionService.CUSTOMER_ID, seed.occurredAt(), seed.eventType(),
                seed.subjectReference(), SOURCE_PROVIDER, session.getSnapshotHash()
        }).toList());
    }

    private List<InteractionSeed> interactionSeeds() {
        List<InteractionSeed> rows = new ArrayList<>();
        rows.add(new InteractionSeed("INT_CONFIRM_BASE_001", OffsetDateTime.parse("2026-04-15T10:00:00Z"),
                "TRANSACTION_CONFIRMATION_VIEW", "TX_BASELINE_REFERENCE"));
        OffsetDateTime start = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        IntStream.rangeClosed(1, 7).forEach(index -> rows.add(new InteractionSeed(
                "INT_CONFIRM_OBS_00" + index, start.plusMinutes((long) (index - 1) * 9),
                "TRANSACTION_CONFIRMATION_VIEW", "TX_DUP_A_001")));
        return List.copyOf(rows);
    }

    private void insertDerivedSignalsAndBaselines(
            DemoSession session,
            List<TransactionSeed> transactions,
            List<InteractionSeed> interactions
    ) {
        SignalCounts counts = detectSignals(transactions, interactions);
        if (counts.missedRecurring() != 3 || counts.duplicateTransfers() != 2
                || counts.repeatedConfirmations() != 7) {
            throw new IllegalStateException("고정 fixture의 3·2·7 신호 불변조건이 깨졌습니다: " + counts);
        }

        String baselineSql = """
                insert into synthetic_baseline (
                    demo_session_id, demo_run_id, baseline_id, customer_id, feature_code, baseline_value,
                    current_value, unit, readiness, comparison_text, algorithm_version,
                    calculated_at, baseline_from, baseline_to, observation_from, observation_to,
                    display_order, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(baselineSql, List.of(
                baseline(session, "BASELINE_MISSED_RECURRING_001", "MISSED_RECURRING_COUNT",
                        "0", counts.missedRecurring(), "COUNT",
                        "최근 60일 동안 평소 반복되던 정기납부 3건이 보이지 않습니다.", 1),
                baseline(session, "BASELINE_DUPLICATE_TRANSFER_001", "DUPLICATE_TRANSFER_COUNT",
                        Integer.toString(counts.baselineDuplicateTransfers()), counts.duplicateTransfers(), "COUNT",
                        "같은 수취인과 금액으로 10분 이내 반복된 송금이 2건 확인됐습니다.", 2),
                baseline(session, "BASELINE_REPEATED_CONFIRMATION_001", "REPEATED_CONFIRMATION_COUNT",
                        Integer.toString(counts.baselineRepeatedConfirmations()), counts.repeatedConfirmations(), "COUNT",
                        "1시간 안에 동일 거래 확인 화면을 7회 조회했습니다.", 3)
        ));

        String reasonSql = """
                insert into synthetic_baseline_reason (
                    demo_session_id, demo_run_id, baseline_id, reason_code, display_order
                ) values (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(reasonSql, List.of(
                reason(session, "BASELINE_MISSED_RECURRING_001", "MISSED_RECURRING", 1),
                reason(session, "BASELINE_DUPLICATE_TRANSFER_001", "DUPLICATE_TRANSFER", 1),
                reason(session, "BASELINE_REPEATED_CONFIRMATION_001", "REPEATED_CONFIRMATION", 1)
        ));

        String signalSql = """
                insert into synthetic_signal (
                    demo_session_id, demo_run_id, alert_id, reason_code, observed_count,
                    window_seconds, algorithm_version, detected_at, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(signalSql, List.of(
                signal(session, "MISSED_RECURRING", counts.missedRecurring(), 60 * 24 * 60 * 60),
                signal(session, "DUPLICATE_TRANSFER", counts.duplicateTransfers(), 10 * 60),
                signal(session, "REPEATED_CONFIRMATION", counts.repeatedConfirmations(), 60 * 60)
        ));
    }

    private SignalCounts detectSignals(List<TransactionSeed> transactions, List<InteractionSeed> interactions) {
        Map<RecurringKey, Set<YearMonth>> baselineSchedules = transactions.stream()
                .filter(this::isRecurring)
                .filter(row -> within(row.occurredAt().toLocalDate(), BASELINE_FROM, BASELINE_TO))
                .collect(Collectors.groupingBy(this::recurringKey, LinkedHashMap::new,
                        Collectors.mapping(row -> YearMonth.from(row.occurredAt()), Collectors.toSet())));
        List<RecurringKey> stableSchedules = baselineSchedules.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 6)
                .map(Map.Entry::getKey)
                .toList();
        LocalDate sixtyDayStart = OBSERVATION_TO.minusDays(59);
        int missed = 0;
        for (RecurringKey schedule : stableSchedules) {
            YearMonth month = YearMonth.from(sixtyDayStart);
            while (!month.isAfter(YearMonth.from(OBSERVATION_TO))) {
                LocalDate expected = month.atDay(schedule.dayOfMonth());
                if (!expected.isBefore(sixtyDayStart) && !expected.isAfter(OBSERVATION_TO)) {
                    boolean present = transactions.stream().filter(this::isRecurring)
                            .anyMatch(row -> recurringKey(row).equals(schedule)
                                    && row.occurredAt().toLocalDate().equals(expected));
                    if (!present) {
                        missed++;
                    }
                }
                month = month.plusMonths(1);
            }
        }
        int baselineDuplicates = duplicateTransferCount(transactions, BASELINE_FROM, BASELINE_TO);
        int currentDuplicates = duplicateTransferCount(transactions, OBSERVATION_FROM, OBSERVATION_TO);
        int baselineConfirmations = maxConfirmationCount(interactions, BASELINE_FROM, BASELINE_TO);
        int currentConfirmations = maxConfirmationCount(interactions, OBSERVATION_FROM, OBSERVATION_TO);
        return new SignalCounts(missed, currentDuplicates, currentConfirmations,
                baselineDuplicates, baselineConfirmations);
    }

    private int duplicateTransferCount(List<TransactionSeed> rows, LocalDate from, LocalDate to) {
        Map<TransferKey, List<TransactionSeed>> groups = rows.stream()
                .filter(row -> "TRANSFER_OUT".equals(row.type()))
                .filter(row -> within(row.occurredAt().toLocalDate(), from, to))
                .collect(Collectors.groupingBy(row -> new TransferKey(
                        row.accountId(), row.counterparty(), row.amount())));
        int duplicates = 0;
        for (List<TransactionSeed> group : groups.values()) {
            List<TransactionSeed> sorted = group.stream()
                    .sorted(Comparator.comparing(TransactionSeed::occurredAt)).toList();
            for (int index = 1; index < sorted.size(); index++) {
                long seconds = Duration.between(sorted.get(index - 1).occurredAt(),
                        sorted.get(index).occurredAt()).toSeconds();
                if (seconds >= 0 && seconds <= 10 * 60) {
                    duplicates++;
                }
            }
        }
        return duplicates;
    }

    private int maxConfirmationCount(List<InteractionSeed> rows, LocalDate from, LocalDate to) {
        List<OffsetDateTime> times = rows.stream()
                .filter(row -> "TRANSACTION_CONFIRMATION_VIEW".equals(row.eventType()))
                .filter(row -> within(row.occurredAt().toLocalDate(), from, to))
                .map(InteractionSeed::occurredAt).sorted().toList();
        int max = 0;
        int left = 0;
        for (int right = 0; right < times.size(); right++) {
            while (Duration.between(times.get(left), times.get(right)).toSeconds() > 60 * 60) {
                left++;
            }
            max = Math.max(max, right - left + 1);
        }
        return max;
    }

    private boolean isRecurring(TransactionSeed row) {
        return "AUTOPAY".equals(row.type()) && "RECURRING_LIVING".equals(row.category());
    }

    private RecurringKey recurringKey(TransactionSeed row) {
        return new RecurringKey(row.accountId(), row.counterparty(), row.amount(), row.occurredAt().getDayOfMonth());
    }

    private boolean within(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private Object[] baseline(DemoSession session, String id, String featureCode, String baselineValue,
                              int currentValue, String unit, String text, int order) {
        return new Object[]{session.getSessionId(), session.getDemoRunId(), id,
                DemoSessionService.CUSTOMER_ID, featureCode, baselineValue, Integer.toString(currentValue),
                unit, "READY", text, ALGORITHM_VERSION, OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                BASELINE_FROM, BASELINE_TO, OBSERVATION_FROM, OBSERVATION_TO, order, session.getSnapshotHash()};
    }

    private Object[] reason(DemoSession session, String baselineId, String reasonCode, int order) {
        return new Object[]{session.getSessionId(), session.getDemoRunId(), baselineId, reasonCode, order};
    }

    private Object[] signal(DemoSession session, String reasonCode, int count, int windowSeconds) {
        return new Object[]{session.getSessionId(), session.getDemoRunId(), DemoSessionService.ALERT_ID,
                reasonCode, count, windowSeconds, ALGORITHM_VERSION,
                OffsetDateTime.parse("2026-08-01T00:00:00Z"), session.getSnapshotHash()};
    }

    private void insertFinancialProfile(DemoSession session) {
        jdbcTemplate.update("""
                insert into synthetic_financial_profile (
                    demo_session_id, demo_run_id, customer_id, as_of_date, period_from, period_to,
                    monthly_income, monthly_expense, upcoming_obligations, liabilities,
                    open_alert_count, change_summary, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, session.getSessionId(), session.getDemoRunId(), DemoSessionService.CUSTOMER_ID,
                LocalDate.of(2026, 7, 31), BASELINE_FROM, OBSERVATION_TO,
                new BigDecimal("3200000"), new BigDecimal("2140000"), new BigDecimal("430000"),
                BigDecimal.ZERO, 1, "정기납부 누락 3건, 중복송금 2건, 반복확인 7회를 확인해 주세요.",
                session.getSnapshotHash());
    }

    private void insertAssetTrend(DemoSession session) {
        String sql = """
                insert into synthetic_asset_trend (
                    demo_session_id, demo_run_id, customer_id, trend_month, total_assets, snapshot_hash
                ) values (?, ?, ?, ?, ?, ?)
                """;
        List<String> amounts = List.of("64000000", "64500000", "65000000", "65500000", "66000000", "66500000",
                "67000000", "67200000", "67500000", "67800000", "67180000", "48250000");
        jdbcTemplate.batchUpdate(sql, IntStream.range(0, amounts.size()).mapToObj(index -> new Object[]{
                session.getSessionId(), session.getDemoRunId(), DemoSessionService.CUSTOMER_ID,
                LocalDate.of(2025, 8, 1).plusMonths(index), new BigDecimal(amounts.get(index)),
                session.getSnapshotHash()
        }).toList());
    }

    private record AccountSeed(String accountId, String institutionId, String accountType,
                               String displayName, String maskedNumber, BigDecimal balance,
                               String connectionId, int order) {
        String canonical() {
            return String.join("|", accountId, institutionId, accountType, maskedNumber,
                    balance.toPlainString(), connectionId, Integer.toString(order));
        }
    }

    private record TransactionSeed(String transactionId, String accountId, OffsetDateTime occurredAt,
                                   String direction, String type, BigDecimal amount, BigDecimal balanceAfter,
                                   String counterparty, String category) {
        String canonical() {
            return String.join("|", transactionId, accountId, occurredAt.toString(), direction, type,
                    amount.toPlainString(), balanceAfter.toPlainString(), counterparty, category);
        }
    }

    private record InteractionSeed(String interactionId, OffsetDateTime occurredAt,
                                   String eventType, String subjectReference) {
        String canonical() {
            return String.join("|", interactionId, occurredAt.toString(), eventType, subjectReference);
        }
    }

    private record RecurringKey(String accountId, String counterparty, BigDecimal amount, int dayOfMonth) {
    }

    private record TransferKey(String accountId, String counterparty, BigDecimal amount) {
    }

    private record SignalCounts(int missedRecurring, int duplicateTransfers, int repeatedConfirmations,
                                int baselineDuplicateTransfers, int baselineRepeatedConfirmations) {
    }
}
