package com.alzswell.investment;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedPrincipal;
import com.alzswell.identity.application.AuthSessionService.AuthenticatedSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class InvestmentMarketIntegrationTest {
 static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
 static final UUID PRINCIPAL=UUID.fromString("11111111-2222-4333-8444-555555555555");
 static final UUID SESSION=UUID.fromString("66666666-7777-4888-8999-000000000000");
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
 @Autowired MockMvc mockMvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper objectMapper;
 private org.springframework.test.web.servlet.request.RequestPostProcessor market(){return user(CUSTOMER).authorities(()->"INVESTMENT_MARKET_READ");}
 private org.springframework.test.web.servlet.request.RequestPostProcessor watchRead(){return user(CUSTOMER).authorities(()->"INVESTMENT_WATCHLIST_READ");}
 private org.springframework.test.web.servlet.request.RequestPostProcessor watchWrite(){var token=UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedPrincipal(PRINCIPAL,CUSTOMER),null,List.of(new SimpleGrantedAuthority("INVESTMENT_WATCHLIST_WRITE")));token.setDetails(new AuthenticatedSession(SESSION));return authentication(token);}
 @Test void readsAllMarketAndWatchlistApis() throws Exception {
  mockMvc.perform(get("/api/v1/investment-accounts/{id}/orders","97200000-0000-0000-0000-000000000001").with(market())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.orderAvailable").value(false));
  mockMvc.perform(get("/api/v1/market-instruments/{id}/quote","97800000-0000-0000-0000-000000000001").with(market())).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPrice").value(550000)).andExpect(jsonPath("$.data.delayed").value(true)).andExpect(jsonPath("$.data.externalProviderCalled").value(false));
  mockMvc.perform(get("/api/v1/market-instruments/{id}/chart","97800000-0000-0000-0000-000000000001").param("from","2026-08-12").param("to","2026-08-14").with(market())).andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(3)).andExpect(jsonPath("$.data.items[2].closePrice").value(550000));
  mockMvc.perform(get("/api/v1/customers/{id}/watchlist",CUSTOMER).with(watchRead())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.version").isNumber());
 }
 @Test void watchlistReplaceIsOwnedVersionedAndIdempotent() throws Exception {
  String body="{\"instrumentIds\":[\"97800000-0000-0000-0000-000000000002\",\"97800000-0000-0000-0000-000000000001\"],\"expectedVersion\":1}";
  for(int i=0;i<2;i++)mockMvc.perform(put("/api/v1/customers/{id}/watchlist",CUSTOMER).header("Idempotency-Key","watchlist-replace-001").contentType(APPLICATION_JSON).content(body).with(watchWrite())).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.items[0].displayOrder").value(1));
  assertThat(jdbc.queryForObject("select count(*) from customer_watchlist_event where customer_id=?",Integer.class,CUSTOMER)).isEqualTo(1);
	  assertThat(jdbc.queryForObject("select count(*) from customer_watchlist_event where customer_id=? and actor_principal_id=? and actor_customer_id=? and actor_session_id=? and actor_type='CUSTOMER'",Integer.class,CUSTOMER,PRINCIPAL,CUSTOMER,SESSION)).isEqualTo(1);
	  var event=jdbc.queryForMap("select * from customer_watchlist_event where customer_id=?",CUSTOMER);
	  OffsetDateTime occurredAt=jdbc.queryForObject("select occurred_at from customer_watchlist_event where customer_id=?",(resultSet,row)->resultSet.getObject(1,OffsetDateTime.class),CUSTOMER);
  assertThat(occurredAt.getNano()%1_000).isZero();
  LinkedHashMap<String,Object> payload=new LinkedHashMap<>();payload.put("hashVersion","ACTOR_SNAPSHOT_V2");payload.put("eventId",event.get("event_id").toString());payload.put("customerId",CUSTOMER);payload.put("eventType","REPLACED");payload.put("version",2L);payload.put("instrumentIds",List.of("97800000-0000-0000-0000-000000000002","97800000-0000-0000-0000-000000000001"));payload.put("actorPrincipalId",PRINCIPAL.toString());payload.put("actorCustomerId",CUSTOMER);payload.put("actorSessionId",SESSION.toString());payload.put("actorType","CUSTOMER");payload.put("occurredAt",occurredAt.toInstant().toString());
  assertThat(event.get("event_hash_version")).isEqualTo("ACTOR_SNAPSHOT_V2");
  assertThat(event.get("event_hash")).isEqualTo(sha256(objectMapper.writeValueAsString(payload)));
  mockMvc.perform(put("/api/v1/customers/{id}/watchlist",CUSTOMER).header("Idempotency-Key","watchlist-replace-002").contentType(APPLICATION_JSON).content(body).with(watchWrite())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVESTMENT_WATCHLIST_VERSION_CONFLICT"));
  mockMvc.perform(put("/api/v1/customers/{id}/watchlist",CUSTOMER).header("Idempotency-Key","watchlist-replace-003").contentType(APPLICATION_JSON).content("{\"instrumentIds\":[\"97800000-0000-0000-0000-000000000001\",\"97800000-0000-0000-0000-000000000001\"],\"expectedVersion\":2}").with(watchWrite())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVESTMENT_WATCHLIST_INVALID"));
 }
 @Test void authorityOwnershipRangeAndAppendOnlyAreEnforced() throws Exception {
  mockMvc.perform(get("/api/v1/customers/{id}/watchlist",CUSTOMER).with(user("OTHER").authorities(()->"INVESTMENT_WATCHLIST_READ"))).andExpect(status().isForbidden());
  mockMvc.perform(get("/api/v1/investment-accounts/{id}/orders","97200000-0000-0000-0000-000000000001").with(user("OTHER").authorities(()->"INVESTMENT_MARKET_READ"))).andExpect(status().isNotFound());
  mockMvc.perform(get("/api/v1/market-instruments/{id}/chart","97800000-0000-0000-0000-000000000001").param("from","2025-01-01").param("to","2026-08-14").with(market())).andExpect(status().isBadRequest());
  assertThatThrownBy(()->jdbc.update("delete from market_quote_snapshot")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
 }
 private String sha256(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
}
