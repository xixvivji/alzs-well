package com.alzswell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void flywayCreatesTheFoundationTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('demo_session', 'decision_audit')
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(2);
    }

    @Test
    void healthApiReturnsTheSameTraceIdInTheHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/v1/system/health")
                        .header("X-Trace-Id", "integration-trace-0001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "integration-trace-0001"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SYSTEM_HEALTHY"))
                .andExpect(jsonPath("$.data.syntheticDataOnly").value(true))
                .andExpect(jsonPath("$.data.externalActionsEnabled").value(false))
                .andExpect(jsonPath("$.traceId").value("integration-trace-0001"));
    }
}
