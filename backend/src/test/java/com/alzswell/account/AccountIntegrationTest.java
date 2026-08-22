package com.alzswell.account;

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
class AccountIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final UUID CHECKING = UUID.fromString("95000000-0000-0000-0000-000000000001");
    private static final UUID DEPOSIT = UUID.fromString("95000000-0000-0000-0000-000000000003");
    private static final UUID STATEMENT = UUID.fromString("95200000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsOwnedMaskedAccountsBalancesRestrictionsInterestAndStatements() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/accounts", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.dataAsOf").value("2026-08-14"))
                .andExpect(jsonPath("$.data.items[0].syntheticData").value(true))
                .andExpect(jsonPath("$.data.items[0].externalExecutionAvailable").value(false));

        mockMvc.perform(get("/api/v1/accounts/{id}", CHECKING).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.maskedAccountNumber").value("110-***-**01"))
                .andExpect(jsonPath("$.data.accountNumberFullyMasked").value(true))
                .andExpect(jsonPath("$.data.transferAvailable").value(false));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", CHECKING).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance").value(18450000))
                .andExpect(jsonPath("$.data.availableBalance").value(18000000));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance-history", CHECKING).with(readUser()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/v1/accounts/{id}/restrictions", DEPOSIT).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].restrictionCode").value("MATURITY_WITHDRAWAL_ONLY"))
                .andExpect(jsonPath("$.data.items[0].externalActionAvailable").value(false));

        mockMvc.perform(get("/api/v1/accounts/{id}/interest-summary", DEPOSIT).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interestType").value("FIXED"))
                .andExpect(jsonPath("$.data.accruedInterest").value(320000));

        mockMvc.perform(get("/api/v1/accounts/{id}/statements", CHECKING).with(readUser()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].fileAvailable").value(false));

        mockMvc.perform(get("/api/v1/accounts/{id}/statements/{statementId}", CHECKING, STATEMENT)
                        .with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statement.transactionCount").value(9))
                .andExpect(jsonPath("$.data.externalDownloadAvailable").value(false));
    }

    @Test
    void accountOwnershipAuthorityAndDateRangeAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", CHECKING)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "ACCOUNT_READ")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/accounts/{id}", CHECKING).with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/{id}/balance-history", CHECKING)
                        .param("from", "2025-01-01").param("to", "2026-12-31").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BALANCE_DATE_RANGE_INVALID"));
        mockMvc.perform(get("/api/v1/accounts/{id}/statements/{statementId}", CHECKING, UUID.randomUUID())
                        .with(readUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATEMENT_NOT_FOUND"));
    }

    @Test
    void accountSnapshotsAreAppendOnly() {
        assertThatThrownBy(() -> jdbc.update(
                "delete from customer_account_statement_snapshot where statement_id=?", STATEMENT))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "update customer_account_snapshot set current_balance=0 where account_id=?", CHECKING))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user(CUSTOMER).authorities(() -> "ACCOUNT_READ");
    }
}
