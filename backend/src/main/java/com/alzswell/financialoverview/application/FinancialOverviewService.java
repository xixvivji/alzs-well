package com.alzswell.financialoverview.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.financialoverview.api.FinancialOverviewErrorCode;
import com.alzswell.financialoverview.api.FinancialOverviewResponses.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinancialOverviewService {
    private static final LocalDate DATA_AS_OF = LocalDate.of(2026, 8, 14);
    private final JdbcTemplate jdbc;

    public FinancialOverviewService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public FinancialSummary financialSummary(String customerId) {
        BigDecimal assets = amount("select coalesce(sum(current_balance),0) from customer_account_snapshot where customer_id=? and account_status='ACTIVE'", customerId);
        BigDecimal liabilities = amount("select coalesce(sum(outstanding_amount),0) from customer_liability_snapshot where customer_id=? and status='ACTIVE'", customerId);
        BigDecimal inflow = flow(customerId, "CREDIT");
        BigDecimal outflow = flow(customerId, "DEBIT");
        int accountCount = count("select count(*) from customer_account_snapshot where customer_id=? and account_status='ACTIVE'", customerId);
        int liabilityCount = count("select count(*) from customer_liability_snapshot where customer_id=? and status='ACTIVE'", customerId);
        return new FinancialSummary(assets, liabilities, assets.subtract(liabilities), inflow, outflow,
                inflow.subtract(outflow), accountCount, liabilityCount, "KRW", DATA_AS_OF, true, false);
    }

    public AssetBreakdown assetBreakdown(String customerId) {
        List<RawAsset> rows = jdbc.query("""
                select a.institution_id,i.display_name institution_name,a.account_type,
                       sum(a.current_balance) amount,count(*) account_count,max(a.data_as_of) data_as_of
                  from customer_account_snapshot a
                  join financial_institution i on i.institution_id=a.institution_id
                 where a.customer_id=? and a.account_status='ACTIVE'
                 group by a.institution_id,i.display_name,a.account_type
                 order by amount desc,a.institution_id,a.account_type
                """, (rs, n) -> new RawAsset(rs.getString("institution_id"), rs.getString("institution_name"),
                        rs.getString("account_type"), rs.getBigDecimal("amount"), rs.getInt("account_count"),
                        rs.getObject("data_as_of", LocalDate.class)), customerId);
        BigDecimal total = rows.stream().map(RawAsset::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AssetBreakdownItem> items = rows.stream().map(row -> new AssetBreakdownItem(
                row.institutionId(), row.institutionName(), row.assetClass(), row.amount(),
                percentage(row.amount(), total), row.accountCount())).toList();
        LocalDate dataAsOf = rows.stream().map(RawAsset::dataAsOf).max(LocalDate::compareTo).orElse(DATA_AS_OF);
        return new AssetBreakdown(items, total, "KRW", dataAsOf);
    }

    public AssetTrends assetTrends(String customerId, LocalDate requestedFrom, LocalDate requestedTo) {
        DateRange range = range(requestedFrom, requestedTo, 365);
        BigDecimal liabilities = amount("select coalesce(sum(outstanding_amount),0) from customer_liability_snapshot where customer_id=? and status='ACTIVE'", customerId);
        List<AssetTrendPoint> items = jdbc.query("""
                select b.balance_date,sum(b.current_balance) total_assets
                  from customer_account_balance_snapshot b
                  join customer_account_snapshot a on a.account_id=b.account_id
                 where a.customer_id=? and b.balance_date between ? and ?
                 group by b.balance_date
                having count(*)=(select count(*) from customer_account_snapshot where customer_id=? and account_status='ACTIVE')
                 order by b.balance_date
                """, (rs, n) -> {
                    BigDecimal assets = rs.getBigDecimal("total_assets");
                    return new AssetTrendPoint(rs.getObject("balance_date", LocalDate.class), assets,
                            liabilities, assets.subtract(liabilities));
                }, customerId, range.from(), range.to(), customerId);
        return new AssetTrends(items, items.size(), range.from(), range.to(), "KRW", true);
    }

    public LiabilityList liabilities(String customerId) {
        List<Liability> items = jdbc.query("""
                select l.*,i.display_name institution_name
                  from customer_liability_snapshot l
                  join financial_institution i on i.institution_id=l.institution_id
                 where l.customer_id=? order by l.next_due_date,l.liability_id
                """, (rs, n) -> new Liability(rs.getObject("liability_id", UUID.class),
                        rs.getString("institution_id"), rs.getString("institution_name"),
                        rs.getString("liability_type"), rs.getString("display_name"),
                        rs.getString("masked_reference"), rs.getBigDecimal("outstanding_amount"),
                        rs.getBigDecimal("scheduled_amount"), rs.getBigDecimal("annual_interest_rate"),
                        rs.getObject("next_due_date", LocalDate.class), rs.getString("status"),
                        rs.getString("currency"), rs.getObject("data_as_of", LocalDate.class), false), customerId);
        BigDecimal total = items.stream().map(Liability::outstandingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate dataAsOf = items.stream().map(Liability::dataAsOf).max(LocalDate::compareTo).orElse(DATA_AS_OF);
        return new LiabilityList(items, items.size(), total, "KRW", dataAsOf);
    }

    public CashflowSummary cashflowSummary(String customerId, LocalDate requestedFrom, LocalDate requestedTo) {
        DateRange range = range(requestedFrom, requestedTo, 365);
        List<CashflowCategory> categories = jdbc.query("""
                select coalesce(p.category_code,e.inferred_category) category,
                       coalesce(sum(t.amount) filter(where t.direction='CREDIT'),0) inflow,
                       coalesce(sum(t.amount) filter(where t.direction='DEBIT'),0) outflow,
                       count(*) transaction_count
                  from financial_transaction_snapshot t
                  join transaction_enrichment_snapshot e on e.transaction_id=t.transaction_id
                  join customer_transaction_preference p on p.transaction_id=t.transaction_id
                 where t.customer_id=? and t.posted_on between ? and ? and t.status='POSTED'
                 group by coalesce(p.category_code,e.inferred_category) order by category
                """, (rs, n) -> new CashflowCategory(rs.getString("category"), rs.getBigDecimal("inflow"),
                        rs.getBigDecimal("outflow"), rs.getInt("transaction_count")),
                customerId, range.from(), range.to());
        BigDecimal inflow = categories.stream().map(CashflowCategory::inflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outflow = categories.stream().map(CashflowCategory::outflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = categories.stream().mapToInt(CashflowCategory::count).sum();
        return new CashflowSummary(inflow, outflow, inflow.subtract(outflow), categories, count,
                range.from(), range.to(), "KRW", true);
    }

    public ExpenseSummary expenseSummary(String customerId, LocalDate requestedFrom, LocalDate requestedTo) {
        DateRange range = range(requestedFrom, requestedTo, 365);
        List<RawExpense> rows = jdbc.query("""
                select coalesce(p.category_code,e.inferred_category) category,i.display_name institution_name,
                       sum(t.amount) amount,count(*) transaction_count
                  from financial_transaction_snapshot t
                  join transaction_enrichment_snapshot e on e.transaction_id=t.transaction_id
                  join customer_transaction_preference p on p.transaction_id=t.transaction_id
                  join customer_account_snapshot a on a.account_id=t.account_id
                  join financial_institution i on i.institution_id=a.institution_id
                 where t.customer_id=? and t.posted_on between ? and ?
                   and t.status='POSTED' and t.direction='DEBIT'
                 group by coalesce(p.category_code,e.inferred_category),i.display_name
                 order by amount desc,category,i.display_name
                """, (rs, n) -> new RawExpense(rs.getString("category"), rs.getString("institution_name"),
                        rs.getBigDecimal("amount"), rs.getInt("transaction_count")),
                customerId, range.from(), range.to());
        BigDecimal total = rows.stream().map(RawExpense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ExpenseBreakdown> items = rows.stream().map(row -> new ExpenseBreakdown(row.category(),
                row.institutionName(), row.amount(), percentage(row.amount(), total), row.count())).toList();
        return new ExpenseSummary(total, items, range.from(), range.to(), "KRW");
    }

    public AssetCalendar assetCalendar(String customerId, LocalDate requestedFrom, LocalDate requestedTo) {
        DateRange range = calendarRange(requestedFrom, requestedTo);
        List<AssetCalendarEvent> items = new ArrayList<>();
        items.addAll(jdbc.query("""
                select event_id,'ASSET_SNAPSHOT' source_type,event_type,title,scheduled_date,direction,
                       expected_amount,currency,certainty
                  from customer_asset_calendar_snapshot
                 where customer_id=? and scheduled_date between ? and ?
                """, this::calendarEvent, customerId, range.from(), range.to()));
        items.addAll(jdbc.query("""
                select recurring_payment_id event_id,'RECURRING_PAYMENT' source_type,payment_type event_type,
                       display_name title,next_expected_date scheduled_date,'OUTFLOW' direction,
                       expected_amount,currency,'EXPECTED' certainty
                  from recurring_payment
                 where customer_id=? and status='ACTIVE' and next_expected_date between ? and ?
                """, this::calendarEvent, customerId, range.from(), range.to()));
        items.addAll(jdbc.query("""
                select liability_id event_id,'LIABILITY' source_type,liability_type event_type,
                       display_name title,next_due_date scheduled_date,'OUTFLOW' direction,
                       scheduled_amount expected_amount,currency,'EXPECTED' certainty
                  from customer_liability_snapshot
                 where customer_id=? and status='ACTIVE' and next_due_date between ? and ?
                """, this::calendarEvent, customerId, range.from(), range.to()));
        items.sort(java.util.Comparator.comparing(AssetCalendarEvent::scheduledDate)
                .thenComparing(AssetCalendarEvent::eventId));
        return new AssetCalendar(List.copyOf(items), items.size(), range.from(), range.to(), DATA_AS_OF, true);
    }

    public DataFreshness dataFreshness(String customerId) {
        List<FreshnessItem> items = jdbc.query("""
                select i.institution_id,i.display_name,c.connection_id,c.connection_status,c.last_synced_at,
                       greatest(i.data_as_of,max(a.data_as_of),max(t.data_as_of)) data_as_of,
                       count(distinct a.account_id) account_count,count(distinct t.transaction_id) transaction_count,
                       c.provider_mode
                  from customer_connection c
                  join financial_institution i on i.institution_id=c.institution_id
                  left join customer_account_snapshot a on a.connection_id=c.connection_id
                  left join financial_transaction_snapshot t on t.account_id=a.account_id
                 where c.customer_id=?
                 group by i.institution_id,i.display_name,i.data_as_of,c.connection_id,c.connection_status,
                          c.last_synced_at,c.provider_mode
                 order by i.display_name,i.institution_id
                """, (rs, n) -> {
                    LocalDate dataAsOf = rs.getObject("data_as_of", LocalDate.class);
                    int accounts = rs.getInt("account_count");
                    boolean complete = accounts > 0 && "ACTIVE".equals(rs.getString("connection_status"));
                    String status = !complete ? "INCOMPLETE" :
                            (ChronoUnit.DAYS.between(dataAsOf, DATA_AS_OF) <= 1 ? "FRESH" : "STALE");
                    return new FreshnessItem(rs.getString("institution_id"), rs.getString("display_name"),
                            rs.getObject("connection_id", UUID.class), rs.getString("connection_status"),
                            rs.getObject("last_synced_at", OffsetDateTime.class), dataAsOf, accounts,
                            rs.getInt("transaction_count"), status, complete, rs.getString("provider_mode"));
                }, customerId);
        boolean allFresh = !items.isEmpty() && items.stream().allMatch(item -> "FRESH".equals(item.freshnessStatus()));
        return new DataFreshness(items, items.size(), DATA_AS_OF, allFresh, true);
    }

    private AssetCalendarEvent calendarEvent(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AssetCalendarEvent(rs.getObject("event_id", UUID.class), rs.getString("source_type"),
                rs.getString("event_type"), rs.getString("title"),
                rs.getObject("scheduled_date", LocalDate.class), rs.getString("direction"),
                rs.getBigDecimal("expected_amount"), rs.getString("currency"), rs.getString("certainty"), false);
    }

    private BigDecimal flow(String customerId, String direction) {
        return jdbc.queryForObject("""
                select coalesce(sum(amount),0) from financial_transaction_snapshot
                 where customer_id=? and direction=? and status='POSTED'
                   and posted_on between date '2026-05-14' and date '2026-08-14'
                """, BigDecimal.class, customerId, direction);
    }

    private BigDecimal amount(String sql, String customerId) {
        return jdbc.queryForObject(sql, BigDecimal.class, customerId);
    }

    private int count(String sql, String customerId) {
        Integer result = jdbc.queryForObject(sql, Integer.class, customerId);
        return result == null ? 0 : result;
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private DateRange range(LocalDate requestedFrom, LocalDate requestedTo, long maxDays) {
        LocalDate to = requestedTo == null ? DATA_AS_OF : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusMonths(3) : requestedFrom;
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > maxDays) {
            throw new BusinessException(FinancialOverviewErrorCode.INVALID_DATE_RANGE);
        }
        return new DateRange(from, to);
    }

    private DateRange calendarRange(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate from = requestedFrom == null ? DATA_AS_OF : requestedFrom;
        LocalDate to = requestedTo == null ? from.plusDays(92) : requestedTo;
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 92) {
            throw new BusinessException(FinancialOverviewErrorCode.INVALID_DATE_RANGE);
        }
        return new DateRange(from, to);
    }

    private record DateRange(LocalDate from, LocalDate to) {}
    private record RawAsset(String institutionId, String institutionName, String assetClass,
                            BigDecimal amount, int accountCount, LocalDate dataAsOf) {}
    private record RawExpense(String category, String institutionName, BigDecimal amount, int count) {}
}
