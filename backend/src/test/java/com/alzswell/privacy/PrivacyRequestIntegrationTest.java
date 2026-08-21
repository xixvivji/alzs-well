package com.alzswell.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PrivacyRequestIntegrationTest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRequests() {
        jdbcTemplate.execute("truncate table customer_privacy_request_event, customer_privacy_request");
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = {"RETENTION_POLICY_READ", "PRIVACY_REQUEST_WRITE"})
    void readsPoliciesAndReceivesDeletionWithoutExecutingIt() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/retention-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false));

        MvcResult result = mockMvc.perform(post("/api/v1/customers/{customerId}/privacy/deletion-requests", CUSTOMER_ID)
                        .header("Idempotency-Key", "privacy-delete-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"CUSTOMER_PROFILE","targetReference":"PROFILE_001",
                                 "reasonCode":"CUSTOMER_REQUEST"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("LEGAL_HOLD_REVIEW"))
                .andExpect(jsonPath("$.data.legalExceptionCode").value("RETENTION_POLICY_REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.deletionExecuted").value(false))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false))
                .andReturn();

        UUID requestId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/requestId").asText());
        assertThat(jdbcTemplate.queryForObject("select count(*) from customer_privacy_request_event where request_id=?", Integer.class, requestId)).isEqualTo(1);
        UUID eventId = jdbcTemplate.queryForObject("select event_id from customer_privacy_request_event where request_id=?", UUID.class, requestId);
        assertThatThrownBy(() -> jdbcTemplate.update("delete from customer_privacy_request_event where event_id=?", eventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = "PRIVACY_REQUEST_WRITE")
    void replaysSameRequestAndRejectsIdempotencyConflict() throws Exception {
        String original = "{\"targetType\":\"CUSTOMER_PROFILE\",\"reasonCode\":\"CUSTOMER_REQUEST\"}";
        MvcResult first = mockMvc.perform(post("/api/v1/customers/{customerId}/privacy/deletion-requests", CUSTOMER_ID)
                        .header("Idempotency-Key", "privacy-replay-001")
                        .contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isCreated()).andReturn();
        String requestId = objectMapper.readTree(first.getResponse().getContentAsByteArray()).at("/data/requestId").asText();

        mockMvc.perform(post("/api/v1/customers/{customerId}/privacy/deletion-requests", CUSTOMER_ID)
                        .header("Idempotency-Key", "privacy-replay-001")
                        .contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.requestId").value(requestId));

        mockMvc.perform(post("/api/v1/customers/{customerId}/privacy/deletion-requests", CUSTOMER_ID)
                        .header("Idempotency-Key", "privacy-replay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"CUSTOMER_PREFERENCES\",\"reasonCode\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from customer_privacy_request", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from customer_privacy_request_event", Integer.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = CUSTOMER_ID, authorities = "PRIVACY_REQUEST_WRITE")
    void receivesCorrectionAndRejectsAnotherCustomersPath() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{customerId}/privacy/correction-requests", CUSTOMER_ID)
                        .header("Idempotency-Key", "privacy-correct-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"CUSTOMER_PREFERENCES","targetReference":"PREFERENCE_001",
                                 "reasonCode":"INACCURATE_DATA","correctedValue":"LARGE_TEXT_ENABLED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestType").value("CORRECTION"))
                .andExpect(jsonPath("$.data.deletionExecuted").value(false));

        mockMvc.perform(post("/api/v1/customers/{customerId}/privacy/deletion-requests", "OTHER_CUSTOMER")
                        .header("Idempotency-Key", "privacy-forbidden-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"CUSTOMER_PROFILE\",\"reasonCode\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "privacy-staff", authorities = "PRIVACY_REQUEST_WRITE_ALL")
    void staffDelegationRejectsUnknownCustomerBeforeCreatingARequest() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{customerId}/privacy/deletion-requests", "UNKNOWN_CUSTOMER")
                        .header("Idempotency-Key", "privacy-unknown-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"CUSTOMER_PROFILE\",\"reasonCode\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRIVACY_CUSTOMER_NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from customer_privacy_request", Integer.class)).isZero();
    }
}
