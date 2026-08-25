package com.alzswell.staffaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=3000"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StaffAccessPoolIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final UUID STAFF = UUID.fromString("96000000-0000-0000-0000-000000000001");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void deniedAccessReturnsForbiddenAndPersistsAuditWithSingleConnection() throws Exception {
        jdbcTemplate.update("""
                insert into auth_principal(principal_id,login_id,customer_id,display_name,password_hash,status,
                    created_at,updated_at)
                select ?,'single-pool-staff',customer_id,'단일 풀 직원',password_hash,'ACTIVE',now(),now()
                  from auth_principal where login_id='synthetic-customer'
                """, STAFF);
        jdbcTemplate.update("insert into auth_principal_role(principal_id,role_code) values(?,'DETECTION_ADMIN')", STAFF);
        UUID alertId = jdbcTemplate.queryForObject(
                "select alert_id from operational_alert where customer_id=? order by alert_id limit 1",
                UUID.class, CUSTOMER);
        var principal = new AuthenticatedPrincipal(STAFF, CUSTOMER);
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ALERT_READ_ALL")));

        mockMvc.perform(get("/api/v1/alerts/{alertId}", alertId).with(authentication(auth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from staff_access_decision_audit_event
                 where staff_principal_id=? and customer_id=? and allowed=false
                """, Integer.class, STAFF, CUSTOMER)).isEqualTo(1);
    }
}
