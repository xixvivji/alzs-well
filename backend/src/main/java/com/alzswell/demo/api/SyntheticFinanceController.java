package com.alzswell.demo.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.demo.application.SyntheticFinanceQueryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/sessions/{sessionId}")
public class SyntheticFinanceController {

    private final SyntheticFinanceQueryService queryService;

    public SyntheticFinanceController(SyntheticFinanceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/customers/{customerId}/connections/consent-summary")
    public ResponseEntity<ApiResponse<ConnectionConsentSummaryResponse>> connections(
            @PathVariable UUID sessionId, @PathVariable String customerId) {
        return ApiResponses.ok("DEMO_CONNECTION_LIST_RETRIEVED",
                "합성 연결기관과 동의 범위를 조회했습니다.", queryService.connections(sessionId, customerId));
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<ApiResponse<AccountListResponse>> accounts(
            @PathVariable UUID sessionId, @PathVariable String customerId) {
        return ApiResponses.ok("ACCOUNT_LIST_RETRIEVED", "합성 계좌 목록을 조회했습니다.",
                queryService.accounts(sessionId, customerId));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<ApiResponse<TransactionListResponse>> transactions(
            @PathVariable UUID sessionId,
            @PathVariable String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.ok("TRANSACTION_LIST_RETRIEVED", "합성 거래내역을 조회했습니다.",
                queryService.transactions(sessionId, accountId, from, to, direction, category, cursor, limit));
    }

    @GetMapping("/customers/{customerId}/baselines")
    public ResponseEntity<ApiResponse<BaselineListResponse>> baselines(
            @PathVariable UUID sessionId, @PathVariable String customerId) {
        return ApiResponses.ok("BASELINE_LIST_RETRIEVED", "개인 금융생활 기준선을 조회했습니다.",
                queryService.baselines(sessionId, customerId));
    }

    @GetMapping("/customers/{customerId}/financial-summary")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> financialSummary(
            @PathVariable UUID sessionId, @PathVariable String customerId) {
        return ApiResponses.ok("FINANCIAL_SUMMARY_RETRIEVED", "합성 금융생활 요약을 조회했습니다.",
                queryService.financialSummary(sessionId, customerId));
    }

    @GetMapping("/protection-actions")
    public ResponseEntity<ApiResponse<ProtectionActionListResponse>> protectionActions(
            @PathVariable UUID sessionId) {
        return ApiResponses.ok("PROTECTION_ACTION_LIST_RETRIEVED", "공식 보호수단 안내를 조회했습니다.",
                queryService.protectionActions(sessionId));
    }
}
