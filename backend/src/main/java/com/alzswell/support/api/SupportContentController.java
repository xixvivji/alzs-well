package com.alzswell.support.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.support.application.SupportContentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/support")
public class SupportContentController {
    private final SupportContentService service;

    public SupportContentController(SupportContentService service) {
        this.service = service;
    }

    @GetMapping("/faqs")
    @Operation(
            summary = "ALZ's well 합성 FAQ 조회",
            description = "승인된 고객지원 문구만 조회하며 외부 고객센터나 모델을 호출하지 않습니다."
    )
    @PreAuthorize("hasAuthority('SUPPORT_CONTENT_READ')")
    public ResponseEntity<ApiResponse<SupportResponses.FaqList>> faqs(
            @RequestParam(required = false)
            @Pattern(regexp = "GENERAL|SECURITY|ALERTS|PRIVACY|ACCESSIBILITY") String category,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ApiResponses.ok(
                "SUPPORT_FAQS_RETRIEVED",
                "합성 고객지원 FAQ를 조회했습니다.",
                service.faqs(category, limit)
        );
    }

    @GetMapping("/notices")
    @Operation(
            summary = "안심은행 합성 공지 조회",
            description = "고정 합성 공지 snapshot만 조회하며 실제 금융기관 공지 API를 호출하지 않습니다."
    )
    @PreAuthorize("hasAuthority('SUPPORT_CONTENT_READ')")
    public ResponseEntity<ApiResponse<SupportResponses.NoticeList>> notices(
            @RequestParam(required = false)
            @Pattern(regexp = "SERVICE|SECURITY|MAINTENANCE|PRODUCT") String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ApiResponses.ok(
                "SUPPORT_NOTICES_RETRIEVED",
                "안심은행 합성 공지를 조회했습니다.",
                service.notices(category, from, to, limit)
        );
    }
}
