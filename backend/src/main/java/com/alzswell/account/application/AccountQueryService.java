package com.alzswell.account.application;

import com.alzswell.account.api.AccountErrorCode;
import com.alzswell.account.api.AccountResponses.*;
import com.alzswell.common.exception.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService {
    private static final String ACCOUNT_SELECT = """
            select a.*,i.display_name institution_name
              from customer_account_snapshot a
              join financial_institution i on i.institution_id=a.institution_id
            """;
    private final JdbcTemplate jdbc;

    public AccountQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AccountList accounts(String customerId) {
        List<AccountSummary> items = jdbc.query(ACCOUNT_SELECT + """
                 where a.customer_id=? order by i.display_name,a.display_name,a.account_id
                """, this::accountSummary, customerId);
        LocalDate dataAsOf = items.stream().map(AccountSummary::dataAsOf).max(LocalDate::compareTo).orElse(null);
        return new AccountList(items, items.size(), dataAsOf);
    }

    public AccountDetail account(String customerId, UUID accountId) {
        AccountSummary account = ownedAccount(customerId, accountId);
        return new AccountDetail(account, true, false, false);
    }

    public Balance balance(String customerId, UUID accountId) {
        AccountSummary account = ownedAccount(customerId, accountId);
        return new Balance(account.accountId(), account.currentBalance(), account.availableBalance(),
                account.currency(), account.balanceAsOf(), account.dataAsOf());
    }

    public BalanceHistory balanceHistory(String customerId, UUID accountId,
                                         LocalDate requestedFrom, LocalDate requestedTo) {
        AccountSummary account = ownedAccount(customerId, accountId);
        LocalDate to = requestedTo == null ? account.dataAsOf() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusMonths(2).withDayOfMonth(1) : requestedFrom;
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new BusinessException(AccountErrorCode.INVALID_DATE_RANGE);
        }
        List<BalancePoint> items = jdbc.query("""
                select balance_date,current_balance,available_balance
                  from customer_account_balance_snapshot
                 where account_id=? and balance_date between ? and ? order by balance_date
                """, (rs, n) -> new BalancePoint(rs.getObject("balance_date", LocalDate.class),
                        rs.getBigDecimal("current_balance"), rs.getBigDecimal("available_balance"),
                        account.currency()), accountId, from, to);
        return new BalanceHistory(accountId, items, items.size(), from, to, account.dataAsOf());
    }

    public RestrictionList restrictions(String customerId, UUID accountId) {
        AccountSummary account = ownedAccount(customerId, accountId);
        List<Restriction> items = jdbc.query("""
                select restriction_id,restriction_code,title,description,status,effective_from,effective_to
                  from customer_account_restriction_snapshot
                 where account_id=? order by effective_from,restriction_id
                """, (rs, n) -> new Restriction(rs.getObject("restriction_id", UUID.class),
                        rs.getString("restriction_code"), rs.getString("title"), rs.getString("description"),
                        rs.getString("status"), rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("effective_to", LocalDate.class), false), accountId);
        return new RestrictionList(accountId, items, items.size(), account.dataAsOf());
    }

    public InterestSummary interest(String customerId, UUID accountId) {
        ownedAccount(customerId, accountId);
        List<InterestSummary> rows = jdbc.query("""
                select account_id,interest_type,annual_interest_rate,accrued_interest,currency,interest_as_of,data_as_of
                  from customer_account_snapshot where account_id=? and customer_id=?
                """, (rs, n) -> new InterestSummary(rs.getObject("account_id", UUID.class),
                        rs.getString("interest_type"), rs.getBigDecimal("annual_interest_rate"),
                        rs.getBigDecimal("accrued_interest"), rs.getString("currency"),
                        rs.getObject("interest_as_of", LocalDate.class), true,
                        rs.getObject("data_as_of", LocalDate.class)), accountId, customerId);
        return rows.getFirst();
    }

    public StatementList statements(String customerId, UUID accountId) {
        AccountSummary account = ownedAccount(customerId, accountId);
        List<StatementSummary> items = jdbc.query("""
                select s.statement_id,s.period_from,s.period_to,s.opening_balance,s.closing_balance,
                       s.total_inflow,s.total_outflow,s.transaction_count,s.generated_at
                  from customer_account_statement_snapshot s
                 where s.account_id=? order by s.period_to desc,s.statement_id
                """, (rs, n) -> statementSummary(rs, account.currency()), accountId);
        return new StatementList(accountId, items, items.size(), account.dataAsOf());
    }

    public StatementDetail statement(String customerId, UUID accountId, UUID statementId) {
        AccountSummary account = ownedAccount(customerId, accountId);
        List<StatementSummary> rows = jdbc.query("""
                select s.statement_id,s.period_from,s.period_to,s.opening_balance,s.closing_balance,
                       s.total_inflow,s.total_outflow,s.transaction_count,s.generated_at
                  from customer_account_statement_snapshot s
                 where s.account_id=? and s.statement_id=?
                """, (rs, n) -> statementSummary(rs, account.currency()), accountId, statementId);
        if (rows.size() != 1) throw new BusinessException(AccountErrorCode.STATEMENT_NOT_FOUND);
        return new StatementDetail(accountId, rows.getFirst(), false, false);
    }

    private AccountSummary ownedAccount(String customerId, UUID accountId) {
        List<AccountSummary> rows = jdbc.query(ACCOUNT_SELECT + """
                 where a.customer_id=? and a.account_id=?
                """, this::accountSummary, customerId, accountId);
        if (rows.size() != 1) throw new BusinessException(AccountErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private StatementSummary statementSummary(ResultSet rs, String currency) throws SQLException {
        return new StatementSummary(rs.getObject("statement_id", UUID.class),
                rs.getObject("period_from", LocalDate.class), rs.getObject("period_to", LocalDate.class),
                rs.getBigDecimal("opening_balance"), rs.getBigDecimal("closing_balance"),
                rs.getBigDecimal("total_inflow"), rs.getBigDecimal("total_outflow"),
                rs.getInt("transaction_count"), currency,
                rs.getObject("generated_at", OffsetDateTime.class), false);
    }

    private AccountSummary accountSummary(ResultSet rs, int rowNum) throws SQLException {
        return new AccountSummary(rs.getObject("account_id", UUID.class), rs.getString("customer_id"),
                rs.getObject("connection_id", UUID.class), rs.getString("institution_id"),
                rs.getString("institution_name"), rs.getString("account_type"), rs.getString("display_name"),
                rs.getString("masked_account_number"), rs.getString("account_status"),
                rs.getBigDecimal("current_balance"), rs.getBigDecimal("available_balance"),
                rs.getString("currency"), rs.getObject("balance_as_of", OffsetDateTime.class),
                rs.getString("provider_mode"), rs.getObject("data_as_of", LocalDate.class), true, false);
    }
}
