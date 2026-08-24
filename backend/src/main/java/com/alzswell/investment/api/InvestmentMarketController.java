package com.alzswell.investment.api;
import com.alzswell.common.api.*;
import com.alzswell.investment.application.InvestmentMarketService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") @Validated
public class InvestmentMarketController {
 private static final String CUSTOMER="^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";private final InvestmentMarketService service;
 public InvestmentMarketController(InvestmentMarketService service){this.service=service;}
 @GetMapping("/investment-accounts/{accountId}/orders") @Operation(summary="합성 투자 주문·체결 이력 조회",description="과거 합성 주문 snapshot만 조회하며 주문·취소를 실행하지 않습니다.") @PreAuthorize("hasAuthority('INVESTMENT_MARKET_READ')")
 public ResponseEntity<ApiResponse<InvestmentMarketResponses.OrderList>> orders(@PathVariable UUID accountId,Authentication a){return ApiResponses.ok("INVESTMENT_ORDERS_RETRIEVED","합성 투자 주문 이력을 조회했습니다.",service.orders(a.getName(),accountId));}
 @GetMapping("/market-instruments/{instrumentId}/quote") @Operation(summary="지연된 합성 종목 시세 조회",description="외부 시세 API를 호출하지 않고 기준일이 고정된 합성 quote만 반환합니다.") @PreAuthorize("hasAuthority('INVESTMENT_MARKET_READ')")
 public ResponseEntity<ApiResponse<InvestmentMarketResponses.Quote>> quote(@PathVariable UUID instrumentId){return ApiResponses.ok("MARKET_QUOTE_RETRIEVED","지연된 합성 시세를 조회했습니다.",service.quote(instrumentId));}
 @GetMapping("/market-instruments/{instrumentId}/chart") @Operation(summary="합성 종목 차트 조회",description="최대 366일 범위의 합성 OHLC 데이터만 반환합니다.") @PreAuthorize("hasAuthority('INVESTMENT_MARKET_READ')")
 public ResponseEntity<ApiResponse<InvestmentMarketResponses.Chart>> chart(@PathVariable UUID instrumentId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return ApiResponses.ok("MARKET_CHART_RETRIEVED","합성 차트 데이터를 조회했습니다.",service.chart(instrumentId,from,to));}
 @GetMapping("/customers/{customerId}/watchlist") @Operation(summary="고객 합성 관심종목 조회",description="본인의 합성 관심종목과 지연 시세만 조회합니다.") @PreAuthorize("#customerId == authentication.name and hasAuthority('INVESTMENT_WATCHLIST_READ')")
 public ResponseEntity<ApiResponse<InvestmentMarketResponses.Watchlist>> watchlist(@PathVariable @Pattern(regexp=CUSTOMER) String customerId){return ApiResponses.ok("INVESTMENT_WATCHLIST_RETRIEVED","합성 관심종목을 조회했습니다.",service.watchlist(customerId));}
 @PutMapping("/customers/{customerId}/watchlist") @Operation(summary="고객 합성 관심종목 전체 변경",description="최대 20개의 활성 합성 종목으로 관심목록만 변경하며 주문을 실행하지 않습니다.") @PreAuthorize("#customerId == authentication.name and hasAuthority('INVESTMENT_WATCHLIST_WRITE')")
 public ResponseEntity<ApiResponse<InvestmentMarketResponses.Watchlist>> replace(@PathVariable @Pattern(regexp=CUSTOMER) String customerId,@RequestHeader("Idempotency-Key") @Size(min=8,max=100) @Pattern(regexp="[A-Za-z0-9._:-]+") String key,@Valid @RequestBody InvestmentMarketRequests.ReplaceWatchlist command){return ApiResponses.ok("INVESTMENT_WATCHLIST_REPLACED","합성 관심종목을 변경했습니다.",service.replaceWatchlist(customerId,command,key));}
}
