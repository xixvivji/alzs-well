package com.alzswell.fx.api;
import jakarta.validation.constraints.*;import java.math.BigDecimal;
public final class FxRequests {private FxRequests(){}
 public record Simulation(@NotBlank @Pattern(regexp="KRW|USD|JPY|EUR") String fromCurrency,@NotBlank @Pattern(regexp="KRW|USD|JPY|EUR") String toCurrency,@NotNull @DecimalMin("1.00") @DecimalMax("100000000.00") @Digits(integer=9,fraction=2) BigDecimal amount){}
}
