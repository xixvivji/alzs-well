package com.alzswell.staffaccess;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StaffAccessIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final UUID ADMIN = UUID.fromString("94000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("94000000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedPrincipals() {
        jdbcTemplate.execute("truncate staff_access_grant_event, staff_access_grant");
        principal(ADMIN, "staff-access-admin", "DETECTION_ADMIN");
        principal(STAFF, "staff-access-protection", "PROTECTION_STAFF");
    }

    @Test
    void grantsEvaluatesAuditsAndRevokesCustomerScopedAccess() throws Exception {
        String request = """
                {"staffPrincipalId":"__STAFF_ID__","purposeCode":"CUSTOMER_PROTECTION_REVIEW",
                 "scopes":["CASE_READ","CASE_REVIEW"],"expiresAt":"__EXPIRES_AT__"}
                """.replace("__STAFF_ID__", STAFF.toString())
                .replace("__EXPIRES_AT__", OffsetDateTime.now().plusDays(1).toString());
        MvcResult created = mockMvc.perform(post("/api/v1/customers/{customerId}/staff-access-grants", CUSTOMER)
                        .with(admin()).header("Idempotency-Key", "staff-grant-0001")
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String grantId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/grantId").asText();

        mockMvc.perform(post("/api/v1/customers/{customerId}/staff-access-grants", CUSTOMER)
                        .with(admin()).header("Idempotency-Key", "staff-grant-0001")
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.grantId").value(grantId));
        mockMvc.perform(post("/api/v1/staff-access-policy/evaluations").with(admin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"staffPrincipalId\":\"" + STAFF + "\",\"customerId\":\"" + CUSTOMER
                                + "\",\"scope\":\"CASE_REVIEW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(true));
        mockMvc.perform(get("/api/v1/customers/{customerId}/staff-access-grants/{grantId}/audit", CUSTOMER, grantId)
                        .with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(2));
        mockMvc.perform(post("/api/v1/customers/{customerId}/staff-access-grants/{grantId}/revoke", CUSTOMER, grantId)
                        .with(admin()).contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\"업무 종료\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(post("/api/v1/staff-access-policy/evaluations").with(admin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"staffPrincipalId\":\"" + STAFF + "\",\"customerId\":\"" + CUSTOMER
                                + "\",\"scope\":\"CASE_REVIEW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));

        UUID eventId = jdbcTemplate.queryForObject(
                "select event_id from staff_access_grant_event order by occurred_at limit 1", UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from staff_access_grant_event where event_id=?", eventId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private void principal(UUID id, String loginId, String role) {
        jdbcTemplate.update("""
                insert into auth_principal(principal_id,login_id,customer_id,display_name,password_hash,status,created_at,updated_at)
                select ?,?,?,?,password_hash,'ACTIVE',now(),now() from auth_principal where login_id='synthetic-customer'
                on conflict(principal_id) do update set status='ACTIVE',updated_at=now()
                """, id, loginId, CUSTOMER, loginId);
        jdbcTemplate.update("insert into auth_principal_role(principal_id,role_code) values(?,?) on conflict do nothing",
                id, role);
    }

    private RequestPostProcessor admin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedPrincipal(ADMIN, CUSTOMER), null, List.of(
                new SimpleGrantedAuthority("STAFF_ACCESS_GRANT_READ"),
                new SimpleGrantedAuthority("STAFF_ACCESS_GRANT_WRITE"),
                new SimpleGrantedAuthority("STAFF_ACCESS_EVALUATE")));
        authentication.setDetails(new AuthenticatedSession(
                UUID.fromString("94000000-0000-0000-0000-000000000003")));
        return authentication(authentication);
    }
}
