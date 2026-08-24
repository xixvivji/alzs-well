package com.alzswell.holding.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.holding.application.FinancialHoldingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") @Validated
public class FinancialHoldingController {
 private static final String CUSTOMER="^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
 private final FinancialHoldingQueryService service;
 public FinancialHoldingController(FinancialHoldingQueryService service){this.service=service;}
 @GetMapping("/customers/{customerId}/deposit-holdings") @Operation(summary="합성 예금 보유 목록 조회") @PreAuthorize("#customerId == authentication.name and hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.DepositList>> deposits(@PathVariable @Pattern(regexp=CUSTOMER) String customerId){return ApiResponses.ok("DEPOSIT_HOLDINGS_RETRIEVED","합성 예금 보유 목록을 조회했습니다.",service.deposits(customerId));}
 @GetMapping("/deposit-holdings/{holdingId}") @Operation(summary="합성 예금 보유 상세 조회") @PreAuthorize("hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.DepositDetail>> deposit(@PathVariable UUID holdingId,Authentication a){return ApiResponses.ok("DEPOSIT_HOLDING_RETRIEVED","합성 예금 보유 상세를 조회했습니다.",service.deposit(a.getName(),holdingId));}
 @GetMapping("/customers/{customerId}/loan-holdings") @Operation(summary="합성 대출 보유 목록 조회") @PreAuthorize("#customerId == authentication.name and hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.LoanList>> loans(@PathVariable @Pattern(regexp=CUSTOMER) String customerId){return ApiResponses.ok("LOAN_HOLDINGS_RETRIEVED","합성 대출 보유 목록을 조회했습니다.",service.loans(customerId));}
 @GetMapping("/loan-holdings/{loanId}") @Operation(summary="합성 대출 보유 상세 조회") @PreAuthorize("hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.LoanDetail>> loan(@PathVariable UUID loanId,Authentication a){return ApiResponses.ok("LOAN_HOLDING_RETRIEVED","합성 대출 보유 상세를 조회했습니다.",service.loan(a.getName(),loanId));}
 @GetMapping("/loan-holdings/{loanId}/repayment-schedule") @Operation(summary="합성 대출 상환일정 조회") @PreAuthorize("hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.RepaymentSchedule>> schedule(@PathVariable UUID loanId,Authentication a){return ApiResponses.ok("LOAN_REPAYMENT_SCHEDULE_RETRIEVED","합성 대출 상환일정을 조회했습니다.",service.schedule(a.getName(),loanId));}
 @GetMapping("/customers/{customerId}/investment-accounts") @Operation(summary="합성 투자계좌 목록 조회") @PreAuthorize("#customerId == authentication.name and hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.InvestmentAccountList>> investments(@PathVariable @Pattern(regexp=CUSTOMER) String customerId){return ApiResponses.ok("INVESTMENT_ACCOUNTS_RETRIEVED","합성 투자계좌 목록을 조회했습니다.",service.investments(customerId));}
 @GetMapping("/investment-accounts/{accountId}/portfolio") @Operation(summary="합성 투자 포트폴리오 조회") @PreAuthorize("hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.Portfolio>> portfolio(@PathVariable UUID accountId,Authentication a){return ApiResponses.ok("INVESTMENT_PORTFOLIO_RETRIEVED","합성 투자 포트폴리오를 조회했습니다.",service.portfolio(a.getName(),accountId));}
 @GetMapping("/investment-accounts/{accountId}/positions") @Operation(summary="합성 투자 포지션 조회") @PreAuthorize("hasAuthority('FINANCIAL_OVERVIEW_READ')")
 public ResponseEntity<ApiResponse<FinancialHoldingResponses.PositionList>> positions(@PathVariable UUID accountId,Authentication a){return ApiResponses.ok("INVESTMENT_POSITIONS_RETRIEVED","합성 투자 포지션을 조회했습니다.",service.positions(a.getName(),accountId));}
}
