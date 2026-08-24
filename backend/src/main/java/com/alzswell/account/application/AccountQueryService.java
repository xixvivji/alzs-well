package com.alzswell.account.application;

import com.alzswell.account.api.AccountErrorCode;
import com.alzswell.account.api.AccountRequests.UpdateDisplaySetting;
import com.alzswell.account.api.AccountResponses.*;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.common.security.SensitiveTextPolicy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final Clock clock;
    private final SensitiveTextPolicy sensitiveTextPolicy;
    private final MutationIdempotencyService idempotency;

    public AccountQueryService(JdbcTemplate jdbc, Clock clock, SensitiveTextPolicy sensitiveTextPolicy,
            MutationIdempotencyService idempotency) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.sensitiveTextPolicy = sensitiveTextPolicy;
        this.idempotency = idempotency;
    }

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

    public RecurringCounterpartyList recurringCounterparties(String customerId, UUID accountId) {
        ownedAccount(customerId, accountId);
        List<RecurringCounterparty> items = jdbc.query("""
                select c.counterparty_id,c.display_name,c.counterparty_type,r.occurrence_count,
                       r.average_amount,r.last_amount,r.currency,r.estimated_cycle_days,
                       r.next_expected_on,r.confidence,r.data_as_of
                  from account_recurring_counterparty_snapshot r
                  join financial_counterparty_snapshot c on c.counterparty_id=r.counterparty_id
                 where r.account_id=? and c.customer_id=?
                 order by r.confidence desc,c.display_name,c.counterparty_id
                """, (rs, n) -> new RecurringCounterparty(rs.getObject("counterparty_id", UUID.class),
                        rs.getString("display_name"), rs.getString("counterparty_type"),
                        rs.getInt("occurrence_count"), rs.getBigDecimal("average_amount"),
                        rs.getBigDecimal("last_amount"), rs.getString("currency"),
                        rs.getInt("estimated_cycle_days"), rs.getObject("next_expected_on", LocalDate.class),
                        rs.getBigDecimal("confidence"), rs.getObject("data_as_of", LocalDate.class)),
                accountId, customerId);
        return new RecurringCounterpartyList(accountId, items, items.size(), true);
    }

    @Transactional
    public DisplaySetting updateDisplaySetting(String customerId, UUID accountId, UpdateDisplaySetting command,
            String idempotencyKey) {
        return idempotency.execute("ACCOUNT_DISPLAY:" + customerId + ":" + accountId, idempotencyKey,
                command, DisplaySetting.class, AccountErrorCode.IDEMPOTENCY_CONFLICT,
                () -> updateDisplaySettingOnce(customerId, accountId, command));
    }

    private DisplaySetting updateDisplaySettingOnce(String customerId, UUID accountId, UpdateDisplaySetting command) {
        ownedAccount(customerId, accountId);
        if (command.alias() == null && command.displayOrder() == null && command.hidden() == null) {
            throw new BusinessException(AccountErrorCode.INVALID_DISPLAY_SETTING);
        }
        DisplaySetting current = displaySetting(customerId, accountId);
        String alias = command.alias() == null ? current.alias() : normalizeAlias(command.alias());
        int displayOrder = command.displayOrder() == null ? current.displayOrder() : command.displayOrder();
        boolean hidden = command.hidden() == null ? current.hidden() : command.hidden();
        OffsetDateTime now = OffsetDateTime.now(clock);
        int changed;
        try {
            changed = jdbc.update("""
                    update account_display_setting
                       set alias=?,display_order=?,hidden=?,row_version=row_version+1,updated_at=?
                     where account_id=? and customer_id=? and row_version=?
                    """, alias, displayOrder, hidden, now, accountId, customerId, command.expectedVersion());
        } catch (DataIntegrityViolationException conflict) {
            throw new BusinessException(AccountErrorCode.DISPLAY_ORDER_CONFLICT);
        }
        if (changed != 1) throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        long nextVersion = command.expectedVersion() + 1;
        jdbc.update("""
                insert into account_display_setting_event(
                    event_id,account_id,customer_id,alias_snapshot,display_order_snapshot,
                    hidden_snapshot,row_version,actor_id,occurred_at
                ) values(?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), accountId, customerId, alias, displayOrder,
                hidden, nextVersion, customerId, now);
        return new DisplaySetting(accountId, alias, displayOrder, hidden, nextVersion, now);
    }

    public AccountGroupList accountGroups(String customerId) {
        List<GroupRow> rows = jdbc.query("""
                select g.group_id,g.group_name,g.display_order group_order,g.data_as_of,
                       a.account_id,a.display_name,a.masked_account_number,i.display_name institution_name,
                       m.display_order account_order
                  from customer_account_group_snapshot g
                  left join customer_account_group_member_snapshot m on m.group_id=g.group_id
                  left join customer_account_snapshot a on a.account_id=m.account_id and a.customer_id=g.customer_id
                  left join financial_institution i on i.institution_id=a.institution_id
                 where g.customer_id=?
                 order by g.display_order,g.group_id,m.display_order,a.account_id
                """, (rs, n) -> new GroupRow(rs.getObject("group_id", UUID.class), rs.getString("group_name"),
                        rs.getInt("group_order"), rs.getObject("data_as_of", LocalDate.class),
                        rs.getObject("account_id", UUID.class), rs.getString("display_name"),
                        rs.getString("masked_account_number"), rs.getString("institution_name"),
                        rs.getInt("account_order")), customerId);
        Map<UUID, GroupBuilder> groups = new LinkedHashMap<>();
        for (GroupRow row : rows) {
            GroupBuilder group = groups.computeIfAbsent(row.groupId(), ignored ->
                    new GroupBuilder(row.groupId(), row.groupName(), row.groupOrder(), row.dataAsOf()));
            if (row.accountId() != null) {
                group.accounts.add(new AccountGroupAccount(row.accountId(), row.displayName(),
                        row.maskedAccountNumber(), row.institutionName(), row.accountOrder()));
            }
        }
        List<AccountGroup> items = groups.values().stream().map(GroupBuilder::response).toList();
        LocalDate dataAsOf = items.stream().map(AccountGroup::dataAsOf).max(LocalDate::compareTo).orElse(null);
        return new AccountGroupList(items, items.size(), dataAsOf, true);
    }

    private DisplaySetting displaySetting(String customerId, UUID accountId) {
        List<DisplaySetting> rows = jdbc.query("""
                select account_id,alias,display_order,hidden,row_version,updated_at
                  from account_display_setting where customer_id=? and account_id=?
                """, (rs, n) -> new DisplaySetting(rs.getObject("account_id", UUID.class), rs.getString("alias"),
                        rs.getInt("display_order"), rs.getBoolean("hidden"), rs.getLong("row_version"),
                        rs.getObject("updated_at", OffsetDateTime.class)), customerId, accountId);
        if (rows.size() != 1) throw new BusinessException(AccountErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private String normalizeAlias(String requestedAlias) {
        if (requestedAlias.isBlank()) return null;
        return sensitiveTextPolicy.validate(requestedAlias, "alias");
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

    private record GroupRow(UUID groupId, String groupName, int groupOrder, LocalDate dataAsOf,
                            UUID accountId, String displayName, String maskedAccountNumber,
                            String institutionName, int accountOrder) {}

    private static final class GroupBuilder {
        private final UUID groupId;
        private final String groupName;
        private final int displayOrder;
        private final LocalDate dataAsOf;
        private final List<AccountGroupAccount> accounts = new ArrayList<>();

        private GroupBuilder(UUID groupId, String groupName, int displayOrder, LocalDate dataAsOf) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.displayOrder = displayOrder;
            this.dataAsOf = dataAsOf;
        }

        private AccountGroup response() {
            return new AccountGroup(groupId, groupName, displayOrder, List.copyOf(accounts), dataAsOf);
        }
    }
}
