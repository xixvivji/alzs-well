package com.alzswell.product.api;
import com.alzswell.common.api.*;
import com.alzswell.product.application.FinancialProductService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class FinancialProductController {
 private final FinancialProductService service;
 public FinancialProductController(FinancialProductService service){this.service=service;}
 @GetMapping("/deposit-products") @Operation(summary="안심은행 합성 예금·적금 상품 목록 조회",description="외부 상품 API를 호출하지 않고 승인된 합성 상품 snapshot만 조회합니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_READ')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.DepositProductList>> deposits(){return ApiResponses.ok("DEPOSIT_PRODUCTS_RETRIEVED","합성 예금·적금 상품을 조회했습니다.",service.depositProducts());}
 @GetMapping("/deposit-products/{productId}") @Operation(summary="안심은행 합성 예금상품 상세 조회",description="합성 상품 조건과 유의사항만 제공하며 가입을 실행하지 않습니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_READ')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.DepositProductDetail>> deposit(@PathVariable UUID productId){return ApiResponses.ok("DEPOSIT_PRODUCT_RETRIEVED","합성 예금상품 상세를 조회했습니다.",service.depositProduct(productId));}
 @GetMapping("/deposit-products/{productId}/rates") @Operation(summary="합성 예금상품 금리표 조회",description="고정 기준일의 합성 금리 구간만 조회합니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_READ')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.DepositRateList>> rates(@PathVariable UUID productId){return ApiResponses.ok("DEPOSIT_PRODUCT_RATES_RETRIEVED","합성 예금상품 금리표를 조회했습니다.",service.depositRates(productId));}
 @PostMapping("/deposit-products/{productId}/interest-simulations") @Operation(summary="실행 없는 합성 이자 모의계산",description="합성 금리로 세전·예상세금·세후 이자를 계산할 뿐 가입이나 외부 호출을 실행하지 않습니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_SIMULATE')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.InterestSimulation>> interest(@PathVariable UUID productId,@Valid @RequestBody FinancialProductRequests.InterestSimulation command){return ApiResponses.ok("DEPOSIT_INTEREST_SIMULATION_COMPLETED","실제 가입 없이 합성 이자를 계산했습니다.",service.simulateInterest(productId,command));}
 @GetMapping("/deposit-holdings/{holdingId}/maturity-options") @Operation(summary="합성 예금 만기 선택지 조회",description="고객 본인의 합성 예금에 대한 안내만 제공하며 선택·해지를 실행하지 않습니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_READ')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.MaturityOptionList>> maturity(@PathVariable UUID holdingId,Authentication authentication){return ApiResponses.ok("DEPOSIT_MATURITY_OPTIONS_RETRIEVED","합성 만기 처리 선택지를 조회했습니다.",service.maturityOptions(authentication.getName(),holdingId));}
 @GetMapping("/loan-products") @Operation(summary="안심은행 합성 대출상품 목록 조회",description="외부 상품 API·신용조회를 호출하지 않고 합성 상품 snapshot만 조회합니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_READ')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.LoanProductList>> loans(){return ApiResponses.ok("LOAN_PRODUCTS_RETRIEVED","합성 대출상품을 조회했습니다.",service.loanProducts());}
 @GetMapping("/loan-products/{productId}") @Operation(summary="안심은행 합성 대출상품 상세 조회",description="합성 금리 범위와 유의사항만 제공하며 심사·신청을 실행하지 않습니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_READ')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.LoanProductDetail>> loan(@PathVariable UUID productId){return ApiResponses.ok("LOAN_PRODUCT_RETRIEVED","합성 대출상품 상세를 조회했습니다.",service.loanProduct(productId));}
 @PostMapping("/loan-products/{productId}/repayment-simulations") @Operation(summary="실행 없는 합성 대출 상환 모의계산",description="원금균등 방식의 예상 상환액만 계산하며 신용조회·심사·신청을 실행하지 않습니다.") @PreAuthorize("hasAuthority('FINANCIAL_PRODUCT_SIMULATE')")
 public ResponseEntity<ApiResponse<FinancialProductResponses.RepaymentSimulation>> repayment(@PathVariable UUID productId,@Valid @RequestBody FinancialProductRequests.RepaymentSimulation command){return ApiResponses.ok("LOAN_REPAYMENT_SIMULATION_COMPLETED","실제 신청 없이 합성 상환액을 계산했습니다.",service.simulateRepayment(productId,command));}
}
