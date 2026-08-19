package com.alzswell.protection.application;

import static com.alzswell.protection.api.ProtectionErrorCode.ACTION_NOT_FOUND;
import static com.alzswell.protection.api.ProtectionErrorCode.CUSTOMER_NOT_FOUND;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.protection.api.ProtectionRequests.EligibilityEvaluationCommand;
import com.alzswell.protection.api.ProtectionResponses.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProtectionCatalogService {
    private static final String POLICY_VERSION = "protection-guidance-policy-v1.0.0";
    private final JdbcClient jdbc;
    public ProtectionCatalogService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public ActionList actions() {
        List<ActionSummary> items = jdbc.sql("select * from protection_action_catalog order by display_order, action_code")
                .query(this::summary).list();
        return new ActionList(items, items.size());
    }

    @Transactional(readOnly = true)
    public ActionDetail action(String actionCode) {
        ActionSummary action = jdbc.sql("select * from protection_action_catalog where action_code = ?")
                .param(actionCode).query(this::summary).optional()
                .orElseThrow(() -> new BusinessException(ACTION_NOT_FOUND));
        List<String> reasons = switch (actionCode) {
            case "SAFE_BLOCK_INFO" -> List.of("DUPLICATE_TRANSFER");
            case "BANK_CONSULTATION" -> List.of(
                    "MISSED_RECURRING_PAYMENT", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION");
            default -> List.of();
        };
        List<UUID> citations = switch (actionCode) {
            case "SAFE_BLOCK_INFO" -> List.of(UUID.fromString("95000000-0000-0000-0000-000000000001"));
            case "BANK_CONSULTATION" -> List.of(UUID.fromString("95000000-0000-0000-0000-000000000002"));
            default -> List.of();
        };
        String sourceUrl = jdbc.sql("select source_url from protection_action_catalog where action_code = ?")
                .param(actionCode).query(String.class).optional().orElse(null);
        return new ActionDetail(action, sourceUrl, reasons, citations, false);
    }

    @Transactional(readOnly = true)
    public EligibilityEvaluation evaluate(String actionCode, EligibilityEvaluationCommand command) {
        ActionDetail detail = action(actionCode);
        ensureCustomer(command.customerId());
        boolean supported = detail.supportedReasonCodes().contains(command.reasonCode());
        List<String> reasons = supported ? List.of("POLICY_REASON_MATCHED", "HUMAN_CONFIRMATION_REQUIRED")
                : List.of("POLICY_REASON_NOT_MATCHED");
        String stableInput = command.customerId() + ":" + actionCode + ":" + command.reasonCode()
                + ":" + POLICY_VERSION;
        String evaluationId = UUID.nameUUIDFromBytes(stableInput.getBytes(StandardCharsets.UTF_8)).toString();
        return new EligibilityEvaluation(evaluationId, command.customerId(), actionCode, command.reasonCode(),
                POLICY_VERSION, supported ? "GUIDANCE_ELIGIBLE" : "NOT_APPLICABLE", reasons,
                supported, false, false);
    }

    @Transactional(readOnly = true)
    public EnrollmentList enrollments(String customerId) {
        ensureCustomer(customerId);
        List<Enrollment> items = jdbc.sql("""
                select e.*, a.title, i.display_name
                  from customer_protection_enrollment e
                  join protection_action_catalog a on a.action_code=e.action_code
                  join financial_institution i on i.institution_id=e.institution_id
                 where e.customer_id=? order by a.display_order,e.enrollment_id
                """).param(customerId).query((rs, n) -> new Enrollment(
                        rs.getObject("enrollment_id", UUID.class), rs.getString("customer_id"),
                        rs.getString("action_code"), rs.getString("title"), rs.getString("institution_id"),
                        rs.getString("display_name"), rs.getString("enrollment_status"),
                        rs.getObject("observed_as_of", LocalDate.class), rs.getString("provider_mode"), true)).list();
        return new EnrollmentList(customerId, items, items.size(), false);
    }

    private void ensureCustomer(String customerId) {
        boolean exists = jdbc.sql("select exists(select 1 from customer_profile where customer_id=?)")
                .param(customerId).query(Boolean.class).single();
        if (!exists) throw new BusinessException(CUSTOMER_NOT_FOUND);
    }

    private ActionSummary summary(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ActionSummary(rs.getString("action_code"), rs.getString("title"),
                rs.getString("action_status"), rs.getString("execution_type"),
                rs.getString("eligibility_summary"), rs.getString("issuer"),
                rs.getObject("effective_from", LocalDate.class), rs.getObject("checked_at", LocalDate.class), false);
    }
}
