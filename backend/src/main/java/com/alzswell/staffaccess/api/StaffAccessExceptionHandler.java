package com.alzswell.staffaccess.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.staffaccess.application.StaffAccessAuditException;
import com.alzswell.staffaccess.application.StaffAccessDecisionAuditService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 업무 트랜잭션이 연결을 반환한 다음 거부 판단을 별도 단일 트랜잭션으로 보존합니다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class StaffAccessExceptionHandler {
    private final StaffAccessDecisionAuditService decisionAudit;

    public StaffAccessExceptionHandler(StaffAccessDecisionAuditService decisionAudit) {
        this.decisionAudit = decisionAudit;
    }

    @ExceptionHandler(StaffAccessAuditException.class)
    public ResponseEntity<ApiResponse<Void>> handle(StaffAccessAuditException exception) {
        StaffAccessAuditException.Decision decision = exception.decision();
        decisionAudit.record(decision.evaluationId(), decision.grantId(), decision.staffPrincipalId(),
                decision.customerId(), decision.purposeCode(), decision.scopeCode(), decision.allowed(),
                decision.decisionCode(), decision.resourceType(), decision.resourceId(), decision.actor(),
                decision.occurredAt());
        return ApiResponses.error(exception.getErrorCode(), exception.getMessage());
    }
}
