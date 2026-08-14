package com.alzswell.demo.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record FinancialSummaryResponse(String customerId, LocalDate asOf, DatePeriod period,
                                       Assets assets, CashFlow cashFlow,
                                       ChangeSummary changeSummary,
                                       List<TrendItem> twelveMonthTrend, boolean syntheticData,
                                       SyntheticDataProvenance provenance) {
    public record DatePeriod(LocalDate from, LocalDate to) {
    }

    public record Assets(MoneyAmount total, MoneyAmount bankDeposits,
                         MoneyAmount investments, MoneyAmount liabilities) {
    }

    public record CashFlow(MoneyAmount monthlyIncome, MoneyAmount monthlyExpense,
                           MoneyAmount upcomingObligations) {
    }

    public record ChangeSummary(int openAlertCount, List<String> reasonCodes, String summary) {
    }

    public record TrendItem(YearMonth month, MoneyAmount totalAssets) {
    }
}
