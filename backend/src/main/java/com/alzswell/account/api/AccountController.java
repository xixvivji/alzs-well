package com.alzswell.account.api;

import com.alzswell.account.api.AccountResponses.*;
import com.alzswell.account.api.AccountRequests.UpdateDisplaySetting;
import com.alzswell.account.application.AccountQueryService;
import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
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
public class AccountController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final AccountQueryService service;

    public AccountController(AccountQueryService service) { this.service = service; }

    @GetMapping("/customers/{customerId}/accounts")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<AccountList>> accounts(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("ACCOUNTS_RETRIEVED", "마스킹된 합성 계좌 목록을 조회했습니다.",
                service.accounts(customerId));
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<AccountDetail>> account(@PathVariable UUID accountId, Authentication auth) {
        return ApiResponses.ok("ACCOUNT_RETRIEVED", "마스킹된 합성 계좌 상세를 조회했습니다.",
                service.account(auth.getName(), accountId));
    }

    @GetMapping("/accounts/{accountId}/balance")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<Balance>> balance(@PathVariable UUID accountId, Authentication auth) {
        return ApiResponses.ok("ACCOUNT_BALANCE_RETRIEVED", "현재·가용 잔액을 조회했습니다.",
                service.balance(auth.getName(), accountId));
    }

    @GetMapping("/accounts/{accountId}/balance-history")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<BalanceHistory>> balanceHistory(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return ApiResponses.ok("ACCOUNT_BALANCE_HISTORY_RETRIEVED", "기간별 잔액 추세를 조회했습니다.",
                service.balanceHistory(auth.getName(), accountId, from, to));
    }

    @GetMapping("/accounts/{accountId}/restrictions")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<RestrictionList>> restrictions(
            @PathVariable UUID accountId, Authentication auth) {
        return ApiResponses.ok("ACCOUNT_RESTRICTIONS_RETRIEVED", "계좌 상태·제약을 조회했습니다.",
                service.restrictions(auth.getName(), accountId));
    }

    @GetMapping("/accounts/{accountId}/interest-summary")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<InterestSummary>> interest(
            @PathVariable UUID accountId, Authentication auth) {
        return ApiResponses.ok("ACCOUNT_INTEREST_RETRIEVED", "이자 요약을 조회했습니다.",
                service.interest(auth.getName(), accountId));
    }

    @GetMapping("/accounts/{accountId}/statements")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<StatementList>> statements(
            @PathVariable UUID accountId, Authentication auth) {
        return ApiResponses.ok("ACCOUNT_STATEMENTS_RETRIEVED", "거래명세서 목록을 조회했습니다.",
                service.statements(auth.getName(), accountId));
    }

    @GetMapping("/accounts/{accountId}/statements/{statementId}")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<StatementDetail>> statement(
            @PathVariable UUID accountId, @PathVariable UUID statementId, Authentication auth) {
        return ApiResponses.ok("ACCOUNT_STATEMENT_RETRIEVED", "거래명세서 상세를 조회했습니다.",
                service.statement(auth.getName(), accountId, statementId));
    }

    @GetMapping("/accounts/{accountId}/recurring-counterparties")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<RecurringCounterpartyList>> recurringCounterparties(
            @PathVariable UUID accountId, Authentication auth) {
        return ApiResponses.ok("ACCOUNT_RECURRING_COUNTERPARTIES_RETRIEVED", "반복 거래 상대 분석을 조회했습니다.",
                service.recurringCounterparties(auth.getName(), accountId));
    }

    @PatchMapping("/accounts/{accountId}/display-settings")
    @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    public ResponseEntity<ApiResponse<DisplaySetting>> updateDisplaySetting(
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") @Size(min=8,max=100)
            @Pattern(regexp="[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody UpdateDisplaySetting command,
            Authentication auth) {
        return ApiResponses.ok("ACCOUNT_DISPLAY_SETTING_UPDATED", "계좌 표시 설정을 변경했습니다.",
                service.updateDisplaySetting(auth.getName(), accountId, command, idempotencyKey));
    }

    @GetMapping("/customers/{customerId}/account-groups")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<AccountGroupList>> accountGroups(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("ACCOUNT_GROUPS_RETRIEVED", "고객 지정 계좌 그룹을 조회했습니다.",
                service.accountGroups(customerId));
    }
}
