package com.alzswell.recurring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RecurringPaymentIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final UUID MISSED_PAYMENT = UUID.fromString("94000000-0000-0000-0000-000000000001");
    private static final UUID DUPLICATE_PAYMENT = UUID.fromString("94000000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetReminder() {
        jdbc.update("delete from recurring_payment_reminder_event");
        jdbc.update("update recurring_payment set reminder_enabled=true,reminder_lead_days=3,row_version=0 "
                + "where recurring_payment_id=?", MISSED_PAYMENT);
    }

    @Test
    void readsListDetailCalendarMissedDuplicatesAndOccurrences() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/recurring-payments", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.dataAsOf").value("2026-08-14"))
                .andExpect(jsonPath("$.data.items[0].externalExecutionAvailable").value(false));

        mockMvc.perform(get("/api/v1/recurring-payments/{id}", MISSED_PAYMENT).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.observationStatus").value("MISSED_CANDIDATE"))
                .andExpect(jsonPath("$.data.latestOccurrence.status").value("EXPECTED"))
                .andExpect(jsonPath("$.data.cancellationAvailable").value(false));

        mockMvc.perform(get("/api/v1/customers/{customerId}/recurring-payments/calendar", CUSTOMER)
                        .param("from", "2026-08-01").param("to", "2026-09-30").with(readUser()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(6));

        mockMvc.perform(get("/api/v1/customers/{customerId}/recurring-payments/missed", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].missedCount").value(3))
                .andExpect(jsonPath("$.data.items[0].reasonCode").value("MISSED_RECURRING"));

        mockMvc.perform(get("/api/v1/customers/{customerId}/recurring-payments/duplicates", CUSTOMER)
                        .with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].payment.recurringPaymentId")
                        .value(DUPLICATE_PAYMENT.toString()))
                .andExpect(jsonPath("$.data.items[0].duplicateOccurrenceCount").value(1));

        mockMvc.perform(get("/api/v1/recurring-payments/{id}/occurrences", MISSED_PAYMENT).with(readUser()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    void reminderUpdateUsesOptimisticLockAndWritesImmutableAudit() throws Exception {
        String body = "{\"enabled\":false,\"leadDays\":2,\"expectedVersion\":0}";
        mockMvc.perform(put("/api/v1/recurring-payments/{id}/reminder-settings", MISSED_PAYMENT)
                        .header("Idempotency-Key", "reminder-update-001")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.reminderSettings.enabled").value(false))
                .andExpect(jsonPath("$.data.payment.reminderSettings.externalDeliveryEnabled").value(false))
                .andExpect(jsonPath("$.data.payment.version").value(1));

        mockMvc.perform(put("/api/v1/recurring-payments/{id}/reminder-settings", MISSED_PAYMENT)
                        .header("Idempotency-Key", "reminder-update-001")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.version").value(1));

        mockMvc.perform(put("/api/v1/recurring-payments/{id}/reminder-settings", MISSED_PAYMENT)
                        .header("Idempotency-Key", "reminder-update-002")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECURRING_PAYMENT_VERSION_CONFLICT"));

        UUID eventId = jdbc.queryForObject("select event_id from recurring_payment_reminder_event", UUID.class);
        assertThatThrownBy(() -> jdbc.update(
                "delete from recurring_payment_reminder_event where event_id=?", eventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void ownershipAuthorityAndDateRangeAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/recurring-payments/{id}", MISSED_PAYMENT)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "RECURRING_PAYMENT_READ")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/recurring-payments/{id}/reminder-settings", MISSED_PAYMENT)
                        .header("Idempotency-Key", "reminder-auth-001")
                        .with(readUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"leadDays\":1,\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers/{customerId}/recurring-payments/calendar", CUSTOMER)
                        .param("from", "2026-08-01").param("to", "2026-12-31").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECURRING_PAYMENT_DATE_RANGE_INVALID"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user(CUSTOMER).authorities(() -> "RECURRING_PAYMENT_READ");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor writeUser() {
        return user(CUSTOMER).authorities(() -> "RECURRING_PAYMENT_WRITE");
    }
}
