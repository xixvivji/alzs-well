package com.alzswell.staffaccess.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.ErrorCode;
import com.alzswell.common.security.AuditActor;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 접근 거부 트랜잭션이 종료된 뒤에도 판단 감사이력을 남기기 위한 예외입니다.
 */
public final class StaffAccessAuditException extends BusinessException {
    private final Decision decision;

    public StaffAccessAuditException(ErrorCode errorCode, Decision decision) {
        super(errorCode);
        this.decision = decision;
    }

    public Decision decision() {
        return decision;
    }

    public record Decision(
            UUID evaluationId,
            UUID grantId,
            UUID staffPrincipalId,
            String customerId,
            String purposeCode,
            String scopeCode,
            boolean allowed,
            String decisionCode,
            String resourceType,
            String resourceId,
            AuditActor actor,
            OffsetDateTime occurredAt
    ) {}
}
