package com.alzswell.connection.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.connection.api.ConnectionResponses.ConnectionDetail;
import com.alzswell.connection.api.ConnectionResponses.ConnectionList;
import com.alzswell.connection.application.FinancialConnectionQueryService;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/connections")
@Validated
@PreAuthorize("(#customerId == authentication.name and hasAuthority('FINANCIAL_CONNECTION_READ')) or "
        + "hasAuthority('FINANCIAL_CONNECTION_READ_ALL')")
public class CustomerConnectionController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final FinancialConnectionQueryService queryService;

    public CustomerConnectionController(FinancialConnectionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ConnectionList>> connections(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("CUSTOMER_CONNECTIONS_RETRIEVED", "금융기관 연결 목록을 조회했습니다.",
                queryService.connections(customerId));
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<ConnectionDetail>> connection(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @PathVariable UUID connectionId) {
        return ApiResponses.ok("CUSTOMER_CONNECTION_RETRIEVED", "금융기관 연결 상세를 조회했습니다.",
                queryService.connection(customerId, connectionId));
    }
}
