package com.alzswell.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TransactionIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final UUID ACCOUNT = UUID.fromString("95000000-0000-0000-0000-000000000001");
    private static final UUID TRANSACTION = UUID.fromString("95500000-0000-0000-0000-000000000003");
    private static final UUID COUNTERPARTY = UUID.fromString("95300000-0000-0000-0000-000000000004");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsListsSearchesSummariesCounterpartiesAndEnrichment() throws Exception {
        String firstPage = mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", ACCOUNT)
                        .param("limit", "2").with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].syntheticData").value(true))
                .andExpect(jsonPath("$.data.items[0].externalActionAvailable").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(firstPage).contains("nextCursor");

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", TRANSACTION).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transaction.description").value("안심마켓 생활비"))
                .andExpect(jsonPath("$.data.transaction.category").value("FOOD"))
                .andExpect(jsonPath("$.data.cancellationAvailable").value(false));

        mockMvc.perform(get("/api/v1/customers/{customerId}/transactions/search", CUSTOMER)
                        .param("q", "안심마켓").param("category", "FOOD").with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TRANSACTION.toString()));

        mockMvc.perform(get("/api/v1/customers/{customerId}/transactions/summary", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionCount").value(8))
                .andExpect(jsonPath("$.data.pendingExcluded").value(true));

        mockMvc.perform(get("/api/v1/customers/{customerId}/counterparties", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));

        mockMvc.perform(get("/api/v1/counterparties/{counterpartyId}/transaction-history", COUNTERPARTY)
                        .with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("안심마켓"))
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(get("/api/v1/transactions/{transactionId}/enrichment", TRANSACTION).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.normalizedDescription").value("안심마켓"))
                .andExpect(jsonPath("$.data.deterministic").value(true));
    }

    @Test
    void ownershipAuthorityCursorAndRangesAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{transactionId}", TRANSACTION)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "TRANSACTION_READ")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/transactions/{transactionId}", TRANSACTION).with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", ACCOUNT)
                        .param("cursor", UUID.randomUUID().toString()).with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSACTION_CURSOR_INVALID"));
        mockMvc.perform(get("/api/v1/customers/{customerId}/transactions/search", CUSTOMER)
                        .param("from", "2025-01-01").param("to", "2026-08-14").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSACTION_DATE_RANGE_INVALID"));
        mockMvc.perform(get("/api/v1/customers/{customerId}/transactions/search", CUSTOMER)
                        .param("minAmount", "1000").param("maxAmount", "100").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSACTION_AMOUNT_RANGE_INVALID"));
    }

    @Test
    @Transactional
    void categoryAndSafeNoteUseOptimisticLockAndAppendOnlyAudit() throws Exception {
        mockMvc.perform(put("/api/v1/transactions/{transactionId}/category", TRANSACTION)
                        .header("Idempotency-Key", "transaction-category-001")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SHOPPING\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("SHOPPING"))
                .andExpect(jsonPath("$.data.rowVersion").value(2));

        mockMvc.perform(put("/api/v1/transactions/{transactionId}/category", TRANSACTION)
                        .header("Idempotency-Key", "transaction-category-001")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SHOPPING\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowVersion").value(2));

        mockMvc.perform(put("/api/v1/transactions/{transactionId}/note", TRANSACTION)
                        .header("Idempotency-Key", "transaction-note-001")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"주말 장보기\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.note").value("주말 장보기"))
                .andExpect(jsonPath("$.data.rowVersion").value(3))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false));

        mockMvc.perform(put("/api/v1/transactions/{transactionId}/note", TRANSACTION)
                        .header("Idempotency-Key", "transaction-note-sensitive-001")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"계좌번호 123456789\",\"expectedVersion\":3}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/transactions/{transactionId}/category", TRANSACTION)
                        .header("Idempotency-Key", "transaction-category-002")
                        .with(writeUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FOOD\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_PREFERENCE_VERSION_CONFLICT"));

        Integer events = jdbc.queryForObject(
                "select count(*) from customer_transaction_preference_event where transaction_id=?",
                Integer.class, TRANSACTION);
        assertThat(events).isEqualTo(2);
    }

    @Test
    void transactionSnapshotsAndPreferenceEventsAreAppendOnly() {
        assertThatThrownBy(() -> jdbc.update(
                "update financial_transaction_snapshot set amount=1 where transaction_id=?", TRANSACTION))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "delete from transaction_enrichment_snapshot where transaction_id=?", TRANSACTION))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user(CUSTOMER).authorities(() -> "TRANSACTION_READ");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor writeUser() {
        return user(CUSTOMER).authorities(() -> "TRANSACTION_WRITE");
    }
}
