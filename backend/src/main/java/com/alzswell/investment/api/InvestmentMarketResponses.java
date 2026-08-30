package com.alzswell.investment.api;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class InvestmentMarketResponses {
 private InvestmentMarketResponses(){}
 public record Order(UUID orderId,UUID instrumentId,String instrumentName,String maskedInstrumentCode,String orderType,String side,BigDecimal quantity,BigDecimal orderPrice,BigDecimal filledQuantity,String status,OffsetDateTime orderedAt,String currency,LocalDate dataAsOf){}
 public record OrderList(UUID accountId,List<Order> items,int total,boolean orderAvailable,boolean cancellationAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record Quote(UUID instrumentId,String instrumentName,String maskedInstrumentCode,String assetClass,String marketCode,OffsetDateTime quotedAt,BigDecimal currentPrice,BigDecimal previousClose,BigDecimal changeAmount,BigDecimal changeRate,String currency,LocalDate dataAsOf,boolean delayed,boolean syntheticData,boolean externalProviderCalled){}
 public record PricePoint(LocalDate priceDate,BigDecimal openPrice,BigDecimal highPrice,BigDecimal lowPrice,BigDecimal closePrice,BigDecimal volume){}
 public record Chart(UUID instrumentId,String instrumentName,List<PricePoint> items,int count,LocalDate from,LocalDate to,String currency,LocalDate dataAsOf,boolean syntheticData,boolean externalProviderCalled){}
 public record WatchlistItem(UUID instrumentId,String instrumentName,String maskedInstrumentCode,String assetClass,int displayOrder,BigDecimal currentPrice,BigDecimal changeRate,String currency,LocalDate dataAsOf){}
 public record Watchlist(String customerId,List<WatchlistItem> items,int total,long version,OffsetDateTime updatedAt,boolean orderAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
}
