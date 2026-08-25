package com.alzswell.fx.api;
import java.math.BigDecimal;import java.time.*;import java.util.*;
public final class FxResponses {private FxResponses(){}
 public record Rate(UUID rateId,String currency,String currencyName,BigDecimal unitAmount,BigDecimal baseRate,BigDecimal remittanceSendRate,BigDecimal remittanceReceiveRate,BigDecimal cashBuyRate,BigDecimal cashSellRate,OffsetDateTime quotedAt,LocalDate dataAsOf){}
 public record RateList(List<Rate> items,int total,String baseCurrency,boolean delayed,boolean syntheticData,boolean externalProviderCalled){}
 public record Account(UUID accountId,String institutionId,String institutionName,String maskedAccountNumber,String accountName,String currency,BigDecimal balance,BigDecimal availableBalance,String status,LocalDate dataAsOf){}
 public record AccountList(String customerId,List<Account> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record Simulation(String fromCurrency,String toCurrency,BigDecimal inputAmount,BigDecimal appliedRate,BigDecimal unitAmount,BigDecimal convertedAmount,String calculationRule,OffsetDateTime rateQuotedAt,boolean personalized,boolean exchangeCreated,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record Remittance(UUID remittanceId,UUID sourceAccountId,String destinationCountryCode,String beneficiaryAlias,String currency,BigDecimal foreignAmount,BigDecimal appliedRate,BigDecimal krwAmount,BigDecimal feeAmount,String status,OffsetDateTime requestedAt,OffsetDateTime completedAt){}
 public record RemittanceList(String customerId,List<Remittance> items,int total,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
}
