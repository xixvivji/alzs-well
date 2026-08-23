package com.alzswell.card.api;

import com.alzswell.card.application.CardQueryService;
import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class CardController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final CardQueryService service;

    public CardController(CardQueryService service) {
        this.service = service;
    }

    @GetMapping("/customers/{customerId}/cards")
    @Operation(summary = "마스킹된 합성 카드 목록 조회",
            description = "본인 소유의 합성 카드 snapshot만 조회하며 외부 금융사를 호출하지 않습니다.")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('CARD_READ')")
    public ResponseEntity<ApiResponse<CardResponses.CardList>> cards(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("CARDS_RETRIEVED", "마스킹된 합성 카드 목록을 조회했습니다.",
                service.cards(customerId));
    }

    @GetMapping("/cards/{cardId}")
    @Operation(summary = "합성 카드 상세 조회",
            description = "카드 상태·결제일·브랜드를 조회하며 잠금·해제·재발급을 실행하지 않습니다.")
    @PreAuthorize("hasAuthority('CARD_READ')")
    public ResponseEntity<ApiResponse<CardResponses.CardDetail>> card(
            @PathVariable UUID cardId, Authentication authentication) {
        return ApiResponses.ok("CARD_RETRIEVED", "합성 카드 상세를 조회했습니다.",
                service.card(authentication.getName(), cardId));
    }

    @GetMapping("/cards/{cardId}/transactions")
    @Operation(summary = "합성 카드 이용내역 조회",
            description = "합성 가맹점명과 불변 이용 snapshot만 기간·cursor로 조회합니다.")
    @PreAuthorize("hasAuthority('CARD_READ')")
    public ResponseEntity<ApiResponse<CardResponses.CardTransactionPage>> transactions(
            @PathVariable UUID cardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            Authentication authentication) {
        return ApiResponses.ok("CARD_TRANSACTIONS_RETRIEVED", "합성 카드 이용내역을 조회했습니다.",
                service.transactions(authentication.getName(), cardId, from, to, cursor, limit));
    }

    @GetMapping("/cards/{cardId}/statements")
    @Operation(summary = "합성 카드 청구서 조회",
            description = "최근 24개의 합성 청구 요약만 조회하며 파일 다운로드나 결제를 실행하지 않습니다.")
    @PreAuthorize("hasAuthority('CARD_READ')")
    public ResponseEntity<ApiResponse<CardResponses.CardStatementList>> statements(
            @PathVariable UUID cardId, Authentication authentication) {
        return ApiResponses.ok("CARD_STATEMENTS_RETRIEVED", "합성 카드 청구 요약을 조회했습니다.",
                service.statements(authentication.getName(), cardId));
    }

    @GetMapping("/cards/{cardId}/payment-due")
    @Operation(summary = "합성 카드 결제예정액 조회",
            description = "고정 기준일의 결제예정액만 조회하며 결제·출금을 실행하지 않습니다.")
    @PreAuthorize("hasAuthority('CARD_READ')")
    public ResponseEntity<ApiResponse<CardResponses.CardPaymentDue>> paymentDue(
            @PathVariable UUID cardId, Authentication authentication) {
        return ApiResponses.ok("CARD_PAYMENT_DUE_RETRIEVED", "합성 카드 결제예정액을 조회했습니다.",
                service.paymentDue(authentication.getName(), cardId));
    }

    @GetMapping("/cards/{cardId}/limits")
    @Operation(summary = "합성 카드 이용한도 조회",
            description = "고정 합성 한도만 조회하며 한도 변경이나 외부 호출을 실행하지 않습니다.")
    @PreAuthorize("hasAuthority('CARD_READ')")
    public ResponseEntity<ApiResponse<CardResponses.CardLimit>> limits(
            @PathVariable UUID cardId, Authentication authentication) {
        return ApiResponses.ok("CARD_LIMIT_RETRIEVED", "합성 카드 이용한도를 조회했습니다.",
                service.limits(authentication.getName(), cardId));
    }
}
