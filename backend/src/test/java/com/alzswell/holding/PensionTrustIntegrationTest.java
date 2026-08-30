package com.alzswell.holding;

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

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class PensionTrustIntegrationTest {
 static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
 @Autowired MockMvc mockMvc; @Autowired JdbcTemplate jdbc;
 private org.springframework.test.web.servlet.request.RequestPostProcessor reader(){return user(CUSTOMER).authorities(()->"FINANCIAL_OVERVIEW_READ");}

 @Test void readsPensionAndTrustWithoutExternalActions() throws Exception {
  mockMvc.perform(get("/api/v1/customers/{id}/pension-holdings",CUSTOMER).with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].institutionName").value("안심은행")).andExpect(jsonPath("$.data.externalProviderCalled").value(false));
  mockMvc.perform(get("/api/v1/pension-holdings/{id}/projection","97400000-0000-0000-0000-000000000001").with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.guaranteed").value(false)).andExpect(jsonPath("$.data.recommendationProvided").value(false)).andExpect(jsonPath("$.data.externalActionExecuted").value(false));
  mockMvc.perform(get("/api/v1/customers/{id}/trust-holdings",CUSTOMER).with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].maskedContractReference").value("TRU-***-**01"));
  mockMvc.perform(get("/api/v1/trust-holdings/{id}","97600000-0000-0000-0000-000000000001").with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.beneficiaryIdentityProvided").value(false)).andExpect(jsonPath("$.data.contractActionAvailable").value(false)).andExpect(jsonPath("$.data.externalProviderCalled").value(false));
 }

 @Test void authorityAndOwnershipAreEnforced() throws Exception {
  mockMvc.perform(get("/api/v1/customers/{id}/pension-holdings",CUSTOMER).with(user(CUSTOMER))).andExpect(status().isForbidden());
  mockMvc.perform(get("/api/v1/customers/{id}/trust-holdings",CUSTOMER).with(user("OTHER").authorities(()->"FINANCIAL_OVERVIEW_READ"))).andExpect(status().isForbidden());
  mockMvc.perform(get("/api/v1/pension-holdings/{id}/projection",UUID.randomUUID()).with(reader())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PENSION_HOLDING_NOT_FOUND"));
  mockMvc.perform(get("/api/v1/trust-holdings/{id}",UUID.randomUUID()).with(reader())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRUST_HOLDING_NOT_FOUND"));
 }

 @Test void pensionAndTrustSnapshotsAreAppendOnly(){
  assertThatThrownBy(()->jdbc.update("update customer_pension_holding_snapshot set current_value=0")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
  assertThatThrownBy(()->jdbc.update("delete from pension_projection_snapshot")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
  assertThatThrownBy(()->jdbc.update("delete from customer_trust_holding_snapshot")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
 }
}
