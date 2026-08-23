package com.alzswell.transfer.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.transfer.api.TransferPreviewErrorCode;
import com.alzswell.transfer.api.TransferPreviewRequests;
import com.alzswell.transfer.api.TransferPreviewResponses.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransferPreviewService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final JdbcTemplate jdbc;

    public TransferPreviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BeneficiaryList beneficiaries(String customerId) {
        List<Beneficiary> items = jdbc.query("""
                select b.beneficiary_id,b.institution_id,i.display_name institution_name,
                       b.display_name,b.masked_account_reference,b.beneficiary_type,b.status,
                       b.favorite,b.provider_mode,b.data_as_of
                  from customer_beneficiary_snapshot b
                  join financial_institution i on i.institution_id=b.institution_id
                 where b.customer_id=?
                 order by b.favorite desc,b.display_name,b.beneficiary_id
                """, (rs, row) -> new Beneficiary(rs.getObject("beneficiary_id", UUID.class),
                rs.getString("institution_id"), rs.getString("institution_name"),
                rs.getString("display_name"), rs.getString("masked_account_reference"),
                rs.getString("beneficiary_type"), rs.getString("status"), rs.getBoolean("favorite"),
                rs.getString("provider_mode"), rs.getObject("data_as_of", LocalDate.class)), customerId);
        LocalDate dataAsOf = items.stream().map(Beneficiary::dataAsOf).max(LocalDate::compareTo).orElse(null);
        return new BeneficiaryList(items, items.size(), dataAsOf, true, false);
    }

    public TransferLimit transferLimit(String customerId) {
        List<TransferLimit> rows = jdbc.query("""
                select limit_snapshot_id,per_transfer_limit,daily_limit,daily_used_amount,
                       daily_remaining_amount,currency,data_as_of,provider_mode
                  from customer_transfer_limit_snapshot
                 where customer_id=? order by data_as_of desc,limit_snapshot_id desc limit 1
                """, (rs, row) -> new TransferLimit(rs.getObject("limit_snapshot_id", UUID.class),
                rs.getBigDecimal("per_transfer_limit"), rs.getBigDecimal("daily_limit"),
                rs.getBigDecimal("daily_used_amount"), rs.getBigDecimal("daily_remaining_amount"),
                rs.getString("currency"), rs.getObject("data_as_of", LocalDate.class),
                rs.getString("provider_mode"), true, false), customerId);
        if (rows.size() != 1) throw new BusinessException(TransferPreviewErrorCode.LIMIT_NOT_AVAILABLE);
        return rows.getFirst();
    }

    public SimulationResult simulate(TransferPreviewRequests.Simulation command) {
        Evaluation evaluation = evaluate(command.customerId(), command.sourceAccountId(),
                command.beneficiaryId(), command.amount(), command.currency());
        List<ValidationCheck> checks = evaluation.financialChecks();
        String outcome = outcome(checks);
        return new SimulationResult(evaluation.context(), ZERO, command.amount(),
                evaluation.context().availableBalance().subtract(command.amount()), outcome, checks,
                evaluation.limit().dataAsOf(), true, false, false, false);
    }

    public ValidationResult validate(TransferPreviewRequests.Validation command) {
        Evaluation evaluation = evaluate(command.customerId(), command.sourceAccountId(),
                command.beneficiaryId(), command.amount(), command.currency());
        List<ValidationCheck> checks = new ArrayList<>(evaluation.financialChecks());
        boolean recipientConfirmed = Boolean.TRUE.equals(command.recipientConfirmed());
        checks.add(new ValidationCheck("RECIPIENT_CONFIRMED", recipientConfirmed,
                recipientConfirmed ? "고객이 마스킹된 수취인을 확인했습니다."
                        : "이체 전 고객의 수취인 확인이 필요합니다."));
        boolean allowed = checks.stream().allMatch(ValidationCheck::passed);
        return new ValidationResult(evaluation.context(), command.purposeCode(), allowed,
                allowed ? "PREVIEW_VALID" : "PREVIEW_BLOCKED", List.copyOf(checks),
                evaluation.limit().dataAsOf(), true, false, false, false);
    }

    private Evaluation evaluate(String customerId, UUID accountId, UUID beneficiaryId,
                                BigDecimal amount, String currency) {
        List<SourceAccount> accounts = jdbc.query("""
                select account_id,available_balance,currency,account_status
                  from customer_account_snapshot
                 where customer_id=? and account_id=?
                """, (rs, row) -> new SourceAccount(rs.getObject("account_id", UUID.class),
                rs.getBigDecimal("available_balance"), rs.getString("currency"),
                rs.getString("account_status")), customerId, accountId);
        List<Beneficiary> beneficiaries = jdbc.query("""
                select b.beneficiary_id,b.institution_id,i.display_name institution_name,
                       b.display_name,b.masked_account_reference,b.beneficiary_type,b.status,
                       b.favorite,b.provider_mode,b.data_as_of
                  from customer_beneficiary_snapshot b
                  join financial_institution i on i.institution_id=b.institution_id
                 where b.customer_id=? and b.beneficiary_id=?
                """, (rs, row) -> new Beneficiary(rs.getObject("beneficiary_id", UUID.class),
                rs.getString("institution_id"), rs.getString("institution_name"),
                rs.getString("display_name"), rs.getString("masked_account_reference"),
                rs.getString("beneficiary_type"), rs.getString("status"), rs.getBoolean("favorite"),
                rs.getString("provider_mode"), rs.getObject("data_as_of", LocalDate.class)),
                customerId, beneficiaryId);
        if (accounts.size() != 1 || beneficiaries.size() != 1) {
            throw new BusinessException(TransferPreviewErrorCode.RESOURCE_NOT_FOUND);
        }
        SourceAccount account = accounts.getFirst();
        Beneficiary beneficiary = beneficiaries.getFirst();
        TransferLimit limit = transferLimit(customerId);
        EvaluationContext context = new EvaluationContext(account.accountId(), account.availableBalance(),
                beneficiary.beneficiaryId(), beneficiary.displayName(), beneficiary.maskedAccountReference(),
                amount, currency, limit.perTransferLimit(), limit.dailyRemainingAmount());
        List<ValidationCheck> checks = List.of(
                check("SOURCE_ACCOUNT_ACTIVE", "ACTIVE".equals(account.status()),
                        "출금계좌가 이체 가능한 활성 상태입니다.", "출금계좌가 활성 상태가 아닙니다."),
                check("BENEFICIARY_ACTIVE", "ACTIVE".equals(beneficiary.status()),
                        "마스킹된 수취인이 활성 상태입니다.", "수취인이 비활성 상태입니다."),
                check("CURRENCY_MATCHED", account.currency().equals(currency) && limit.currency().equals(currency),
                        "통화가 합성 계좌·한도와 일치합니다.", "통화가 합성 계좌·한도와 일치하지 않습니다."),
                check("AVAILABLE_BALANCE_SUFFICIENT", account.availableBalance().compareTo(amount) >= 0,
                        "가용잔액 범위 안입니다.", "가용잔액을 초과합니다."),
                check("PER_TRANSFER_LIMIT_ALLOWED", limit.perTransferLimit().compareTo(amount) >= 0,
                        "건별 합성 이체한도 범위 안입니다.", "건별 합성 이체한도를 초과합니다."),
                check("DAILY_LIMIT_ALLOWED", limit.dailyRemainingAmount().compareTo(amount) >= 0,
                        "일일 합성 잔여한도 범위 안입니다.", "일일 합성 잔여한도를 초과합니다.")
        );
        return new Evaluation(context, limit, checks);
    }

    private ValidationCheck check(String code, boolean passed, String success, String failure) {
        return new ValidationCheck(code, passed, passed ? success : failure);
    }

    private String outcome(List<ValidationCheck> checks) {
        return checks.stream().allMatch(ValidationCheck::passed) ? "SIMULATION_ALLOWED" : "SIMULATION_BLOCKED";
    }

    private record SourceAccount(UUID accountId, BigDecimal availableBalance, String currency, String status) {}
    private record Evaluation(EvaluationContext context, TransferLimit limit,
                              List<ValidationCheck> financialChecks) {}
}
