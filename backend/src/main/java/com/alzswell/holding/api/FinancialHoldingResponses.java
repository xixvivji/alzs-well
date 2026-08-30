package com.alzswell.holding.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinancialHoldingResponses {
 private FinancialHoldingResponses(){}
 public record Deposit(UUID holdingId,UUID accountId,String institutionId,String institutionName,String displayName,String maskedAccountNumber,String productType,BigDecimal principalAmount,BigDecimal currentBalance,BigDecimal accruedInterest,BigDecimal annualInterestRate,LocalDate openedOn,LocalDate maturityDate,String status,String currency,LocalDate dataAsOf){}
 public record DepositList(List<Deposit> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record DepositDetail(Deposit deposit,BigDecimal expectedMaturityAmount,boolean maturityActionAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record Loan(UUID loanId,String institutionId,String institutionName,String displayName,String maskedReference,String loanType,BigDecimal originalPrincipal,BigDecimal outstandingAmount,BigDecimal scheduledAmount,BigDecimal annualInterestRate,LocalDate nextDueDate,LocalDate startedOn,LocalDate maturityDate,String repaymentMethod,String status,String currency,LocalDate dataAsOf){}
 public record LoanList(List<Loan> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record LoanDetail(Loan loan,boolean repaymentAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record RepaymentInstallment(UUID installmentId,int installmentNumber,LocalDate dueDate,BigDecimal principalAmount,BigDecimal interestAmount,BigDecimal totalAmount,String status,LocalDate dataAsOf){}
 public record RepaymentSchedule(UUID loanId,List<RepaymentInstallment> items,int total,boolean paymentExecutionAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record InvestmentAccount(UUID accountId,String institutionId,String institutionName,String displayName,String maskedAccountNumber,String accountType,String status,BigDecimal cashBalance,BigDecimal totalMarketValue,String currency,LocalDate dataAsOf){}
 public record InvestmentAccountList(List<InvestmentAccount> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record Allocation(String assetClass,BigDecimal marketValue,BigDecimal weightPercent){}
 public record Portfolio(UUID accountId,BigDecimal cashBalance,BigDecimal investedMarketValue,BigDecimal totalMarketValue,List<Allocation> allocations,boolean orderAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record Position(UUID positionId,String assetClass,String instrumentName,String maskedInstrumentCode,BigDecimal quantity,BigDecimal averagePurchasePrice,BigDecimal currentPrice,BigDecimal marketValue,BigDecimal unrealizedProfitLoss,String currency,LocalDate dataAsOf){}
 public record PositionList(UUID accountId,List<Position> items,int total,boolean orderAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record PensionHolding(UUID holdingId,String institutionId,String institutionName,String displayName,String maskedContractReference,String pensionType,String status,BigDecimal contributedAmount,BigDecimal currentValue,LocalDate expectedBenefitStartDate,String currency,LocalDate dataAsOf){}
 public record PensionHoldingList(List<PensionHolding> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record PensionScenario(UUID projectionId,String scenarioCode,BigDecimal assumedAnnualReturn,BigDecimal projectedValue,BigDecimal projectedMonthlyBenefit,LocalDate benefitStartDate,LocalDate calculatedOn){}
 public record PensionProjection(UUID holdingId,List<PensionScenario> scenarios,int total,String disclaimer,boolean guaranteed,boolean recommendationProvided,boolean actionAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
 public record TrustHolding(UUID trustId,String institutionId,String institutionName,String displayName,String maskedContractReference,String trustType,String purposeCode,String status,BigDecimal entrustedPrincipal,BigDecimal currentValue,int beneficiaryCount,LocalDate startedOn,LocalDate maturityDate,LocalDate nextReviewDate,String currency,LocalDate dataAsOf){}
 public record TrustHoldingList(List<TrustHolding> items,int total,boolean syntheticData,boolean externalProviderCalled){}
 public record TrustHoldingDetail(TrustHolding trust,boolean beneficiaryIdentityProvided,boolean contractActionAvailable,boolean syntheticData,boolean externalProviderCalled,boolean externalActionExecuted){}
}
