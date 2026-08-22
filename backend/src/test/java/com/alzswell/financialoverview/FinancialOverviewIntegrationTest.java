package com.alzswell.financialoverview;

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
class FinancialOverviewIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsAllEightFinancialOverviewApis() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/financial-summary", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAssets").value(49650000))
                .andExpect(jsonPath("$.data.totalLiabilities").value(12800000))
                .andExpect(jsonPath("$.data.netAssets").value(36850000))
                .andExpect(jsonPath("$.data.syntheticData").value(true))
                .andExpect(jsonPath("$.data.externalExecutionAvailable").value(false));

        mockMvc.perform(get("/api/v1/customers/{customerId}/asset-breakdown", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.totalAssets").value(49650000));

        mockMvc.perform(get("/api/v1/customers/{customerId}/asset-trends", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.items[2].totalAssets").value(49650000));

        mockMvc.perform(get("/api/v1/customers/{customerId}/liabilities", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].maskedReference").value("CD-***-**02"))
                .andExpect(jsonPath("$.data.items[0].repaymentAvailable").value(false));

        mockMvc.perform(get("/api/v1/customers/{customerId}/cashflow-summary", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionCount").value(8))
                .andExpect(jsonPath("$.data.totalInflow").value(5619600))
                .andExpect(jsonPath("$.data.totalOutflow").value(1835200));

        mockMvc.perform(get("/api/v1/customers/{customerId}/expense-summary", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpense").value(1835200));

        mockMvc.perform(get("/api/v1/customers/{customerId}/asset-calendar", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.items[0].externalActionAvailable").value(false));

        mockMvc.perform(get("/api/v1/customers/{customerId}/data-freshness", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.allFresh").value(true))
                .andExpect(jsonPath("$.data.items[0].freshnessStatus").value("FRESH"));
    }

    @Test
    void ownershipAuthorityAndDateLimitsAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/financial-summary", CUSTOMER)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "FINANCIAL_OVERVIEW_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers/{customerId}/financial-summary", CUSTOMER).with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers/{customerId}/cashflow-summary", CUSTOMER)
                        .param("from", "2025-01-01").param("to", "2026-08-14").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINANCIAL_OVERVIEW_DATE_RANGE_INVALID"));
        mockMvc.perform(get("/api/v1/customers/{customerId}/asset-calendar", CUSTOMER)
                        .param("from", "2026-08-14").param("to", "2027-01-01").with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINANCIAL_OVERVIEW_DATE_RANGE_INVALID"));
    }

    @Test
    void liabilityAndCalendarSnapshotsAreAppendOnly() {
        assertThatThrownBy(() -> jdbc.update(
                "update customer_liability_snapshot set outstanding_amount=0 where liability_id=?",
                UUID.fromString("95600000-0000-0000-0000-000000000001")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "delete from customer_asset_calendar_snapshot where event_id=?",
                UUID.fromString("95700000-0000-0000-0000-000000000001")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user(CUSTOMER).authorities(() -> "FINANCIAL_OVERVIEW_READ");
    }
}
