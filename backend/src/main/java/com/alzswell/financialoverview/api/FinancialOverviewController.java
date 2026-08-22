package com.alzswell.financialoverview.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.financialoverview.api.FinancialOverviewResponses.*;
import com.alzswell.financialoverview.application.FinancialOverviewService;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@Validated
public class FinancialOverviewController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private static final String AUTH = "#customerId == authentication.name and hasAuthority('FINANCIAL_OVERVIEW_READ')";
    private final FinancialOverviewService service;

    public FinancialOverviewController(FinancialOverviewService service) { this.service = service; }

    @GetMapping("/financial-summary")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<FinancialSummary>> financialSummary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("FINANCIAL_SUMMARY_RETRIEVED", "통합 자산·부채·현금흐름 요약을 조회했습니다.",
                service.financialSummary(customerId));
    }

    @GetMapping("/asset-breakdown")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<AssetBreakdown>> assetBreakdown(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("ASSET_BREAKDOWN_RETRIEVED", "기관·상품·자산군별 구성을 조회했습니다.",
                service.assetBreakdown(customerId));
    }

    @GetMapping("/asset-trends")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<AssetTrends>> assetTrends(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponses.ok("ASSET_TRENDS_RETRIEVED", "기간별 총자산·순자산 추세를 조회했습니다.",
                service.assetTrends(customerId, from, to));
    }

    @GetMapping("/liabilities")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<LiabilityList>> liabilities(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("LIABILITIES_RETRIEVED", "마스킹된 합성 부채 요약을 조회했습니다.",
                service.liabilities(customerId));
    }

    @GetMapping("/cashflow-summary")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<CashflowSummary>> cashflowSummary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponses.ok("CASHFLOW_SUMMARY_RETRIEVED", "기간별 수입·지출·순현금흐름을 조회했습니다.",
                service.cashflowSummary(customerId, from, to));
    }

    @GetMapping("/expense-summary")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<ExpenseSummary>> expenseSummary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponses.ok("EXPENSE_SUMMARY_RETRIEVED", "범주·기관별 지출 분석을 조회했습니다.",
                service.expenseSummary(customerId, from, to));
    }

    @GetMapping("/asset-calendar")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<AssetCalendar>> assetCalendar(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponses.ok("ASSET_CALENDAR_RETRIEVED", "급여·이자·납부·만기 통합 일정을 조회했습니다.",
                service.assetCalendar(customerId, from, to));
    }

    @GetMapping("/data-freshness")
    @PreAuthorize(AUTH)
    public ResponseEntity<ApiResponse<DataFreshness>> dataFreshness(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("DATA_FRESHNESS_RETRIEVED", "기관별 데이터 최신성·완전성을 조회했습니다.",
                service.dataFreshness(customerId));
    }
}
