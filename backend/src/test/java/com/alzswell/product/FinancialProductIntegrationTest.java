package com.alzswell.product;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
class FinancialProductIntegrationTest {
 static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
 @Autowired MockMvc mockMvc;@Autowired JdbcTemplate jdbc;
 private org.springframework.test.web.servlet.request.RequestPostProcessor read(){return user(CUSTOMER).authorities(()->"FINANCIAL_PRODUCT_READ");}
 private org.springframework.test.web.servlet.request.RequestPostProcessor simulate(){return user(CUSTOMER).authorities(()->"FINANCIAL_PRODUCT_SIMULATE");}
 @Test void readsAndSimulatesAllEightProductApis() throws Exception {
  mockMvc.perform(get("/api/v1/deposit-products").with(read())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.externalProviderCalled").value(false));
  mockMvc.perform(get("/api/v1/deposit-products/{id}","97400000-0000-0000-0000-000000000001").with(read())).andExpect(status().isOk()).andExpect(jsonPath("$.data.product.institutionName").value("안심은행")).andExpect(jsonPath("$.data.applicationAvailable").value(false));
  mockMvc.perform(get("/api/v1/deposit-products/{id}/rates","97400000-0000-0000-0000-000000000001").with(read())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));
  mockMvc.perform(post("/api/v1/deposit-products/{id}/interest-simulations","97400000-0000-0000-0000-000000000001").with(simulate()).contentType(APPLICATION_JSON).content("{\"principalAmount\":20000000,\"termMonths\":12}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.grossInterest").value(640000)).andExpect(jsonPath("$.data.estimatedTax").value(98560)).andExpect(jsonPath("$.data.estimatedMaturityAmount").value(20541440)).andExpect(jsonPath("$.data.personalized").value(false));
  mockMvc.perform(get("/api/v1/deposit-holdings/{id}/maturity-options","97000000-0000-0000-0000-000000000001").with(read())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.selectable").value(false));
  mockMvc.perform(get("/api/v1/loan-products").with(read())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
  mockMvc.perform(get("/api/v1/loan-products/{id}","97700000-0000-0000-0000-000000000001").with(read())).andExpect(status().isOk()).andExpect(jsonPath("$.data.creditAssessmentPerformed").value(false));
  mockMvc.perform(post("/api/v1/loan-products/{id}/repayment-simulations","97700000-0000-0000-0000-000000000001").with(simulate()).contentType(APPLICATION_JSON).content("{\"principalAmount\":12000000,\"termMonths\":12,\"annualInterestRate\":4.2000}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.monthlyPrincipal").value(1000000)).andExpect(jsonPath("$.data.firstPaymentAmount").value(1042000)).andExpect(jsonPath("$.data.totalInterest").value(273000)).andExpect(jsonPath("$.data.applicationAvailable").value(false));
 }
 @Test void authorityOwnershipAndProductRangesAreEnforced() throws Exception {
  mockMvc.perform(get("/api/v1/deposit-products").with(user(CUSTOMER))).andExpect(status().isForbidden());
  mockMvc.perform(get("/api/v1/deposit-holdings/{id}/maturity-options","97000000-0000-0000-0000-000000000001").with(user("OTHER").authorities(()->"FINANCIAL_PRODUCT_READ"))).andExpect(status().isNotFound());
  mockMvc.perform(post("/api/v1/deposit-products/{id}/interest-simulations","97400000-0000-0000-0000-000000000001").with(simulate()).contentType(APPLICATION_JSON).content("{\"principalAmount\":20000000,\"termMonths\":60}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FINANCIAL_PRODUCT_SIMULATION_OUT_OF_RANGE"));
 }
 @Test void productSnapshotsAreAppendOnly(){
  assertThatThrownBy(()->jdbc.update("update deposit_product_snapshot set status='AVAILABLE'")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
  assertThatThrownBy(()->jdbc.update("delete from loan_product_snapshot")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
 }
}
