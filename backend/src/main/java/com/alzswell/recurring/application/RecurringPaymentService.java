package com.alzswell.recurring.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.common.security.AuditActor;
import com.alzswell.recurring.api.RecurringPaymentErrorCode;
import com.alzswell.recurring.api.RecurringPaymentRequests.ReminderSettingsCommand;
import com.alzswell.recurring.api.RecurringPaymentResponses.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringPaymentService {
    private static final String PAYMENT_SELECT = """
            select p.*, i.display_name institution_name,
                   case
                     when exists(select 1 from recurring_payment_occurrence o
                                  where o.recurring_payment_id=p.recurring_payment_id and o.occurrence_status='MISSED')
                       then 'MISSED_CANDIDATE'
                     when exists(select 1 from recurring_payment_occurrence o
                                  where o.recurring_payment_id=p.recurring_payment_id and o.occurrence_status='DUPLICATE_CANDIDATE')
                       then 'DUPLICATE_CANDIDATE'
                     else 'ON_TRACK'
                   end observation_status
              from recurring_payment p
              join financial_institution i on i.institution_id=p.institution_id
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MutationIdempotencyService idempotency;

    public RecurringPaymentService(JdbcTemplate jdbc, Clock clock, MutationIdempotencyService idempotency) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public PaymentList payments(String customerId) {
        List<PaymentSummary> items = jdbc.query(PAYMENT_SELECT + """
                 where p.customer_id=? order by p.next_expected_date, p.display_name, p.recurring_payment_id
                """, this::paymentSummary, customerId);
        return new PaymentList(items, items.size(), dataAsOf(items));
    }

    @Transactional(readOnly = true)
    public PaymentDetail payment(String customerId, UUID recurringPaymentId) {
        PaymentSummary payment = ownedPayment(customerId, recurringPaymentId);
        List<Occurrence> rows = latestOccurrenceRows(recurringPaymentId);
        return new PaymentDetail(payment, rows.isEmpty() ? null : rows.getFirst(), false, false);
    }

    @Transactional(readOnly = true)
    public Calendar calendar(String customerId, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate dataAsOf = customerDataAsOf(customerId);
        LocalDate from = requestedFrom == null ? dataAsOf.withDayOfMonth(1) : requestedFrom;
        LocalDate to = requestedTo == null ? from.plusMonths(2).minusDays(1) : requestedTo;
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 92) {
            throw new BusinessException(RecurringPaymentErrorCode.INVALID_DATE_RANGE);
        }
        List<CalendarEntry> items = jdbc.query("""
                select p.recurring_payment_id,p.display_name,o.occurrence_id,o.expected_date,o.observed_at,
                       o.amount,p.currency,o.occurrence_status
                  from recurring_payment p join recurring_payment_occurrence o using(recurring_payment_id)
                 where p.customer_id=? and o.expected_date between ? and ?
                 order by o.expected_date,p.display_name,o.occurrence_id
                """, (rs, n) -> new CalendarEntry(
                        rs.getObject("recurring_payment_id", UUID.class), rs.getString("display_name"),
                        rs.getObject("occurrence_id", UUID.class), rs.getObject("expected_date", LocalDate.class),
                        rs.getObject("observed_at", OffsetDateTime.class), rs.getBigDecimal("amount"),
                        rs.getString("currency"), rs.getString("occurrence_status")), customerId, from, to);
        return new Calendar(items, items.size(), from, to, dataAsOf);
    }

    @Transactional(readOnly = true)
    public MissedList missed(String customerId) {
        PaymentList payments = payments(customerId);
        Map<UUID, PaymentSummary> paymentById = payments.items().stream().collect(Collectors.toMap(
                PaymentSummary::recurringPaymentId, Function.identity()));
        List<MissedCandidate> items = jdbc.query("""
                select p.recurring_payment_id,count(*) missed_count,max(o.expected_date) latest_missed_date,
                       sum(o.amount) total_missed_amount
                  from recurring_payment p join recurring_payment_occurrence o using(recurring_payment_id)
                 where p.customer_id=? and o.occurrence_status='MISSED'
                 group by p.recurring_payment_id order by max(o.expected_date) desc,p.recurring_payment_id
                """, (rs, n) -> new MissedCandidate(
                        paymentById.get(rs.getObject("recurring_payment_id", UUID.class)),
                        rs.getLong("missed_count"), rs.getObject("latest_missed_date", LocalDate.class),
                        rs.getBigDecimal("total_missed_amount"), "MISSED_RECURRING"), customerId);
        return new MissedList(items, items.size(), payments.dataAsOf());
    }

    @Transactional(readOnly = true)
    public DuplicateList duplicates(String customerId) {
        PaymentList payments = payments(customerId);
        Map<UUID, PaymentSummary> paymentById = payments.items().stream().collect(Collectors.toMap(
                PaymentSummary::recurringPaymentId, Function.identity()));
        List<DuplicateCandidate> items = jdbc.query("""
                select p.recurring_payment_id,count(*) duplicate_count,max(o.expected_date) cycle_date,
                       sum(o.amount) duplicate_amount
                  from recurring_payment p join recurring_payment_occurrence o using(recurring_payment_id)
                 where p.customer_id=? and o.occurrence_status='DUPLICATE_CANDIDATE'
                 group by p.recurring_payment_id order by max(o.expected_date) desc,p.recurring_payment_id
                """, (rs, n) -> new DuplicateCandidate(
                        paymentById.get(rs.getObject("recurring_payment_id", UUID.class)),
                        rs.getLong("duplicate_count"), rs.getObject("cycle_date", LocalDate.class),
                        rs.getBigDecimal("duplicate_amount"), "DUPLICATE_PAYMENT_CANDIDATE"), customerId);
        return new DuplicateList(items, items.size(), payments.dataAsOf());
    }

    @Transactional(readOnly = true)
    public OccurrenceList occurrences(String customerId, UUID recurringPaymentId) {
        PaymentSummary payment = ownedPayment(customerId, recurringPaymentId);
        List<Occurrence> items = occurrenceRows(recurringPaymentId);
        return new OccurrenceList(recurringPaymentId, items, items.size(), payment.dataAsOf());
    }

    @Transactional
    public PaymentDetail updateReminder(String customerId, UUID recurringPaymentId,
                                        ReminderSettingsCommand command, String idempotencyKey, AuditActor actor) {
        return idempotency.execute("RECURRING_REMINDER:" + customerId + ":" + recurringPaymentId,
                idempotencyKey, command, PaymentDetail.class, RecurringPaymentErrorCode.IDEMPOTENCY_CONFLICT,
                () -> updateReminderOnce(customerId, recurringPaymentId, command, actor));
    }

    private PaymentDetail updateReminderOnce(String customerId, UUID recurringPaymentId,
                                        ReminderSettingsCommand command, AuditActor actor) {
        ownedPayment(customerId, recurringPaymentId);
        int changed = jdbc.update("""
                update recurring_payment
                   set reminder_enabled=?,reminder_lead_days=?,row_version=row_version+1,updated_at=?
                 where recurring_payment_id=? and customer_id=? and row_version=?
                """, command.enabled(), command.leadDays(), OffsetDateTime.now(clock),
                recurringPaymentId, customerId, command.expectedVersion());
        if (changed != 1) throw new BusinessException(RecurringPaymentErrorCode.VERSION_CONFLICT);
        PaymentDetail result = payment(customerId, recurringPaymentId);
        jdbc.update("""
                insert into recurring_payment_reminder_event(
                    event_id,recurring_payment_id,enabled_snapshot,lead_days_snapshot,version_snapshot,
                    actor_principal_id,actor_customer_id,actor_session_id,actor_type,occurred_at
                ) values(?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), recurringPaymentId, command.enabled(), command.leadDays(),
                result.payment().version(), actor.principalId(), actor.customerId(), actor.sessionId(),
                actor.actorType(), OffsetDateTime.now(clock));
        return result;
    }

    private PaymentSummary ownedPayment(String customerId, UUID recurringPaymentId) {
        List<PaymentSummary> rows = jdbc.query(PAYMENT_SELECT + """
                 where p.customer_id=? and p.recurring_payment_id=?
                """, this::paymentSummary, customerId, recurringPaymentId);
        if (rows.size() != 1) throw new BusinessException(RecurringPaymentErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private List<Occurrence> latestOccurrenceRows(UUID recurringPaymentId) {
        return jdbc.query("""
                select o.occurrence_id,o.expected_date,o.observed_at,o.amount,p.currency,o.occurrence_status
                  from recurring_payment_occurrence o join recurring_payment p using(recurring_payment_id)
                 where o.recurring_payment_id=? order by o.expected_date desc,o.observed_at desc nulls first,o.occurrence_id
                 limit 1
                """, (rs, n) -> occurrence(rs), recurringPaymentId);
    }

    private List<Occurrence> occurrenceRows(UUID recurringPaymentId) {
        return jdbc.query("""
                select o.occurrence_id,o.expected_date,o.observed_at,o.amount,p.currency,o.occurrence_status
                  from recurring_payment_occurrence o join recurring_payment p using(recurring_payment_id)
                 where o.recurring_payment_id=? order by o.expected_date desc,o.observed_at desc nulls first,o.occurrence_id
                """, (rs, n) -> occurrence(rs), recurringPaymentId);
    }

    private Occurrence occurrence(ResultSet rs) throws SQLException {
        return new Occurrence(
                        rs.getObject("occurrence_id", UUID.class), rs.getObject("expected_date", LocalDate.class),
                        rs.getObject("observed_at", OffsetDateTime.class), rs.getBigDecimal("amount"),
                        rs.getString("currency"), rs.getString("occurrence_status"));
    }

    private PaymentSummary paymentSummary(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentSummary(
                rs.getObject("recurring_payment_id", UUID.class), rs.getString("customer_id"),
                rs.getString("institution_id"), rs.getString("institution_name"), rs.getString("display_name"),
                rs.getString("payment_type"), rs.getString("category_code"), rs.getString("cadence"),
                rs.getBigDecimal("expected_amount"), rs.getString("currency"),
                rs.getObject("next_expected_date", LocalDate.class), rs.getInt("grace_days"),
                rs.getString("status"), rs.getString("observation_status"), rs.getString("provider_mode"),
                rs.getObject("data_as_of", LocalDate.class),
                new ReminderSettings(rs.getBoolean("reminder_enabled"), rs.getInt("reminder_lead_days"),
                        List.of("IN_APP"), false),
                rs.getLong("row_version"), false);
    }

    private LocalDate customerDataAsOf(String customerId) {
        List<LocalDate> dates = jdbc.query("select max(data_as_of) data_as_of from recurring_payment where customer_id=?",
                (rs, n) -> rs.getObject("data_as_of", LocalDate.class), customerId);
        if (dates.isEmpty() || dates.getFirst() == null) throw new BusinessException(RecurringPaymentErrorCode.NOT_FOUND);
        return dates.getFirst();
    }

    private LocalDate dataAsOf(List<PaymentSummary> items) {
        return items.stream().map(PaymentSummary::dataAsOf).max(LocalDate::compareTo).orElse(null);
    }
}
