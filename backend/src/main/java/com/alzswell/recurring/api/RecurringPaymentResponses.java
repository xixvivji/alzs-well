package com.alzswell.recurring.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class RecurringPaymentResponses {
    private RecurringPaymentResponses() {}

    public record ReminderSettings(boolean enabled, int leadDays, List<String> channels,
                                   boolean externalDeliveryEnabled) {}

    public record PaymentSummary(UUID recurringPaymentId, String customerId, String institutionId,
                                 String institutionName, String displayName, String paymentType,
                                 String categoryCode, String cadence, BigDecimal expectedAmount,
                                 String currency, LocalDate nextExpectedDate, int graceDays, String status,
                                 String observationStatus, String providerMode, LocalDate dataAsOf,
                                 ReminderSettings reminderSettings, long version,
                                 boolean externalExecutionAvailable) {}

    public record PaymentList(List<PaymentSummary> items, int total, LocalDate dataAsOf) {}

    public record Occurrence(UUID occurrenceId, LocalDate expectedDate, OffsetDateTime observedAt,
                             BigDecimal amount, String currency, String status) {}

    public record PaymentDetail(PaymentSummary payment, Occurrence latestOccurrence,
                                boolean cancellationAvailable, boolean externalActionExecuted) {}

    public record CalendarEntry(UUID recurringPaymentId, String displayName, UUID occurrenceId,
                                LocalDate expectedDate, OffsetDateTime observedAt, BigDecimal amount,
                                String currency, String status) {}

    public record Calendar(List<CalendarEntry> items, int total, LocalDate from, LocalDate to,
                           LocalDate dataAsOf) {}

    public record MissedCandidate(PaymentSummary payment, long missedCount,
                                  LocalDate latestMissedDate, BigDecimal totalMissedAmount,
                                  String reasonCode) {}

    public record MissedList(List<MissedCandidate> items, int total, LocalDate dataAsOf) {}

    public record DuplicateCandidate(PaymentSummary payment, long duplicateOccurrenceCount,
                                     LocalDate cycleDate, BigDecimal duplicateAmount,
                                     String reasonCode) {}

    public record DuplicateList(List<DuplicateCandidate> items, int total, LocalDate dataAsOf) {}

    public record OccurrenceList(UUID recurringPaymentId, List<Occurrence> items, int total,
                                 LocalDate dataAsOf) {}
}
