package com.alzswell.product.api;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public final class FinancialProductResponses {
 private FinancialProductResponses(){}
 public record DepositProduct(UUID productId,String institutionId,String institutionName,String productName,String productType,BigDecimal minPrincipal,BigDecimal maxPrincipal,int minTermMonths,int maxTermMonths,String interestPaymentType,String summary,String status,String currency,LocalDate dataAsOf){}
 public record DepositProductList(List<DepositProduct> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record DepositProductDetail(DepositProduct product,String cautionText,boolean applicationAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record DepositRate(UUID rateId,String tierCode,int minTermMonths,int maxTermMonths,BigDecimal annualInterestRate,String rateType,LocalDate dataAsOf){}
 public record DepositRateList(UUID productId,List<DepositRate> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record InterestSimulation(UUID productId,String inputMode,BigDecimal inputAmount,int termMonths,BigDecimal annualInterestRate,BigDecimal totalPrincipal,BigDecimal grossInterest,BigDecimal estimatedTax,BigDecimal netInterest,BigDecimal estimatedMaturityAmount,String currency,String calculationRule,boolean personalized,boolean applicationAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record MaturityOption(UUID optionId,String optionCode,String title,String description,int displayOrder){}
 public record MaturityOptionList(UUID holdingId,LocalDate maturityDate,List<MaturityOption> items,int total,boolean selectable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record LoanProduct(UUID productId,String institutionId,String institutionName,String productName,String productType,BigDecimal minPrincipal,BigDecimal maxPrincipal,int minTermMonths,int maxTermMonths,BigDecimal minAnnualInterestRate,BigDecimal maxAnnualInterestRate,String repaymentMethod,String summary,String status,String currency,LocalDate dataAsOf){}
 public record LoanProductList(List<LoanProduct> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record LoanProductDetail(LoanProduct product,String cautionText,boolean applicationAvailable,boolean creditAssessmentPerformed,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record RepaymentSimulation(UUID productId,BigDecimal principalAmount,int termMonths,BigDecimal annualInterestRate,String repaymentMethod,BigDecimal monthlyPrincipal,BigDecimal firstPaymentAmount,BigDecimal finalPaymentAmount,BigDecimal totalInterest,BigDecimal totalRepaymentAmount,String currency,String calculationRule,boolean personalized,boolean creditAssessmentPerformed,boolean applicationAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
}
