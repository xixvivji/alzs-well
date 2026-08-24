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
class FinancialHoldingIntegrationTest {
 static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired MockMvc mockMvc; @Autowired JdbcTemplate jdbc;
 private org.springframework.test.web.servlet.request.RequestPostProcessor reader(){return user(CUSTOMER).authorities(()->"FINANCIAL_OVERVIEW_READ");}

 @Test void readsAllEightHoldingApis() throws Exception {
  mockMvc.perform(get("/api/v1/customers/{id}/deposit-holdings",CUSTOMER).with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].institutionName").value("안심은행"));
  mockMvc.perform(get("/api/v1/deposit-holdings/{id}","97000000-0000-0000-0000-000000000001").with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.expectedMaturityAmount").value(20640000)).andExpect(jsonPath("$.data.externalActionExecuted").value(false));
  mockMvc.perform(get("/api/v1/customers/{id}/loan-holdings",CUSTOMER).with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
  mockMvc.perform(get("/api/v1/loan-holdings/{id}","95600000-0000-0000-0000-000000000001").with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.loan.institutionName").value("안심은행")).andExpect(jsonPath("$.data.repaymentAvailable").value(false));
  mockMvc.perform(get("/api/v1/loan-holdings/{id}/repayment-schedule","95600000-0000-0000-0000-000000000001").with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.paymentExecutionAvailable").value(false));
  mockMvc.perform(get("/api/v1/customers/{id}/investment-accounts",CUSTOMER).with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].institutionName").value("안심증권"));
  mockMvc.perform(get("/api/v1/investment-accounts/{id}/portfolio","97200000-0000-0000-0000-000000000001").with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.investedMarketValue").value(10000000)).andExpect(jsonPath("$.data.orderAvailable").value(false));
  mockMvc.perform(get("/api/v1/investment-accounts/{id}/positions","97200000-0000-0000-0000-000000000001").with(reader())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.externalProviderCalled").value(false));
 }
 @Test void authorityAndOwnershipAreEnforced() throws Exception {
  mockMvc.perform(get("/api/v1/customers/{id}/deposit-holdings",CUSTOMER).with(user(CUSTOMER))).andExpect(status().isForbidden());
  mockMvc.perform(get("/api/v1/customers/{id}/loan-holdings",CUSTOMER).with(user("OTHER").authorities(()->"FINANCIAL_OVERVIEW_READ"))).andExpect(status().isForbidden());
  mockMvc.perform(get("/api/v1/investment-accounts/{id}/positions",UUID.randomUUID()).with(reader())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVESTMENT_ACCOUNT_NOT_FOUND"));
 }
 @Test void newSnapshotsAreAppendOnly(){
  assertThatThrownBy(()->jdbc.update("update customer_deposit_holding_snapshot set principal_amount=0")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
  assertThatThrownBy(()->jdbc.update("delete from investment_position_snapshot")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
 }
}
