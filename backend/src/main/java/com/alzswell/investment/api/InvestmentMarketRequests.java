package com.alzswell.investment.api;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public final class InvestmentMarketRequests {
 private InvestmentMarketRequests(){}
 public record ReplaceWatchlist(@NotNull @Size(max=20) List<@NotNull UUID> instrumentIds,@Min(1) long expectedVersion){}
}
