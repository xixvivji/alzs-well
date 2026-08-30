package com.alzswell.transaction.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.common.security.AuditActor;
import com.alzswell.common.security.SensitiveTextPolicy;
import com.alzswell.transaction.api.TransactionErrorCode;
import com.alzswell.transaction.api.TransactionRequests.UpdateCategory;
import com.alzswell.transaction.api.TransactionRequests.UpdateNote;
import com.alzswell.transaction.api.TransactionResponses.*;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {
    private static final String TRANSACTION_SELECT = """
            select t.transaction_id,t.account_id,a.display_name account_display_name,
                   i.display_name institution_name,t.counterparty_id,c.display_name counterparty_name,
                   t.occurred_at,t.posted_on,t.direction,t.transaction_type,t.status,t.amount,t.currency,
                   t.balance_after,t.display_description,coalesce(p.category_code,e.inferred_category) category,
                   p.note_text,p.row_version,t.provider_mode,t.data_as_of
              from financial_transaction_snapshot t
              join customer_account_snapshot a on a.account_id=t.account_id
              join financial_institution i on i.institution_id=a.institution_id
              left join financial_counterparty_snapshot c on c.counterparty_id=t.counterparty_id
              join transaction_enrichment_snapshot e on e.transaction_id=t.transaction_id
              join customer_transaction_preference p on p.transaction_id=t.transaction_id
            """;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SensitiveTextPolicy sensitiveTextPolicy;
    private final MutationIdempotencyService idempotency;

    public TransactionService(JdbcTemplate jdbc, Clock clock, SensitiveTextPolicy sensitiveTextPolicy,
            MutationIdempotencyService idempotency) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.sensitiveTextPolicy = sensitiveTextPolicy;
        this.idempotency = idempotency;
    }

    public TransactionPage accountTransactions(String customerId, UUID accountId, LocalDate from, LocalDate to,
                                                String direction, String category, UUID cursor, int limit) {
        requireOwnedAccount(customerId, accountId);
        return page(customerId, accountId, null, null, from, to, direction, category,
                null, null, cursor, limit);
    }

    public TransactionDetail transaction(String customerId, UUID transactionId) {
        return new TransactionDetail(ownedTransaction(customerId, transactionId), false, false, false);
    }

    public TransactionPage search(String customerId, String query, UUID accountId, LocalDate from, LocalDate to,
                                  String direction, String category, BigDecimal minAmount, BigDecimal maxAmount,
                                  UUID cursor, int limit) {
        if (accountId != null) requireOwnedAccount(customerId, accountId);
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException(TransactionErrorCode.INVALID_AMOUNT_RANGE);
        }
        String safeQuery = query == null || query.isBlank() ? null : sensitiveTextPolicy.validate(query, "q");
        return page(customerId, accountId, null, safeQuery, from, to, direction, category,
                minAmount, maxAmount, cursor, limit);
    }

    public TransactionSummary summary(String customerId, LocalDate requestedFrom, LocalDate requestedTo) {
        DateRange range = range(requestedFrom, requestedTo);
        List<CategorySummary> categories = jdbc.query("""
                select coalesce(p.category_code,e.inferred_category) category,
                       coalesce(sum(t.amount) filter(where t.direction='CREDIT'),0) inflow,
                       coalesce(sum(t.amount) filter(where t.direction='DEBIT'),0) outflow,
                       count(*) transaction_count
                  from financial_transaction_snapshot t
                  join transaction_enrichment_snapshot e on e.transaction_id=t.transaction_id
                  join customer_transaction_preference p on p.transaction_id=t.transaction_id
                 where t.customer_id=? and t.posted_on between ? and ? and t.status='POSTED'
                 group by coalesce(p.category_code,e.inferred_category)
                 order by category
                """, (rs, n) -> new CategorySummary(rs.getString("category"), rs.getBigDecimal("inflow"),
                        rs.getBigDecimal("outflow"), rs.getInt("transaction_count")),
                customerId, range.from(), range.to());
        BigDecimal inflow = categories.stream().map(CategorySummary::inflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outflow = categories.stream().map(CategorySummary::outflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = categories.stream().mapToInt(CategorySummary::count).sum();
        return new TransactionSummary(inflow, outflow, inflow.subtract(outflow), count, "KRW",
                categories, range.from(), range.to(), true);
    }

    public CounterpartyList counterparties(String customerId) {
        List<CounterpartyItem> items = jdbc.query("""
                select c.counterparty_id,c.display_name,c.counterparty_type,c.first_seen_on,c.last_seen_on,
                       c.transaction_count,c.new_counterparty,coalesce(sum(t.amount),0) total_amount,c.data_as_of
                  from financial_counterparty_snapshot c
                  left join financial_transaction_snapshot t on t.counterparty_id=c.counterparty_id
                       and t.customer_id=c.customer_id and t.status='POSTED'
                 where c.customer_id=?
                 group by c.counterparty_id,c.display_name,c.counterparty_type,c.first_seen_on,c.last_seen_on,
                          c.transaction_count,c.new_counterparty,c.data_as_of
                 order by c.last_seen_on desc,c.counterparty_id
                """, (rs, n) -> new CounterpartyItem(rs.getObject("counterparty_id", UUID.class),
                        rs.getString("display_name"), rs.getString("counterparty_type"),
                        rs.getObject("first_seen_on", LocalDate.class), rs.getObject("last_seen_on", LocalDate.class),
                        rs.getInt("transaction_count"), rs.getBoolean("new_counterparty"),
                        rs.getBigDecimal("total_amount"), "KRW", rs.getObject("data_as_of", LocalDate.class)), customerId);
        return new CounterpartyList(items, items.size(), true);
    }

    public CounterpartyHistory counterpartyHistory(String customerId, UUID counterpartyId,
                                                    LocalDate from, LocalDate to) {
        String displayName = ownedCounterparty(customerId, counterpartyId);
        TransactionPage page = page(customerId, null, counterpartyId, null, from, to,
                null, null, null, null, null, 100);
        return new CounterpartyHistory(counterpartyId, displayName, page.items(), page.count(),
                page.nextCursor(), page.hasNext(), page.from(), page.to());
    }

    public TransactionEnrichment enrichment(String customerId, UUID transactionId) {
        TransactionItem transaction = ownedTransaction(customerId, transactionId);
        return jdbc.queryForObject("""
                select e.normalized_description,e.inferred_category,e.recurring_candidate,e.new_counterparty,
                       e.confidence,e.enrichment_version,e.reason_codes,p.category_code
                  from transaction_enrichment_snapshot e
                  join customer_transaction_preference p on p.transaction_id=e.transaction_id
                 where e.transaction_id=? and p.customer_id=?
                """, (rs, n) -> new TransactionEnrichment(transactionId, rs.getString("normalized_description"),
                        rs.getString("inferred_category"), transaction.category(),
                        rs.getString("category_code") != null, rs.getBoolean("recurring_candidate"),
                        rs.getBoolean("new_counterparty"), rs.getBigDecimal("confidence"),
                        rs.getString("enrichment_version"), array(rs.getArray("reason_codes")), true),
                transactionId, customerId);
    }

    @Transactional
    public TransactionPreference updateCategory(AuditActor actor, UUID transactionId, UpdateCategory command,
            String idempotencyKey) {
        String customerId = actor.customerId();
        return idempotency.execute("TRANSACTION_CATEGORY:" + customerId + ":" + transactionId,
                idempotencyKey, command, TransactionPreference.class, TransactionErrorCode.IDEMPOTENCY_CONFLICT,
                () -> { ownedTransaction(customerId, transactionId); return updatePreference(customerId,
                        transactionId, "CATEGORY_UPDATED", command.category(), null,
                        command.expectedVersion(), true, actor); });
    }

    @Transactional
    public TransactionPreference updateNote(AuditActor actor, UUID transactionId, UpdateNote command,
            String idempotencyKey) {
        String customerId = actor.customerId();
        String note = command.note().isBlank() ? null : sensitiveTextPolicy.validate(command.note(), "note");
        record SafeNote(String note,long expectedVersion) {}
        SafeNote safe = new SafeNote(note, command.expectedVersion());
        return idempotency.execute("TRANSACTION_NOTE:" + customerId + ":" + transactionId,
                idempotencyKey, safe, TransactionPreference.class, TransactionErrorCode.IDEMPOTENCY_CONFLICT,
                () -> { ownedTransaction(customerId, transactionId); return updatePreference(customerId,
                        transactionId, "NOTE_UPDATED", null, note, command.expectedVersion(), false, actor); });
    }

    private TransactionPreference updatePreference(String customerId, UUID transactionId, String eventType,
                                                   String category, String note, long expectedVersion,
                                                   boolean categoryUpdate, AuditActor actor) {
        Preference current = preference(customerId, transactionId);
        String nextCategory = categoryUpdate ? category : current.category();
        String nextNote = categoryUpdate ? current.note() : note;
        OffsetDateTime now = OffsetDateTime.now(clock);
        int changed = jdbc.update("""
                update customer_transaction_preference
                   set category_code=?,note_text=?,row_version=row_version+1,updated_at=?
                 where transaction_id=? and customer_id=? and row_version=?
                """, nextCategory, nextNote, now, transactionId, customerId, expectedVersion);
        if (changed != 1) throw new BusinessException(TransactionErrorCode.VERSION_CONFLICT);
        long nextVersion = expectedVersion + 1;
        jdbc.update("""
                insert into customer_transaction_preference_event(
                    event_id,transaction_id,customer_id,event_type,category_snapshot,note_snapshot,
                    row_version,actor_id,actor_principal_id,actor_customer_id,actor_session_id,
                    actor_type,occurred_at
                ) values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), transactionId, customerId, eventType, nextCategory, nextNote,
                nextVersion, actor.legacyActorId(), actor.principalId(), actor.customerId(), actor.sessionId(),
                actor.actorType(), now);
        return new TransactionPreference(transactionId, nextCategory, nextNote, nextVersion, now, false);
    }

    private TransactionPage page(String customerId, UUID accountId, UUID counterpartyId, String query,
                                 LocalDate requestedFrom, LocalDate requestedTo, String direction, String category,
                                 BigDecimal minAmount, BigDecimal maxAmount, UUID cursor, int limit) {
        DateRange range = range(requestedFrom, requestedTo);
        StringBuilder sql = new StringBuilder(TRANSACTION_SELECT).append(" where t.customer_id=?");
        List<Object> args = new ArrayList<>();
        args.add(customerId);
        if (accountId != null) { sql.append(" and t.account_id=?"); args.add(accountId); }
        if (counterpartyId != null) { sql.append(" and t.counterparty_id=?"); args.add(counterpartyId); }
        sql.append(" and t.posted_on between ? and ?"); args.add(range.from()); args.add(range.to());
        if (direction != null) { sql.append(" and t.direction=?"); args.add(direction); }
        if (category != null) {
            sql.append(" and coalesce(p.category_code,e.inferred_category)=?"); args.add(category);
        }
        if (query != null) {
            sql.append(" and (lower(t.display_description) like lower(?) escape '!' or lower(coalesce(c.display_name,'')) like lower(?) escape '!')");
            String pattern = "%" + escapeLike(query) + "%";
            args.add(pattern); args.add(pattern);
        }
        if (minAmount != null) { sql.append(" and t.amount>=?"); args.add(minAmount); }
        if (maxAmount != null) { sql.append(" and t.amount<=?"); args.add(maxAmount); }
        if (cursor != null) {
            OffsetDateTime cursorTime = cursorTime(customerId, accountId, counterpartyId, cursor);
            sql.append(" and (t.occurred_at,t.transaction_id)<(?,?)");
            args.add(cursorTime); args.add(cursor);
        }
        sql.append(" order by t.occurred_at desc,t.transaction_id desc limit ?");
        args.add(Math.incrementExact(limit));
        List<TransactionItem> rows = jdbc.query(sql.toString(), this::transactionItem, args.toArray());
        boolean hasNext = rows.size() > limit;
        List<TransactionItem> items = hasNext ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        UUID nextCursor = hasNext ? items.getLast().transactionId() : null;
        return new TransactionPage(items, items.size(), nextCursor, hasNext, range.from(), range.to());
    }

    private OffsetDateTime cursorTime(String customerId, UUID accountId, UUID counterpartyId, UUID cursor) {
        StringBuilder sql = new StringBuilder("select occurred_at from financial_transaction_snapshot where customer_id=? and transaction_id=?");
        List<Object> args = new ArrayList<>(List.of(customerId, cursor));
        if (accountId != null) { sql.append(" and account_id=?"); args.add(accountId); }
        if (counterpartyId != null) { sql.append(" and counterparty_id=?"); args.add(counterpartyId); }
        List<OffsetDateTime> rows = jdbc.query(sql.toString(),
                (rs, n) -> rs.getObject("occurred_at", OffsetDateTime.class), args.toArray());
        if (rows.size() != 1) throw new BusinessException(TransactionErrorCode.INVALID_CURSOR);
        return rows.getFirst();
    }

    private DateRange range(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate to = requestedTo == null ? LocalDate.of(2026, 8, 14) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusMonths(3) : requestedFrom;
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new BusinessException(TransactionErrorCode.INVALID_DATE_RANGE);
        }
        return new DateRange(from, to);
    }

    private TransactionItem ownedTransaction(String customerId, UUID transactionId) {
        List<TransactionItem> rows = jdbc.query(TRANSACTION_SELECT +
                " where t.customer_id=? and t.transaction_id=?", this::transactionItem, customerId, transactionId);
        if (rows.size() != 1) throw new BusinessException(TransactionErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private void requireOwnedAccount(String customerId, UUID accountId) {
        Boolean exists = jdbc.queryForObject("""
                select exists(select 1 from customer_account_snapshot where customer_id=? and account_id=?)
                """, Boolean.class, customerId, accountId);
        if (!Boolean.TRUE.equals(exists)) throw new BusinessException(TransactionErrorCode.NOT_FOUND);
    }

    private String ownedCounterparty(String customerId, UUID counterpartyId) {
        List<String> rows = jdbc.query("""
                select display_name from financial_counterparty_snapshot where customer_id=? and counterparty_id=?
                """, (rs, n) -> rs.getString(1), customerId, counterpartyId);
        if (rows.size() != 1) throw new BusinessException(TransactionErrorCode.COUNTERPARTY_NOT_FOUND);
        return rows.getFirst();
    }

    private Preference preference(String customerId, UUID transactionId) {
        return jdbc.queryForObject("""
                select category_code,note_text,row_version from customer_transaction_preference
                 where customer_id=? and transaction_id=?
                """, (rs, n) -> new Preference(rs.getString("category_code"), rs.getString("note_text"),
                        rs.getLong("row_version")), customerId, transactionId);
    }

    private TransactionItem transactionItem(ResultSet rs, int rowNum) throws SQLException {
        return new TransactionItem(rs.getObject("transaction_id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("account_display_name"), rs.getString("institution_name"),
                rs.getObject("counterparty_id", UUID.class), rs.getString("counterparty_name"),
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getObject("posted_on", LocalDate.class),
                rs.getString("direction"), rs.getString("transaction_type"), rs.getString("status"),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getBigDecimal("balance_after"),
                rs.getString("display_description"), rs.getString("category"), rs.getString("note_text"),
                rs.getLong("row_version"), rs.getString("provider_mode"), rs.getObject("data_as_of", LocalDate.class),
                true, false);
    }

    private List<String> array(Array sqlArray) throws SQLException {
        if (sqlArray == null) return List.of();
        return Arrays.stream((Object[]) sqlArray.getArray()).map(String::valueOf).toList();
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private record DateRange(LocalDate from, LocalDate to) {}
    private record Preference(String category, String note, long version) {}
}
