package com.alzswell.card.application;

import com.alzswell.card.api.CardErrorCode;
import com.alzswell.card.api.CardResponses.*;
import com.alzswell.common.exception.BusinessException;
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
public class CardQueryService {
    private static final LocalDate DATA_AS_OF = LocalDate.of(2026, 8, 14);
    private static final String CARD_SELECT = """
            select c.card_id,c.institution_id,i.display_name institution_name,c.linked_account_id,
                   c.display_name,c.masked_card_number,c.card_type,c.brand_code,c.status,c.payment_day,
                   c.next_payment_due_date,c.current_usage_amount,c.current_due_amount,
                   c.total_limit_amount,c.available_limit_amount,c.currency,c.provider_mode,c.data_as_of
              from customer_card_snapshot c
              join financial_institution i on i.institution_id=c.institution_id
            """;
    private final JdbcTemplate jdbc;

    public CardQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CardList cards(String customerId) {
        List<CardRow> rows = jdbc.query(CARD_SELECT + """
                 where c.customer_id=? order by c.status,c.display_name,c.card_id
                """, this::cardRow, customerId);
        List<CardSummary> items = rows.stream().map(CardRow::summary).toList();
        LocalDate dataAsOf = rows.stream().map(CardRow::dataAsOf).max(LocalDate::compareTo).orElse(null);
        return new CardList(items, items.size(), dataAsOf, true, false);
    }

    public CardDetail card(String customerId, UUID cardId) {
        CardRow row = ownedCard(customerId, cardId);
        return new CardDetail(row.summary(), row.nextPaymentDueDate(), row.currentDueAmount(),
                false, false, false, true, false, false);
    }

    public CardTransactionPage transactions(String customerId, UUID cardId,
                                            LocalDate requestedFrom, LocalDate requestedTo,
                                            UUID cursor, int limit) {
        ownedCard(customerId, cardId);
        DateRange range = range(requestedFrom, requestedTo);
        StringBuilder sql = new StringBuilder("""
                select card_transaction_id,occurred_at,merchant_display_name,category_code,
                       amount,status,installment_months,currency,data_as_of
                  from card_transaction_snapshot where card_id=?
                   and (occurred_at at time zone 'Asia/Seoul')::date between ? and ?
                """);
        List<Object> args = new ArrayList<>(List.of(cardId, range.from(), range.to()));
        if (cursor != null) {
            OffsetDateTime cursorTime = cursorTime(cardId, cursor);
            sql.append(" and (occurred_at,card_transaction_id)<(?,?)");
            args.add(cursorTime);
            args.add(cursor);
        }
        sql.append(" order by occurred_at desc,card_transaction_id desc limit ?");
        args.add(Math.incrementExact(limit));
        List<CardTransaction> rows = jdbc.query(sql.toString(), (rs, n) -> new CardTransaction(
                rs.getObject("card_transaction_id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("merchant_display_name"),
                rs.getString("category_code"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getInt("installment_months"), rs.getString("currency"),
                rs.getObject("data_as_of", LocalDate.class)), args.toArray());
        boolean hasNext = rows.size() > limit;
        List<CardTransaction> items = hasNext ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        UUID nextCursor = hasNext ? items.getLast().cardTransactionId() : null;
        return new CardTransactionPage(cardId, items, items.size(), nextCursor, hasNext,
                range.from(), range.to(), true, false);
    }

    public CardStatementList statements(String customerId, UUID cardId) {
        ownedCard(customerId, cardId);
        List<CardStatement> items = jdbc.query("""
                select statement_id,period_from,period_to,statement_date,due_date,total_amount,
                       paid_amount,remaining_due_amount,status,currency,data_as_of
                  from card_statement_snapshot where card_id=?
                 order by period_to desc,statement_id desc limit 24
                """, (rs, n) -> new CardStatement(rs.getObject("statement_id", UUID.class),
                rs.getObject("period_from", LocalDate.class), rs.getObject("period_to", LocalDate.class),
                rs.getObject("statement_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("total_amount"), rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("remaining_due_amount"), rs.getString("status"), rs.getString("currency"),
                rs.getObject("data_as_of", LocalDate.class)), cardId);
        return new CardStatementList(cardId, items, items.size(), false, true, false, false);
    }

    public CardPaymentDue paymentDue(String customerId, UUID cardId) {
        CardRow card = ownedCard(customerId, cardId);
        List<CardStatement> statements = statements(customerId, cardId).items();
        if (statements.isEmpty()) throw new BusinessException(CardErrorCode.STATEMENT_NOT_FOUND);
        CardStatement latest = statements.getFirst();
        return new CardPaymentDue(cardId, latest.statementId(), card.nextPaymentDueDate(),
                card.currentDueAmount(), card.currency(), latest.status(), card.dataAsOf(),
                false, true, false, false);
    }

    public CardLimit limits(String customerId, UUID cardId) {
        CardRow card = ownedCard(customerId, cardId);
        return new CardLimit(cardId, "CREDIT".equals(card.cardType()) ? "MONTHLY_CREDIT" : "DAILY_DEBIT",
                card.totalLimitAmount(), card.currentUsageAmount(), card.availableLimitAmount(),
                card.currency(), card.dataAsOf(), false, true, false, false);
    }

    private CardRow ownedCard(String customerId, UUID cardId) {
        List<CardRow> rows = jdbc.query(CARD_SELECT + " where c.customer_id=? and c.card_id=?",
                this::cardRow, customerId, cardId);
        if (rows.size() != 1) throw new BusinessException(CardErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private OffsetDateTime cursorTime(UUID cardId, UUID cursor) {
        List<OffsetDateTime> rows = jdbc.query("""
                select occurred_at from card_transaction_snapshot
                 where card_id=? and card_transaction_id=?
                """, (rs, n) -> rs.getObject("occurred_at", OffsetDateTime.class), cardId, cursor);
        if (rows.size() != 1) throw new BusinessException(CardErrorCode.CURSOR_INVALID);
        return rows.getFirst();
    }

    private DateRange range(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate to = requestedTo == null ? DATA_AS_OF : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(30) : requestedFrom;
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new BusinessException(CardErrorCode.DATE_RANGE_INVALID);
        }
        return new DateRange(from, to);
    }

    private CardRow cardRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new CardRow(rs.getObject("card_id", UUID.class), rs.getString("institution_id"),
                rs.getString("institution_name"), rs.getObject("linked_account_id", UUID.class),
                rs.getString("display_name"), rs.getString("masked_card_number"), rs.getString("card_type"),
                rs.getString("brand_code"), rs.getString("status"), rs.getInt("payment_day"),
                rs.getObject("next_payment_due_date", LocalDate.class), rs.getBigDecimal("current_usage_amount"),
                rs.getBigDecimal("current_due_amount"), rs.getBigDecimal("total_limit_amount"),
                rs.getBigDecimal("available_limit_amount"), rs.getString("currency"),
                rs.getString("provider_mode"), rs.getObject("data_as_of", LocalDate.class));
    }

    private record DateRange(LocalDate from, LocalDate to) {}

    private record CardRow(UUID cardId, String institutionId, String institutionName, UUID linkedAccountId,
                           String displayName, String maskedCardNumber, String cardType, String brandCode,
                           String status, int paymentDay, LocalDate nextPaymentDueDate,
                           java.math.BigDecimal currentUsageAmount, java.math.BigDecimal currentDueAmount,
                           java.math.BigDecimal totalLimitAmount, java.math.BigDecimal availableLimitAmount,
                           String currency, String providerMode, LocalDate dataAsOf) {
        CardSummary summary() {
            return new CardSummary(cardId, institutionId, institutionName, linkedAccountId, displayName,
                    maskedCardNumber, cardType, brandCode, status, paymentDay, currentUsageAmount,
                    currency, providerMode, dataAsOf);
        }
    }
}
