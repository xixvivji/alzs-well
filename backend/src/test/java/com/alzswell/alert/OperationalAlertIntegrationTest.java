package com.alzswell.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OperationalAlertIntegrationTest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAlerts() {
        jdbcTemplate.update("truncate operational_alert_context_event, operational_alert_audit_event");
        jdbcTemplate.update("""
                update operational_alert
                   set state = 'AWAITING_CONTEXT', alert_version = 1, deferred_until = null,
                       updated_at = created_at
                """);
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"ALERT_READ", "ALERT_RESPOND"})
    void retrievesCustomerAlertsDetailAndContextOptions() throws Exception {
        UUID alertId = alertId();
        mockMvc.perform(get("/api/v1/customers/{customerId}/alerts", CUSTOMER_ID)
                        .queryParam("state", "AWAITING_CONTEXT").queryParam("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ALERTS_RETRIEVED"))
                .andExpect(jsonPath("$.data.totalCount").value(2));

        mockMvc.perform(get("/api/v1/alerts/{alertId}", alertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alert.customerId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false));

        mockMvc.perform(get("/api/v1/alerts/{alertId}/context-options", alertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options.length()").value(3));
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"ALERT_READ", "ALERT_RESPOND"})
    void defersThenRespondsWithoutExecutingFinancialAction() throws Exception {
        UUID alertId = alertId();
        String deferredUntil = OffsetDateTime.now().plusHours(2).toString();
        mockMvc.perform(post("/api/v1/alerts/{alertId}/defer", alertId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"deferredUntil\":\"" + deferredUntil + "\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentState").value("DEFERRED"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", alertId)
                        .header("Idempotency-Key", "alert-context-0001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"responseCode\":\"NOT_SURE\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentState").value("BANK_REVIEW"))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false))
                .andExpect(jsonPath("$.data.externalNotificationSent").value(false));

        mockMvc.perform(get("/api/v1/alerts/{alertId}/audit", alertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2));

        var contextActor = jdbcTemplate.queryForMap("""
                select actor_principal_id, actor_customer_id, actor_session_id, actor_type
                  from operational_alert_context_event where alert_id = ?
                """, alertId);
        assertThat(contextActor.get("actor_principal_id")).isNull();
        assertThat(contextActor.get("actor_customer_id")).isEqualTo(CUSTOMER_ID);
        assertThat(contextActor.get("actor_session_id")).isNull();
        assertThat(contextActor.get("actor_type")).isEqualTo("CUSTOMER");
        Integer customerAuditEvents = jdbcTemplate.queryForObject("""
                select count(*) from operational_alert_audit_event
                 where alert_id = ? and actor_customer_id = ? and actor_type = 'CUSTOMER'
                """, Integer.class, alertId, CUSTOMER_ID);
        assertThat(customerAuditEvents).isEqualTo(2);

        UUID contextEventId=jdbcTemplate.queryForObject(
                "select context_event_id from operational_alert_context_event where alert_id=?",
                UUID.class,alertId);
        UUID auditEventId=jdbcTemplate.queryForObject(
                "select audit_event_id from operational_alert_audit_event where alert_id=? limit 1",
                UUID.class,alertId);
        assertThatThrownBy(()->jdbcTemplate.update("""
                update operational_alert_context_event set created_at=created_at
                 where context_event_id=?
                """,contextEventId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update(
                "delete from operational_alert_context_event where context_event_id=?",contextEventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update("""
                update operational_alert_audit_event set created_at=created_at
                 where audit_event_id=?
                """,auditEventId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbcTemplate.update(
                "delete from operational_alert_audit_event where audit_event_id=?",auditEventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"ALERT_READ", "ALERT_RESPOND"})
    void replaysSameContextRequestAndRejectsDifferentRequest() throws Exception {
        UUID alertId = alertId();
        String first = "{\"responseCode\":\"EXPECTED_CHANGE\",\"expectedVersion\":1}";
        MvcResult result = mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", alertId)
                        .header("Idempotency-Key", "alert-context-0002")
                        .contentType(APPLICATION_JSON).content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentState").value("CLOSED_NORMAL"))
                .andReturn();
        JsonNode original = objectMapper.readTree(result.getResponse().getContentAsByteArray());

        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", alertId)
                        .header("Idempotency-Key", "alert-context-0002")
                        .contentType(APPLICATION_JSON).content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(original.path("data").path("version").asLong()))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));

        mockMvc.perform(post("/api/v1/alerts/{alertId}/context-responses", alertId)
                        .header("Idempotency-Key", "alert-context-0002")
                        .contentType(APPLICATION_JSON)
                        .content("{\"responseCode\":\"NOT_SURE\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @WithMockUser(username = "ANOTHER_CUSTOMER", authorities = {"ALERT_READ", "ALERT_RESPOND"})
    void hidesAnotherCustomersAlert() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/{alertId}", alertId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/{alertId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private UUID alertId() {
        UUID alertId = jdbcTemplate.queryForObject("""
                select alert_id from operational_alert
                 where customer_id = ? order by alert_id limit 1
                """, UUID.class, CUSTOMER_ID);
        assertThat(alertId).isNotNull();
        return alertId;
    }

}
