package com.alzswell.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SupportContentIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void readsFaqsAndSyntheticNoticesWithStableOrdering() throws Exception {
        mockMvc.perform(get("/api/v1/support/faqs").with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUPPORT_FAQS_RETRIEVED"))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.syntheticData").value(true))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false));

        mockMvc.perform(get("/api/v1/support/faqs")
                        .param("category", "SECURITY")
                        .with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].categoryCode").value("SECURITY"));

        mockMvc.perform(get("/api/v1/support/notices").with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUPPORT_NOTICES_RETRIEVED"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].institutionName").value("안심은행"))
                .andExpect(jsonPath("$.data.items[0].important").value(true))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false));
    }

    @Test
    void authorityAndInputBoundariesAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/support/faqs").with(user("SYN_CUSTOMER_FIN_MGMT_001")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/support/faqs")
                        .param("category", "UNKNOWN")
                        .with(readUser()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/support/notices")
                        .param("from", "2026-08-25")
                        .param("to", "2026-08-24")
                        .with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUPPORT_NOTICE_PERIOD_INVALID"));

        mockMvc.perform(get("/api/v1/support/notices")
                        .param("from", "2025-08-24")
                        .param("to", "2026-08-25")
                        .with(readUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUPPORT_NOTICE_PERIOD_INVALID"));

        mockMvc.perform(get("/api/v1/support/notices")
                        .param("limit", "101")
                        .with(readUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportSnapshotsAreAppendOnly() {
        assertThatThrownBy(() -> jdbc.update(
                "update support_faq_snapshot set display_order = display_order + 1"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from support_notice_snapshot"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user("SYN_CUSTOMER_FIN_MGMT_001").authorities(() -> "SUPPORT_CONTENT_READ");
    }
}
