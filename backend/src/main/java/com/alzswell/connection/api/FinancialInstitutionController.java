package com.alzswell.connection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.connection.api.ConnectionResponses.InstitutionDetail;
import com.alzswell.connection.api.ConnectionResponses.InstitutionList;
import com.alzswell.connection.application.FinancialConnectionQueryService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/financial-institutions")
@Validated
public class FinancialInstitutionController {
    private final FinancialConnectionQueryService queryService;

    public FinancialInstitutionController(FinancialConnectionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<InstitutionList>> institutions() {
        return ApiResponses.ok("FINANCIAL_INSTITUTIONS_RETRIEVED", "금융기관 목록을 조회했습니다.",
                queryService.institutions());
    }

    @GetMapping("/{institutionId}")
    public ResponseEntity<ApiResponse<InstitutionDetail>> institution(
            @PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$") String institutionId) {
        return ApiResponses.ok("FINANCIAL_INSTITUTION_RETRIEVED", "금융기관 상세를 조회했습니다.",
                queryService.institution(institutionId));
    }
}
