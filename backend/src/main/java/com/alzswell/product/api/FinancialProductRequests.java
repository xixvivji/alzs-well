package com.alzswell.product.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class FinancialProductRequests {
 private FinancialProductRequests(){}
 public record InterestSimulation(@NotNull @DecimalMin("10000") @DecimalMax("1000000000") @Digits(integer=10,fraction=0) BigDecimal principalAmount,@Min(1) @Max(120) int termMonths){}
 public record RepaymentSimulation(@NotNull @DecimalMin("100000") @DecimalMax("1000000000") @Digits(integer=10,fraction=0) BigDecimal principalAmount,@Min(1) @Max(360) int termMonths,@NotNull @DecimalMin("0.0000") @DecimalMax("30.0000") @Digits(integer=2,fraction=4) BigDecimal annualInterestRate){}
}
