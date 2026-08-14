package com.alzswell.demo.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import com.alzswell.demo.api.AccountListResponse;
import com.alzswell.demo.api.BaselineListResponse;
import com.alzswell.demo.api.ConnectionConsentSummaryResponse;
import com.alzswell.demo.api.DemoErrorCode;
import com.alzswell.demo.api.FinancialSummaryResponse;
import com.alzswell.demo.api.MoneyAmount;
import com.alzswell.demo.api.ProtectionActionListResponse;
import com.alzswell.demo.api.SyntheticDataProvenance;
import com.alzswell.demo.api.TransactionListResponse;
import com.alzswell.demo.domain.DemoSession;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SyntheticFinanceQueryService {

    private static final String KRW = "KRW";
    private final JdbcTemplate jdbcTemplate;
    private final DemoSessionService sessionService;

    public SyntheticFinanceQueryService(JdbcTemplate jdbcTemplate, DemoSessionService sessionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionService = sessionService;
    }

    public ConnectionConsentSummaryResponse connections(UUID sessionId, String customerId) {
        DemoSession session = sessionService.requireFinancialFixture(sessionId, customerId);
        UUID demoRunId = session.getDemoRunId();
        List<ConnectionConsentSummaryResponse.ConnectionItem> items = jdbcTemplate.query("""
                select connection_id, institution_id, institution_name, institution_type,
                       connection_status, source_provider, source_updated_at, data_freshness, consent_id
                  from synthetic_connection
                 where demo_session_id = ? and demo_run_id = ? and customer_id = ? order by display_order
                """, (rs, row) -> new ConnectionConsentSummaryResponse.ConnectionItem(
                rs.getString("connection_id"), rs.getString("institution_id"),
                rs.getString("institution_name"), rs.getString("institution_type"),
                rs.getString("connection_status"), rs.getString("source_provider"),
                rs.getObject("source_updated_at", OffsetDateTime.class), rs.getString("data_freshness"),
                rs.getString("consent_id"), scopes(sessionId, demoRunId, rs.getString("connection_id"))
        ), sessionId, demoRunId, customerId);
        ConnectionConsentSummaryResponse.ConsentSummary consent = jdbcTemplate.queryForObject("""
                select purpose, granted, granted_at, expires_at, revocable, trusted_contact_granted
                  from synthetic_consent where demo_session_id = ? and demo_run_id = ? and customer_id = ?
                """, (rs, row) -> new ConnectionConsentSummaryResponse.ConsentSummary(
                rs.getString("purpose"), rs.getBoolean("granted"),
                rs.getObject("granted_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class), rs.getBoolean("revocable"),
                rs.getBoolean("trusted_contact_granted")
        ), sessionId, demoRunId, customerId);
        return new ConnectionConsentSummaryResponse(items, consent, provenance(sessionId, demoRunId, customerId));
    }

    private List<String> scopes(UUID sessionId, UUID demoRunId, String connectionId) {
        return jdbcTemplate.queryForList("""
                select scope_code from synthetic_connection_scope
                 where demo_session_id = ? and demo_run_id = ? and connection_id = ? order by display_order
                """, String.class, sessionId, demoRunId, connectionId);
    }

    public AccountListResponse accounts(UUID sessionId, String customerId) {
        DemoSession session = sessionService.requireFinancialFixture(sessionId, customerId);
        UUID demoRunId = session.getDemoRunId();
        List<AccountListResponse.AccountItem> items = jdbcTemplate.query("""
                select account_id, institution_id, account_type, display_name, masked_account_number,
                       current_balance, available_balance, currency, connection_id, consent_id,
                       source_provider, source_updated_at, data_freshness
                  from synthetic_account
                 where demo_session_id = ? and demo_run_id = ? and customer_id = ? order by display_order
                """, (rs, row) -> new AccountListResponse.AccountItem(
                rs.getString("account_id"), rs.getString("institution_id"), rs.getString("account_type"),
                rs.getString("display_name"), rs.getString("masked_account_number"),
                money(rs, "current_balance"), money(rs, "available_balance"),
                rs.getString("connection_id"), rs.getString("consent_id"),
                rs.getString("source_provider"), rs.getObject("source_updated_at", OffsetDateTime.class),
                rs.getString("data_freshness")
        ), sessionId, demoRunId, customerId);
        return new AccountListResponse(customerId, items, null, false, provenance(sessionId, demoRunId, customerId));
    }

    public TransactionListResponse transactions(UUID sessionId, String accountId, LocalDate from,
                                                LocalDate to, String direction, String category,
                                                String cursor, int limit) {
        DemoSession session = sessionService.requireFinancialFixture(sessionId);
        UUID demoRunId = session.getDemoRunId();
        validateFilters(from, to, direction, limit);
        List<String> accountCustomers = jdbcTemplate.queryForList("""
                select customer_id from synthetic_account
                 where demo_session_id = ? and demo_run_id = ? and account_id = ?
                """, String.class, sessionId, demoRunId, accountId);
        if (accountCustomers.isEmpty()) {
            throw new BusinessException(DemoErrorCode.SYNTHETIC_ACCOUNT_NOT_FOUND);
        }

        List<Object> args = new ArrayList<>(List.of(sessionId, demoRunId, accountId));
        StringBuilder sql = new StringBuilder("""
                select transaction_id, occurred_at, posted_at, direction, transaction_type, amount,
                       currency, balance_after, counterparty_display_name, category,
                       transaction_status, source_provider, data_freshness
                  from synthetic_transaction where demo_session_id = ? and demo_run_id = ? and account_id = ?
                """);
        if (from != null) { sql.append(" and occurred_at >= ?"); args.add(from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)); }
        if (to != null) { sql.append(" and occurred_at < ?"); args.add(to.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)); }
        if (direction != null) { sql.append(" and direction = ?"); args.add(direction); }
        if (category != null) { sql.append(" and category = ?"); args.add(category); }
        Cursor decoded = decodeCursor(cursor);
        if (decoded != null) {
            sql.append(" and (occurred_at < ? or (occurred_at = ? and transaction_id < ?))");
            args.add(decoded.occurredAt()); args.add(decoded.occurredAt()); args.add(decoded.transactionId());
        }
        sql.append(" order by occurred_at desc, transaction_id desc limit ?");
        args.add(limit + 1);
        List<TransactionListResponse.TransactionItem> rows = jdbcTemplate.query(sql.toString(),
                (rs, row) -> new TransactionListResponse.TransactionItem(
                        rs.getString("transaction_id"), rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getObject("posted_at", OffsetDateTime.class), rs.getString("direction"),
                        rs.getString("transaction_type"), decimal(rs, "amount"), rs.getString("currency"),
                        decimal(rs, "balance_after"), rs.getString("counterparty_display_name"),
                        rs.getString("category"), rs.getString("transaction_status"),
                        rs.getString("source_provider"), rs.getString("data_freshness")), args.toArray());
        boolean hasMore = rows.size() > limit;
        List<TransactionListResponse.TransactionItem> items = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? encodeCursor(items.get(items.size() - 1)) : null;
        return new TransactionListResponse(accountId, List.copyOf(items), next, hasMore,
                provenance(sessionId, demoRunId, accountCustomers.getFirst()));
    }

    public BaselineListResponse baselines(UUID sessionId, String customerId) {
        DemoSession session = sessionService.requireFinancialFixture(sessionId, customerId);
        UUID demoRunId = session.getDemoRunId();
        List<BaselineListResponse.BaselineItem> items = jdbcTemplate.query("""
                select baseline_id, feature_code, baseline_value, current_value, unit, readiness,
                       comparison_text, algorithm_version, calculated_at
                  from synthetic_baseline
                 where demo_session_id = ? and demo_run_id = ? and customer_id = ? order by display_order
                """, (rs, row) -> new BaselineListResponse.BaselineItem(
                rs.getString("baseline_id"), rs.getString("feature_code"), rs.getString("baseline_value"),
                rs.getString("current_value"), rs.getString("unit"), rs.getString("readiness"),
                rs.getString("comparison_text"), reasons(sessionId, demoRunId, rs.getString("baseline_id")),
                rs.getString("algorithm_version"), rs.getObject("calculated_at", OffsetDateTime.class)
        ), sessionId, demoRunId, customerId);
        List<LocalDate> periods = jdbcTemplate.query("""
                select baseline_from, baseline_to, observation_from, observation_to
                  from synthetic_baseline where demo_session_id = ? and demo_run_id = ? and customer_id = ?
                 order by display_order limit 1
                """, rs -> {
            rs.next();
            return List.of(rs.getObject("baseline_from", LocalDate.class),
                    rs.getObject("baseline_to", LocalDate.class),
                    rs.getObject("observation_from", LocalDate.class),
                    rs.getObject("observation_to", LocalDate.class));
        }, sessionId, demoRunId, customerId);
        return new BaselineListResponse(customerId,
                new BaselineListResponse.DatePeriod(periods.get(0), periods.get(1)),
                new BaselineListResponse.DatePeriod(periods.get(2), periods.get(3)), items,
                provenance(sessionId, demoRunId, customerId));
    }

    private List<String> reasons(UUID sessionId, UUID demoRunId, String baselineId) {
        return jdbcTemplate.queryForList("""
                select reason_code from synthetic_baseline_reason
                 where demo_session_id = ? and demo_run_id = ? and baseline_id = ? order by display_order
                """, String.class, sessionId, demoRunId, baselineId);
    }

    public FinancialSummaryResponse financialSummary(UUID sessionId, String customerId) {
        DemoSession session = sessionService.requireFinancialFixture(sessionId, customerId);
        UUID demoRunId = session.getDemoRunId();
        Profile profile = jdbcTemplate.queryForObject("""
                select as_of_date, period_from, period_to, monthly_income, monthly_expense,
                       upcoming_obligations, liabilities, open_alert_count, change_summary
                  from synthetic_financial_profile where demo_session_id = ? and demo_run_id = ? and customer_id = ?
                """, (rs, row) -> new Profile(rs.getObject("as_of_date", LocalDate.class),
                rs.getObject("period_from", LocalDate.class), rs.getObject("period_to", LocalDate.class),
                decimal(rs, "monthly_income"), decimal(rs, "monthly_expense"),
                decimal(rs, "upcoming_obligations"), decimal(rs, "liabilities"),
                rs.getInt("open_alert_count"), rs.getString("change_summary")), sessionId, demoRunId, customerId);
        List<String> totals = jdbcTemplate.query("""
                select coalesce(sum(current_balance), 0) total,
                       coalesce(sum(current_balance) filter (where account_type <> 'INVESTMENT'), 0) deposits,
                       coalesce(sum(current_balance) filter (where account_type = 'INVESTMENT'), 0) investments
                  from synthetic_account where demo_session_id = ? and demo_run_id = ? and customer_id = ?
                """, rs -> { rs.next(); return List.of(decimal(rs, "total"), decimal(rs, "deposits"), decimal(rs, "investments")); },
                sessionId, demoRunId, customerId);
        List<FinancialSummaryResponse.TrendItem> trend = jdbcTemplate.query("""
                select trend_month, total_assets from synthetic_asset_trend
                 where demo_session_id = ? and demo_run_id = ? and customer_id = ? order by trend_month
                """, (rs, row) -> new FinancialSummaryResponse.TrendItem(
                YearMonth.from(rs.getObject("trend_month", LocalDate.class)), amount(decimal(rs, "total_assets"))),
                sessionId, demoRunId, customerId);
        return new FinancialSummaryResponse(customerId, profile.asOf(),
                new FinancialSummaryResponse.DatePeriod(profile.from(), profile.to()),
                new FinancialSummaryResponse.Assets(amount(totals.get(0)), amount(totals.get(1)),
                        amount(totals.get(2)), amount(profile.liabilities())),
                new FinancialSummaryResponse.CashFlow(amount(profile.income()), amount(profile.expense()),
                        amount(profile.upcoming())),
                new FinancialSummaryResponse.ChangeSummary(profile.alertCount(),
                        List.of("MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"), profile.summary()),
                trend, true, provenance(sessionId, demoRunId, customerId));
    }

    public ProtectionActionListResponse protectionActions(UUID sessionId) {
        sessionService.requireActive(sessionId);
        List<ProtectionActionListResponse.ProtectionActionItem> items = jdbcTemplate.query("""
                select action_code, title, action_status, execution_type, eligibility_summary,
                       issuer, source_url, effective_from, checked_at
                  from protection_action_catalog order by display_order
                """, (rs, row) -> new ProtectionActionListResponse.ProtectionActionItem(
                rs.getString("action_code"), rs.getString("title"), rs.getString("action_status"),
                rs.getString("execution_type"), rs.getString("eligibility_summary"),
                new ProtectionActionListResponse.Source(rs.getString("issuer"), rs.getString("source_url"),
                        rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("checked_at", LocalDate.class))));
        return new ProtectionActionListResponse(items, true, "SYNTHETIC_ONLY");
    }

    private SyntheticDataProvenance provenance(UUID sessionId, UUID demoRunId, String customerId) {
        ProvenanceRow source = jdbcTemplate.queryForObject("""
                select source_provider, max(source_updated_at) source_updated_at,
                       min(data_freshness) data_freshness, min(consent_id) consent_id,
                       min(snapshot_hash) snapshot_hash
                  from synthetic_connection
                 where demo_session_id = ? and demo_run_id = ? and customer_id = ?
                 group by source_provider
                """, (rs, row) -> new ProvenanceRow(rs.getString("source_provider"),
                rs.getObject("source_updated_at", OffsetDateTime.class), rs.getString("data_freshness"),
                rs.getString("consent_id"), rs.getString("snapshot_hash")), sessionId, demoRunId, customerId);
        List<String> scope = jdbcTemplate.queryForList("""
                select distinct s.scope_code
                  from synthetic_connection_scope s
                  join synthetic_connection c
                    on c.demo_session_id = s.demo_session_id
                   and c.demo_run_id = s.demo_run_id
                   and c.connection_id = s.connection_id
                 where c.demo_session_id = ? and c.demo_run_id = ? and c.customer_id = ? order by s.scope_code
                """, String.class, sessionId, demoRunId, customerId);
        return new SyntheticDataProvenance(true, source.sourceProvider(), source.sourceUpdatedAt(),
                source.dataFreshness(), source.consentId(), scope, source.snapshotHash());
    }

    private void validateFilters(LocalDate from, LocalDate to, String direction, int limit) {
        if ((from != null && to != null && from.isAfter(to)) || limit < 1 || limit > 100
                || (direction != null && !List.of("IN", "OUT").contains(direction))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "거래 조회 조건이 올바르지 않습니다.");
        }
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf('|');
            return new Cursor(OffsetDateTime.parse(decoded.substring(0, separator)), decoded.substring(separator + 1));
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "cursor가 올바르지 않습니다.");
        }
    }

    private String encodeCursor(TransactionListResponse.TransactionItem item) {
        String raw = item.occurredAt() + "|" + item.transactionId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private MoneyAmount money(ResultSet rs, String column) throws SQLException {
        return new MoneyAmount(decimal(rs, column), rs.getString("currency"));
    }

    private MoneyAmount amount(String value) {
        return new MoneyAmount(value, KRW);
    }

    private String decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value.stripTrailingZeros().toPlainString();
    }

    private record Cursor(OffsetDateTime occurredAt, String transactionId) { }
    private record ProvenanceRow(String sourceProvider, OffsetDateTime sourceUpdatedAt,
                                 String dataFreshness, String consentId, String snapshotHash) { }
    private record Profile(LocalDate asOf, LocalDate from, LocalDate to, String income,
                           String expense, String upcoming, String liabilities,
                           int alertCount, String summary) { }
}
