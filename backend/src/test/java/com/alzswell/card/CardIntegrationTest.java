package com.alzswell.card;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CardIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final UUID CREDIT_CARD = UUID.fromString("96000000-0000-0000-0000-000000000001");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsAllSixCardApisWithoutExternalActions() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/cards", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].maskedCardNumber")
                        .value("안심카드 ****-****-****-1001"))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false));

        mockMvc.perform(get("/api/v1/cards/{cardId}", CREDIT_CARD).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.cardType").value("CREDIT"))
                .andExpect(jsonPath("$.data.lockAvailable").value(false))
                .andExpect(jsonPath("$.data.replacementAvailable").value(false));

        mockMvc.perform(get("/api/v1/cards/{cardId}/transactions", CREDIT_CARD).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.items[0].merchantDisplayName").value("합성마트 01"));

        mockMvc.perform(get("/api/v1/cards/{cardId}/statements", CREDIT_CARD).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].remainingDueAmount").value(800000))
                .andExpect(jsonPath("$.data.downloadable").value(false));

        mockMvc.perform(get("/api/v1/cards/{cardId}/payment-due", CREDIT_CARD).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(800000))
                .andExpect(jsonPath("$.data.paymentAvailable").value(false))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false));

        mockMvc.perform(get("/api/v1/cards/{cardId}/limits", CREDIT_CARD).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLimitAmount").value(5000000))
                .andExpect(jsonPath("$.data.availableLimitAmount").value(3800000))
                .andExpect(jsonPath("$.data.limitChangeAvailable").value(false));
    }

    @Test
    void paginationDateRangeAuthorityAndOwnershipAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardId}/transactions", CREDIT_CARD)
                        .param("limit", "2").with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor")
                        .value("96100000-0000-0000-0000-000000000002"));
        mockMvc.perform(get("/api/v1/cards/{cardId}/transactions", CREDIT_CARD)
                        .param("cursor", "96100000-0000-0000-0000-000000000002")
                        .param("limit", "2").with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc.perform(get("/api/v1/cards/{cardId}/transactions", CREDIT_CARD)
                        .param("from", "2025-01-01").param("to", "2026-08-14").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARD_TRANSACTION_DATE_RANGE_INVALID"));

        mockMvc.perform(get("/api/v1/customers/{customerId}/cards", CUSTOMER).with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers/{customerId}/cards", CUSTOMER)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "CARD_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/cards/{cardId}", CREDIT_CARD)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "CARD_READ")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }

    @Test
    void invalidCursorAndImmutableSnapshotsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardId}/transactions", CREDIT_CARD)
                        .param("cursor", "96100000-0000-0000-0000-999999999999").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARD_TRANSACTION_CURSOR_INVALID"));

        assertThatThrownBy(() -> jdbc.update(
                "update customer_card_snapshot set current_due_amount=0 where card_id=?", CREDIT_CARD))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "delete from card_transaction_snapshot where card_transaction_id=?",
                UUID.fromString("96100000-0000-0000-0000-000000000001")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "update card_statement_snapshot set remaining_due_amount=0 where statement_id=?",
                UUID.fromString("96200000-0000-0000-0000-000000000001")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("""
                insert into customer_card_snapshot values(
                    '96000000-0000-0000-0000-999999999999',?,'SYNTHETIC_BANK',
                    '95000000-0000-0000-0000-000000000004','잘못된 연결','안심카드 ****-****-****-9999',
                    'CREDIT','LOCAL','ACTIVE',15,'2026-08-15',0,0,1000000,1000000,'KRW',
                    'SYNTHETIC_PROVIDER','2026-08-14',repeat('a',64)
                )
                """, CUSTOMER)).isInstanceOf(DataAccessException.class);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user(CUSTOMER).authorities(() -> "CARD_READ");
    }
}
