package com.alzswell.transaction.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.transaction.api.TransactionRequests.UpdateCategory;
import com.alzswell.transaction.api.TransactionRequests.UpdateNote;
import com.alzswell.transaction.api.TransactionResponses.*;
import com.alzswell.transaction.application.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class TransactionController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private static final String DIRECTION_PATTERN = "CREDIT|DEBIT";
    private static final String CATEGORY_PATTERN = "INCOME|HOUSING|UTILITIES|COMMUNICATION|FOOD|TRANSPORT|HEALTH|FINANCE|SHOPPING|OTHER";
    private final TransactionService service;

    public TransactionController(TransactionService service) { this.service = service; }

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<TransactionPage>> accountTransactions(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Pattern(regexp = DIRECTION_PATTERN) String direction,
            @RequestParam(required = false) @Pattern(regexp = CATEGORY_PATTERN) String category,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            Authentication auth) {
        return ApiResponses.ok("TRANSACTIONS_RETRIEVED", "마스킹된 합성 거래내역을 조회했습니다.",
                service.accountTransactions(auth.getName(), accountId, from, to, direction, category, cursor, limit));
    }

    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<TransactionDetail>> transaction(
            @PathVariable UUID transactionId, Authentication auth) {
        return ApiResponses.ok("TRANSACTION_RETRIEVED", "마스킹된 합성 거래 상세를 조회했습니다.",
                service.transaction(auth.getName(), transactionId));
    }

    @GetMapping("/customers/{customerId}/transactions/search")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<TransactionPage>> search(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Pattern(regexp = DIRECTION_PATTERN) String direction,
            @RequestParam(required = false) @Pattern(regexp = CATEGORY_PATTERN) String category,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minAmount,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxAmount,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponses.ok("TRANSACTIONS_SEARCHED", "고객 거래내역을 안전한 조건으로 검색했습니다.",
                service.search(customerId, q, accountId, from, to, direction, category,
                        minAmount, maxAmount, cursor, limit));
    }

    @GetMapping("/customers/{customerId}/transactions/summary")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<TransactionSummary>> summary(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponses.ok("TRANSACTION_SUMMARY_RETRIEVED", "기간·범주별 거래 요약을 조회했습니다.",
                service.summary(customerId, from, to));
    }

    @GetMapping("/customers/{customerId}/counterparties")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<CounterpartyList>> counterparties(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("TRANSACTION_COUNTERPARTIES_RETRIEVED", "마스킹된 거래 상대 목록을 조회했습니다.",
                service.counterparties(customerId));
    }

    @GetMapping("/counterparties/{counterpartyId}/transaction-history")
    @PreAuthorize("hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<CounterpartyHistory>> counterpartyHistory(
            @PathVariable UUID counterpartyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return ApiResponses.ok("COUNTERPARTY_TRANSACTION_HISTORY_RETRIEVED", "거래 상대별 추세를 조회했습니다.",
                service.counterpartyHistory(auth.getName(), counterpartyId, from, to));
    }

    @GetMapping("/transactions/{transactionId}/enrichment")
    @PreAuthorize("hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<TransactionEnrichment>> enrichment(
            @PathVariable UUID transactionId, Authentication auth) {
        return ApiResponses.ok("TRANSACTION_ENRICHMENT_RETRIEVED", "거래 정규화·분석 부가정보를 조회했습니다.",
                service.enrichment(auth.getName(), transactionId));
    }

    @PutMapping("/transactions/{transactionId}/category")
    @PreAuthorize("hasAuthority('TRANSACTION_WRITE')")
    public ResponseEntity<ApiResponse<TransactionPreference>> updateCategory(
            @PathVariable UUID transactionId,
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100)
            @Pattern(regexp="[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody UpdateCategory command,
            Authentication auth) {
        return ApiResponses.ok("TRANSACTION_CATEGORY_UPDATED", "고객 지정 거래 범주를 변경했습니다.",
                service.updateCategory(auth.getName(), transactionId, command, idempotencyKey));
    }

    @PutMapping("/transactions/{transactionId}/note")
    @PreAuthorize("hasAuthority('TRANSACTION_WRITE')")
    public ResponseEntity<ApiResponse<TransactionPreference>> updateNote(
            @PathVariable UUID transactionId,
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100)
            @Pattern(regexp="[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody UpdateNote command,
            Authentication auth) {
        return ApiResponses.ok("TRANSACTION_NOTE_UPDATED", "금융 기억노트를 변경했습니다.",
                service.updateNote(auth.getName(), transactionId, command, idempotencyKey));
    }
}
