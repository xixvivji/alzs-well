package com.alzswell.staffaccess.application;

import com.alzswell.common.security.AuditActor;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccessDecisionAuditService {
    private final JdbcClient jdbc;

    public StaffAccessDecisionAuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(UUID evaluationId, UUID grantId, UUID staffPrincipalId, String customerId,
            String purposeCode, String scopeCode, boolean allowed, String decisionCode,
            String resourceType, String resourceId, AuditActor actor, OffsetDateTime occurredAt) {
        jdbc.sql("""
                insert into staff_access_decision_audit_event(
                    evaluation_id,grant_id,staff_principal_id,customer_id,purpose_code,scope_code,
                    allowed,decision_code,resource_type,resource_id,actor_principal_id,
                    actor_customer_id,actor_session_id,actor_type,occurred_at
                ) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """).params(evaluationId, grantId, staffPrincipalId, customerId, purposeCode, scopeCode,
                allowed, decisionCode, resourceType, resourceId, actor.principalId(), actor.customerId(),
                actor.sessionId(), actor.actorType(), occurredAt).update();
    }
}
