package com.alzswell.investment.application;
import static com.alzswell.investment.api.InvestmentMarketResponses.*;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.investment.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class InvestmentMarketService {
 private static final LocalDate DATA_AS_OF=LocalDate.of(2026,8,14);
 private static final String INSTRUMENT_QUOTE="""
 select i.instrument_id,i.instrument_name,i.masked_instrument_code,i.asset_class,i.market_code,i.currency,i.data_as_of,
 q.quoted_at,q.current_price,q.previous_close,q.change_amount,q.change_rate
 from market_instrument_snapshot i join lateral(select * from market_quote_snapshot q0 where q0.instrument_id=i.instrument_id order by q0.quoted_at desc,q0.quote_id desc limit 1) q on true
 """;
 private final JdbcTemplate jdbc;private final MutationIdempotencyService idempotency;private final Clock clock;private final ObjectMapper objectMapper;
 public InvestmentMarketService(JdbcTemplate jdbc,MutationIdempotencyService idempotency,Clock clock,ObjectMapper objectMapper){this.jdbc=jdbc;this.idempotency=idempotency;this.clock=clock;this.objectMapper=objectMapper;}
 public OrderList orders(String customerId,UUID accountId){ownedAccount(customerId,accountId);List<Order> items=jdbc.query("""
 select o.order_id,o.instrument_id,i.instrument_name,i.masked_instrument_code,o.order_type,o.side,o.quantity,o.order_price,o.filled_quantity,o.status,o.ordered_at,o.currency,o.data_as_of
 from investment_order_snapshot o join market_instrument_snapshot i on i.instrument_id=o.instrument_id where o.investment_account_id=? order by o.ordered_at desc,o.order_id desc limit 100
 """,(r,n)->new Order(r.getObject("order_id",UUID.class),r.getObject("instrument_id",UUID.class),r.getString("instrument_name"),r.getString("masked_instrument_code"),r.getString("order_type"),r.getString("side"),r.getBigDecimal("quantity"),r.getBigDecimal("order_price"),r.getBigDecimal("filled_quantity"),r.getString("status"),r.getObject("ordered_at",OffsetDateTime.class),r.getString("currency"),r.getObject("data_as_of",LocalDate.class)),accountId);return new OrderList(accountId,items,items.size(),false,false,true,false,false);}
 public Quote quote(UUID instrumentId){List<Quote> rows=jdbc.query(INSTRUMENT_QUOTE+" where i.instrument_id=? and i.status='ACTIVE'",this::quoteRow,instrumentId);if(rows.size()!=1)throw new BusinessException(InvestmentMarketErrorCode.INSTRUMENT_NOT_FOUND);return rows.getFirst();}
 public Chart chart(UUID instrumentId,LocalDate requestedFrom,LocalDate requestedTo){Quote instrument=quote(instrumentId);LocalDate to=requestedTo==null?DATA_AS_OF:requestedTo;LocalDate from=requestedFrom==null?to.minusDays(30):requestedFrom;if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365)throw new BusinessException(InvestmentMarketErrorCode.CHART_RANGE_INVALID);List<PricePoint> items=jdbc.query("""
 select price_date,open_price,high_price,low_price,close_price,volume from market_price_point_snapshot where instrument_id=? and price_date between ? and ? order by price_date,price_point_id
 """,(r,n)->new PricePoint(r.getObject("price_date",LocalDate.class),r.getBigDecimal("open_price"),r.getBigDecimal("high_price"),r.getBigDecimal("low_price"),r.getBigDecimal("close_price"),r.getBigDecimal("volume")),instrumentId,from,to);return new Chart(instrumentId,instrument.instrumentName(),items,items.size(),from,to,instrument.currency(),instrument.dataAsOf(),true,false);}
 public Watchlist watchlist(String customerId){List<State> states=jdbc.query("select version,updated_at from customer_watchlist_state where customer_id=?",(r,n)->new State(r.getLong(1),r.getObject(2,OffsetDateTime.class)),customerId);if(states.isEmpty())return new Watchlist(customerId,List.of(),0,1,OffsetDateTime.now(clock),false,true,false,false);List<WatchlistItem> items=jdbc.query("""
 select w.instrument_id,i.instrument_name,i.masked_instrument_code,i.asset_class,w.display_order,q.current_price,q.change_rate,i.currency,i.data_as_of
 from customer_watchlist_item w join market_instrument_snapshot i on i.instrument_id=w.instrument_id join lateral(select * from market_quote_snapshot q0 where q0.instrument_id=i.instrument_id order by q0.quoted_at desc,q0.quote_id desc limit 1) q on true where w.customer_id=? order by w.display_order,w.instrument_id
 """,(r,n)->new WatchlistItem(r.getObject("instrument_id",UUID.class),r.getString("instrument_name"),r.getString("masked_instrument_code"),r.getString("asset_class"),r.getInt("display_order"),r.getBigDecimal("current_price"),r.getBigDecimal("change_rate"),r.getString("currency"),r.getObject("data_as_of",LocalDate.class)),customerId);State state=states.getFirst();return new Watchlist(customerId,items,items.size(),state.version(),state.updatedAt(),false,true,false,false);}
 @Transactional public Watchlist replaceWatchlist(String customerId,InvestmentMarketRequests.ReplaceWatchlist command,String key){return idempotency.execute("INVESTMENT_WATCHLIST:"+customerId,key,command,Watchlist.class,InvestmentMarketErrorCode.IDEMPOTENCY_CONFLICT,()->replaceOnce(customerId,command));}
 private Watchlist replaceOnce(String customerId,InvestmentMarketRequests.ReplaceWatchlist command){LinkedHashSet<UUID> unique=new LinkedHashSet<>(command.instrumentIds());if(unique.size()!=command.instrumentIds().size())throw new BusinessException(InvestmentMarketErrorCode.WATCHLIST_INVALID);for(UUID id:unique){Integer valid=jdbc.queryForObject("select count(*) from market_instrument_snapshot where status='ACTIVE' and instrument_id=?",Integer.class,id);if(valid==null||valid!=1)throw new BusinessException(InvestmentMarketErrorCode.WATCHLIST_INVALID);}OffsetDateTime now=OffsetDateTime.now(clock);jdbc.update("insert into customer_watchlist_state(customer_id,version,updated_at) values(?,1,?) on conflict (customer_id) do nothing",customerId,now);int changed=jdbc.update("update customer_watchlist_state set version=version+1,updated_at=? where customer_id=? and version=?",now,customerId,command.expectedVersion());if(changed!=1)throw new BusinessException(InvestmentMarketErrorCode.WATCHLIST_VERSION_CONFLICT);jdbc.update("delete from customer_watchlist_item where customer_id=?",customerId);int order=1;for(UUID id:unique)jdbc.update("insert into customer_watchlist_item(customer_id,instrument_id,display_order,added_at) values(?,?,?,?)",customerId,id,order++,now);UUID actor=jdbc.queryForObject("select principal_id from auth_principal where customer_id=? and status='ACTIVE' order by principal_id limit 1",UUID.class,customerId);long version=command.expectedVersion()+1;String json=json(unique);jdbc.update("insert into customer_watchlist_event(event_id,customer_id,event_type,version,instrument_ids,actor_id,occurred_at,event_hash) values(?,?, 'REPLACED',?,?::jsonb,?,?,?)",UUID.randomUUID(),customerId,version,json,actor,now,sha256(customerId+"|"+version+"|"+json));return watchlist(customerId);}
 private void ownedAccount(String customerId,UUID accountId){Integer count=jdbc.queryForObject("select count(*) from customer_investment_account_snapshot where customer_id=? and investment_account_id=?",Integer.class,customerId,accountId);if(count==null||count!=1)throw new BusinessException(InvestmentMarketErrorCode.ACCOUNT_NOT_FOUND);}
 private Quote quoteRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Quote(r.getObject("instrument_id",UUID.class),r.getString("instrument_name"),r.getString("masked_instrument_code"),r.getString("asset_class"),r.getString("market_code"),r.getObject("quoted_at",OffsetDateTime.class),r.getBigDecimal("current_price"),r.getBigDecimal("previous_close"),r.getBigDecimal("change_amount"),r.getBigDecimal("change_rate"),r.getString("currency"),r.getObject("data_as_of",LocalDate.class),true,true,false);}
 private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private record State(long version,OffsetDateTime updatedAt){}
}
