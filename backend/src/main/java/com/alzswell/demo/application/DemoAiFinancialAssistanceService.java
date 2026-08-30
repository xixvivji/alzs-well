package com.alzswell.demo.application;

import com.alzswell.assistance.application.AiAssistanceException;
import com.alzswell.assistance.application.InternalFinancialAiClient;
import com.alzswell.assistance.application.InternalFinancialAiClient.*;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.demo.api.AiFinancialAssistanceRequests.IntentDraft;
import com.alzswell.demo.api.AiFinancialAssistanceResponses;
import com.alzswell.demo.api.BaselineListResponse;
import com.alzswell.demo.api.DemoErrorCode;
import com.alzswell.demo.domain.DemoSession;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoAiFinancialAssistanceService {
    private static final Logger log = LoggerFactory.getLogger(DemoAiFinancialAssistanceService.class);
    private static final List<String> PAYMENT_VALUES = List.of("KEEP_ESSENTIAL_PAYMENTS", "REVIEW_BEFORE_CHANGE");
    private static final List<String> EXPLANATION_VALUES = List.of("SIMPLE_TEXT", "VOICE_AND_TEXT", "STAFF_EXPLANATION");
    private static final List<String> HELP_VALUES = List.of("ON_REPEATED_CHANGE", "ON_CUSTOMER_REQUEST", "NEVER_AUTOMATIC");
    private static final List<String> SCOPE_VALUES = List.of("PAYMENT_PREFERENCE", "EXPLANATION_PREFERENCE", "HELP_CONDITION", "ACCESSIBILITY");
    private static final List<String> EVIDENCE_FIELDS = List.of(
            "paymentContinuity", "explanationMode", "helpCondition", "shareScopes");
    private static final List<String> FORBIDDEN_LANGUAGE = List.of("치매", "진단", "사기 거래", "계좌를 정지", "자동 연락");

    private final DemoSessionService sessionService;
    private final SyntheticFinanceQueryService financeQueryService;
    private final InternalFinancialAiClient aiClient;
    private final DemoAuditWriter auditWriter;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;

    public DemoAiFinancialAssistanceService(
            DemoSessionService sessionService,
            SyntheticFinanceQueryService financeQueryService,
            InternalFinancialAiClient aiClient,
            DemoAuditWriter auditWriter,
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${app.ai-assistance.enabled:false}") boolean enabled
    ) {
        this.sessionService = sessionService;
        this.financeQueryService = financeQueryService;
        this.aiClient = aiClient;
        this.auditWriter = auditWriter;
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
    }

    public AiFinancialAssistanceResponses.IntentSuggestion suggest(
            UUID sessionId, UUID demoRunId, String customerId, String utterance
    ) {
        requireRun(sessionId, demoRunId, customerId);
        if (enabled) {
            try {
                UUID requestId = UUID.randomUUID();
                IntentStructureResponse response = aiClient.structureIntent(
                        new IntentStructureRequest("1.0.0", requestId, utterance));
                requireSafeIntentResponse(response, requestId);
                return map(response);
            } catch (AiAssistanceException | IllegalArgumentException exception) {
                log.warn("AI intent assistance failed; using deterministic fallback: {}",
                        exception.getClass().getSimpleName());
            }
        }
        return fallbackIntent(utterance);
    }

    @Transactional
    public AiFinancialAssistanceResponses.Intent saveDraft(
            UUID sessionId, UUID demoRunId, String customerId, IntentDraft command
    ) {
        DemoSession session = requireRun(sessionId, demoRunId, customerId);
        List<String> scopes = normalizedScopes(command.shareScopes());
        OffsetDateTime now = OffsetDateTime.now(clock);
        IntentRow current = find(sessionId, session.getDemoRunId());
        if (current == null) {
            if (command.expectedVersion() != 0) throw new BusinessException(DemoErrorCode.AI_INTENT_VERSION_CONFLICT);
            UUID intentId = UUID.randomUUID();
            jdbc.update("""
                    insert into demo_financial_intent(
                        demo_session_id,demo_run_id,intent_id,customer_id,status,version,
                        payment_continuity,explanation_mode,help_condition,share_scopes,
                        disclaimer_accepted,created_at,updated_at
                    ) values(?,?,?,?,'DRAFT',1,?,?,?,?::varchar[],false,?,?)
                    """, sessionId, session.getDemoRunId(), intentId, customerId,
                    command.paymentContinuity(), command.explanationMode(), command.helpCondition(),
                    pgArray(scopes), now, now);
        } else {
            if (!"DRAFT".equals(current.status())) throw new BusinessException(DemoErrorCode.AI_INTENT_INVALID_STATE);
            int changed = jdbc.update("""
                    update demo_financial_intent set version=version+1,payment_continuity=?,
                        explanation_mode=?,help_condition=?,share_scopes=?::varchar[],updated_at=?
                     where demo_session_id=? and demo_run_id=? and status='DRAFT' and version=?
                    """, command.paymentContinuity(), command.explanationMode(), command.helpCondition(),
                    pgArray(scopes), now, sessionId, session.getDemoRunId(), command.expectedVersion());
            if (changed != 1) throw new BusinessException(DemoErrorCode.AI_INTENT_VERSION_CONFLICT);
        }
        IntentRow saved = requireIntent(sessionId, session.getDemoRunId());
        auditWriter.write(sessionId, session.getDemoRunId(), "DEMO_AI_INTENT_DRAFT_SAVED",
                Map.of("intentId", saved.intentId().toString(), "version", saved.version(),
                        "shareScopeCount", saved.shareScopes().size(), "financialActionExecuted", false), now);
        return map(saved);
    }

    @Transactional
    public AiFinancialAssistanceResponses.Intent approve(
            UUID sessionId, UUID demoRunId, String customerId, long expectedVersion
    ) {
        DemoSession session = requireRun(sessionId, demoRunId, customerId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int changed = jdbc.update("""
                update demo_financial_intent set status='APPROVED',version=version+1,
                    disclaimer_accepted=true,approved_at=?,updated_at=?
                 where demo_session_id=? and demo_run_id=? and status='DRAFT' and version=?
                """, now, now, sessionId, session.getDemoRunId(), expectedVersion);
        if (changed != 1) {
            IntentRow current = find(sessionId, session.getDemoRunId());
            if (current == null) throw new BusinessException(DemoErrorCode.AI_INTENT_NOT_FOUND);
            if (!"DRAFT".equals(current.status())) throw new BusinessException(DemoErrorCode.AI_INTENT_INVALID_STATE);
            throw new BusinessException(DemoErrorCode.AI_INTENT_VERSION_CONFLICT);
        }
        IntentRow approved = requireIntent(sessionId, session.getDemoRunId());
        auditWriter.write(sessionId, session.getDemoRunId(), "DEMO_AI_INTENT_APPROVED",
                Map.of("intentId", approved.intentId().toString(), "version", approved.version(),
                        "legallyBinding", false, "financialActionExecuted", false), now);
        return map(approved);
    }

    @Transactional(readOnly = true)
    public AiFinancialAssistanceResponses.Intent current(
            UUID sessionId, UUID demoRunId, String customerId
    ) {
        DemoSession session = requireRun(sessionId, demoRunId, customerId);
        return map(requireIntent(sessionId, session.getDemoRunId()));
    }

    public AiFinancialAssistanceResponses.ChangeAnalysis analyze(
            UUID sessionId, UUID demoRunId, String customerId
    ) {
        DemoSession session = requireRun(sessionId, demoRunId, customerId);
        BaselineListResponse baselines = financeQueryService.baselines(sessionId, customerId);
        List<FeatureSeries> features = new ArrayList<>(baselines.items().stream()
                .map(item -> featureSeries(item.featureCode(), item.baselineValue(), item.currentValue()))
                .toList());
        features.addAll(transactionFeatureSeries(sessionId, session.getDemoRunId(), customerId,
                baselines.observationPeriod().to()));
        if (enabled) {
            try {
                UUID requestId = UUID.randomUUID();
                ChangeAnalysisResponse response = aiClient.analyzeChanges(
                        new ChangeAnalysisRequest("1.0.0", requestId, 60, 30, features));
                requireSafeChangeResponse(response, requestId, features);
                return map(response, false, "FASTAPI_EWMA_CUSUM");
            } catch (AiAssistanceException | IllegalArgumentException exception) {
                log.warn("AI change analysis failed; using baseline fallback: {}",
                        exception.getClass().getSimpleName());
            }
        }
        return fallbackChanges(features);
    }

    public AiFinancialAssistanceResponses.PlainLanguage plainLanguage(
            UUID sessionId, UUID demoRunId, String customerId, String featureCode
    ) {
        DemoSession session = requireRun(sessionId, demoRunId, customerId);
        AiFinancialAssistanceResponses.ChangeItem change = analyze(sessionId, demoRunId, customerId).changes()
                .stream().filter(item -> item.featureCode().equals(featureCode)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported change feature"));
        IntentRow intent = find(sessionId, session.getDemoRunId());
        String mode = intent == null ? "SIMPLE_TEXT" : intent.explanationMode();
        if (enabled) {
            try {
                UUID requestId = UUID.randomUUID();
                PlainLanguageResponse response = aiClient.plainLanguage(new PlainLanguageRequest(
                        "1.0.0", requestId, mode,
                        new PlainLanguageFact(featureCode, change.baselineValue(), change.recentValue(), 30, "COUNT")));
                requireSafeLanguageResponse(response, requestId);
                return new AiFinancialAssistanceResponses.PlainLanguage(featureCode, response.title(), response.text(),
                        response.speechText(), mode, response.generationMode(), response.modelInvoked(),
                        response.fallbackUsed(), false, false);
            } catch (AiAssistanceException | IllegalArgumentException exception) {
                log.warn("AI plain-language generation failed; using constrained fallback: {}",
                        exception.getClass().getSimpleName());
            }
        }
        return fallbackLanguage(featureCode, change, mode);
    }

    private DemoSession requireRun(UUID sessionId, UUID requestedRun, String customerId) {
        DemoSession session = sessionService.requireFinancialFixture(sessionId, customerId);
        if (requestedRun == null || !requestedRun.equals(session.getDemoRunId())) {
            throw new BusinessException(DemoErrorCode.RUN_STALE);
        }
        return session;
    }

    private void requireSafeIntentResponse(IntentStructureResponse response, UUID requestId) {
        if (response == null) throw new IllegalArgumentException("missing AI intent response");
        IntentSuggestion value = response.suggestion();
        if (!"1.0.0".equals(response.contractVersion()) || !requestId.equals(response.requestId())
                || response.healthInferenceUsed()
                || response.financialActionExecuted() || value == null
                || response.summary() == null || response.evidence() == null
                || response.clarifyingQuestions() == null || response.generatedBy() == null
                || !PAYMENT_VALUES.contains(value.paymentContinuity())
                || !EXPLANATION_VALUES.contains(value.explanationMode())
                || !HELP_VALUES.contains(value.helpCondition())
                || value.shareScopes() == null || value.shareScopes().size() > 4
                || !SCOPE_VALUES.containsAll(value.shareScopes())
                || response.evidence().stream().anyMatch(item -> item == null
                    || !EVIDENCE_FIELDS.contains(item.field())
                    || item.excerpt() == null || !Double.isFinite(item.confidence())
                    || item.confidence() < 0 || item.confidence() > 1)) {
            throw new IllegalArgumentException("unsafe AI intent response");
        }
    }

    private void requireSafeChangeResponse(
            ChangeAnalysisResponse response, UUID requestId, List<FeatureSeries> features
    ) {
        if (response == null || !"1.0.0".equals(response.contractVersion())
                || !requestId.equals(response.requestId()) || response.diagnosisInferred()
                || response.financialActionExecuted() || response.changes() == null
                || response.baselineDays() != 60 || response.recentDays() != 30
                || response.changes().size() != features.size()
                || response.changes().stream().anyMatch(java.util.Objects::isNull)
                || !response.changes().stream().map(ChangeSignal::featureCode).collect(java.util.stream.Collectors.toSet())
                    .equals(features.stream().map(FeatureSeries::featureCode).collect(java.util.stream.Collectors.toSet()))
                || response.changes().stream().anyMatch(item -> item == null || item.featureCode() == null
                    || item.explanation() == null || !"EWMA_CUSUM_V1".equals(item.method())
                    || !Double.isFinite(item.baselineValue()) || item.baselineValue() < 0
                    || !Double.isFinite(item.recentValue()) || item.recentValue() < 0
                    || !Double.isFinite(item.delta()) || !Double.isFinite(item.ewmaScore())
                    || !Double.isFinite(item.cusumScore()) || item.cusumScore() < 0)) {
            throw new IllegalArgumentException("unsafe AI change response");
        }
    }

    private void requireSafeLanguageResponse(PlainLanguageResponse response, UUID requestId) {
        if (response == null || response.title() == null || response.text() == null
                || response.speechText() == null || response.generationMode() == null) {
            throw new IllegalArgumentException("missing AI plain-language response");
        }
        String combined = response.title() + " " + response.text() + " " + response.speechText();
        if (!"1.0.0".equals(response.contractVersion()) || !requestId.equals(response.requestId())
                || response.diagnosisInferred()
                || response.financialActionExecuted() || response.text().length() > 300
                || response.speechText().length() > 300 || response.title().length() > 80
                || !"CONSTRAINED_NLG_V1".equals(response.generationMode())
                || FORBIDDEN_LANGUAGE.stream().anyMatch(combined::contains)) {
            throw new IllegalArgumentException("unsafe AI language response");
        }
    }

    private AiFinancialAssistanceResponses.IntentSuggestion map(IntentStructureResponse response) {
        IntentSuggestion value = response.suggestion();
        return new AiFinancialAssistanceResponses.IntentSuggestion(
                new AiFinancialAssistanceResponses.IntentFields(value.paymentContinuity(), value.explanationMode(),
                        value.helpCondition(), List.copyOf(value.shareScopes())),
                response.summary(), response.evidence().stream().map(item ->
                        new AiFinancialAssistanceResponses.FieldEvidence(item.field(), item.excerpt(), item.confidence())).toList(),
                response.needsClarification(), List.copyOf(response.clarifyingQuestions()),
                response.generatedBy(), response.modelInvoked(), response.fallbackUsed(), false, false);
    }

    private AiFinancialAssistanceResponses.IntentSuggestion fallbackIntent(String utterance) {
        String normalized = utterance.toLowerCase();
        String payment = normalized.contains("계속") || normalized.contains("유지")
                ? "KEEP_ESSENTIAL_PAYMENTS" : "REVIEW_BEFORE_CHANGE";
        String explanation = normalized.contains("음성") || normalized.contains("읽어")
                ? "VOICE_AND_TEXT" : normalized.contains("행원") ? "STAFF_EXPLANATION" : "SIMPLE_TEXT";
        String help = normalized.contains("반복") ? "ON_REPEATED_CHANGE"
                : normalized.contains("자동") ? "NEVER_AUTOMATIC" : "ON_CUSTOMER_REQUEST";
        return new AiFinancialAssistanceResponses.IntentSuggestion(
                new AiFinancialAssistanceResponses.IntentFields(payment, explanation, help, List.of()),
                "고객이 직접 확인할 수 있도록 안전한 기본값으로 정리했습니다.",
                List.of(new AiFinancialAssistanceResponses.FieldEvidence("paymentContinuity", utterance, 0.5)),
                true, List.of("행원과 공유할 항목을 직접 선택해 주세요."),
                "spring-deterministic-fallback-v1", false, true, false, false);
    }

    private FeatureSeries featureSeries(String featureCode, String baseline, String current) {
        List<Double> values = new ArrayList<>(java.util.Collections.nCopies(90, 0.0));
        spread(values, 0, 60, count(baseline));
        spread(values, 60, 90, count(current));
        return new FeatureSeries(featureCode, List.copyOf(values), "COUNT");
    }

    private List<FeatureSeries> transactionFeatureSeries(
            UUID sessionId, UUID runId, String customerId, LocalDate windowEnd
    ) {
        LocalDate windowStart = windowEnd.minusDays(89);
        LocalDate recentStart = windowEnd.minusDays(29);
        List<TransactionObservation> transactions = jdbc.query("""
                select t.occurred_at,t.amount,t.counterparty_display_name
                  from synthetic_transaction t
                  join synthetic_account a on a.demo_session_id=t.demo_session_id
                   and a.demo_run_id=t.demo_run_id and a.account_id=t.account_id
                 where t.demo_session_id=? and t.demo_run_id=? and a.customer_id=?
                   and t.direction='OUT' and t.occurred_at::date<=?
                 order by t.occurred_at,t.transaction_id
                """, (rs, row) -> new TransactionObservation(
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getBigDecimal("amount").doubleValue(),
                        rs.getString("counterparty_display_name")),
                sessionId, runId, customerId, windowEnd);

        List<Double> newCounterparties = emptySeries();
        List<Double> unusualTimes = emptySeries();
        List<Double> unusualAmounts = emptySeries();
        Set<String> seen = new HashSet<>();
        List<Double> baselineAmounts = transactions.stream()
                .filter(item -> !item.occurredAt().toLocalDate().isBefore(windowStart)
                        && item.occurredAt().toLocalDate().isBefore(recentStart))
                .map(TransactionObservation::amount).toList();
        double amountThreshold = unusualAmountThreshold(baselineAmounts);

        for (TransactionObservation transaction : transactions) {
            boolean firstCounterparty = seen.add(transaction.counterparty());
            LocalDate day = transaction.occurredAt().toLocalDate();
            if (day.isBefore(windowStart)) continue;
            int index = (int) java.time.temporal.ChronoUnit.DAYS.between(windowStart, day);
            if (index < 0 || index >= 90) continue;
            if (firstCounterparty) increment(newCounterparties, index);
            int hour = transaction.occurredAt().getHour();
            if (hour < 7 || hour >= 22) increment(unusualTimes, index);
            if (transaction.amount() > amountThreshold) increment(unusualAmounts, index);
        }
        return List.of(
                new FeatureSeries("NEW_COUNTERPARTY_COUNT", List.copyOf(newCounterparties), "COUNT"),
                new FeatureSeries("UNUSUAL_TIME_COUNT", List.copyOf(unusualTimes), "COUNT"),
                new FeatureSeries("UNUSUAL_AMOUNT_COUNT", List.copyOf(unusualAmounts), "COUNT")
        );
    }

    private List<Double> emptySeries() {
        return new ArrayList<>(java.util.Collections.nCopies(90, 0.0));
    }

    private void increment(List<Double> values, int index) {
        values.set(index, values.get(index) + 1.0);
    }

    private double unusualAmountThreshold(List<Double> baselineAmounts) {
        if (baselineAmounts.isEmpty()) return Double.MAX_VALUE;
        double mean = baselineAmounts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = baselineAmounts.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
        return Math.max(mean * 1.5, mean + 3 * Math.sqrt(variance));
    }

    private void spread(List<Double> values, int from, int to, int count) {
        if (count <= 0) return;
        int slots = Math.min(count, Math.min(7, to - from));
        for (int index = 0; index < count; index++) {
            int offset = Math.max(0, to - slots + (index % slots));
            values.set(offset, values.get(offset) + 1.0);
        }
    }

    private AiFinancialAssistanceResponses.ChangeAnalysis map(
            ChangeAnalysisResponse response, boolean fallback, String mode
    ) {
        List<AiFinancialAssistanceResponses.ChangeItem> changes = response.changes().stream().map(item ->
                new AiFinancialAssistanceResponses.ChangeItem(item.featureCode(), item.baselineValue(),
                        item.recentValue(), item.delta(), item.direction(), item.ewmaScore(), item.cusumScore(),
                        item.changeDetected(), item.persistent(), item.dataSufficient(), item.method(),
                        item.explanation())).toList();
        return new AiFinancialAssistanceResponses.ChangeAnalysis(response.baselineDays(), response.recentDays(),
                response.baselineDays() + response.recentDays(), changes, mode, fallback,
                true, false, false);
    }

    private AiFinancialAssistanceResponses.ChangeAnalysis fallbackChanges(List<FeatureSeries> features) {
        List<AiFinancialAssistanceResponses.ChangeItem> items = features.stream().map(feature -> {
            double baseline = feature.dailyValues().subList(0, 60).stream()
                    .mapToDouble(Double::doubleValue).sum() / 2.0;
            double current = feature.dailyValues().subList(60, 90).stream()
                    .mapToDouble(Double::doubleValue).sum();
            double delta = current - baseline;
            boolean changed = Math.abs(delta) >= Math.max(1, baseline * 0.5);
            String explanation = changed
                    ? "최근 30일 동안 " + featureLabel(feature.featureCode()) + "이 평소 "
                        + displayCount(baseline) + "에서 " + displayCount(current) + "로 달라졌습니다."
                    : "최근 30일 동안 " + featureLabel(feature.featureCode()) + "은 평소 범위와 비슷했습니다.";
            return new AiFinancialAssistanceResponses.ChangeItem(feature.featureCode(), baseline, current, delta,
                    delta > 0 ? "INCREASE" : delta < 0 ? "DECREASE" : "STABLE", 0, 0,
                    changed, current >= 3, true, "BASELINE_RULE_FALLBACK", explanation);
        }).toList();
        return new AiFinancialAssistanceResponses.ChangeAnalysis(60, 30, 90, items,
                "BASELINE_RULE_FALLBACK", true, true, false, false);
    }

    private AiFinancialAssistanceResponses.PlainLanguage fallbackLanguage(
            String featureCode, AiFinancialAssistanceResponses.ChangeItem change, String mode
    ) {
        String label = featureLabel(featureCode);
        String text = "최근 30일 동안 " + label + "이 평소 " + displayCount(change.baselineValue())
                + "에서 " + displayCount(change.recentValue()) + "로 달라졌습니다. "
                + ("STAFF_EXPLANATION".equals(mode) ? "잘 모르겠다면 행원과 함께 확인할 수 있습니다."
                : "알고 있는 변화인지 천천히 확인해 주세요.");
        return new AiFinancialAssistanceResponses.PlainLanguage(featureCode, label + " 변화를 확인해 주세요",
                text, text, mode, "SPRING_CONSTRAINED_FALLBACK_V1", false, true, false, false);
    }

    private String featureLabel(String featureCode) {
        return switch (featureCode) {
            case "MISSED_RECURRING_COUNT" -> "정기납부";
            case "DUPLICATE_TRANSFER_COUNT" -> "같은 송금";
            case "REPEATED_CONFIRMATION_COUNT" -> "거래결과 확인";
            case "NEW_COUNTERPARTY_COUNT" -> "새로운 분과의 거래";
            case "UNUSUAL_TIME_COUNT" -> "평소와 다른 시간대 거래";
            default -> "평소와 다른 금액 거래";
        };
    }

    private IntentRow requireIntent(UUID sessionId, UUID runId) {
        IntentRow row = find(sessionId, runId);
        if (row == null) throw new BusinessException(DemoErrorCode.AI_INTENT_NOT_FOUND);
        return row;
    }

    private IntentRow find(UUID sessionId, UUID runId) {
        return jdbc.query("select * from demo_financial_intent where demo_session_id=? and demo_run_id=?",
                (rs, row) -> new IntentRow(rs.getObject("intent_id", UUID.class), rs.getString("customer_id"),
                        rs.getString("status"), rs.getLong("version"), rs.getString("payment_continuity"),
                        rs.getString("explanation_mode"), rs.getString("help_condition"),
                        List.of((String[]) rs.getArray("share_scopes").getArray()),
                        rs.getBoolean("disclaimer_accepted"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class), rs.getObject("approved_at", OffsetDateTime.class)),
                sessionId, runId).stream().findFirst().orElse(null);
    }

    private AiFinancialAssistanceResponses.Intent map(IntentRow row) {
        return new AiFinancialAssistanceResponses.Intent(row.intentId(), row.customerId(), row.status(), row.version(),
                row.paymentContinuity(), row.explanationMode(), row.helpCondition(), row.shareScopes(),
                row.disclaimerAccepted(), row.createdAt(), row.updatedAt(), row.approvedAt(), false, false);
    }

    private List<String> normalizedScopes(List<String> scopes) {
        List<String> safe = scopes == null ? List.of() : List.copyOf(new LinkedHashSet<>(scopes));
        if (safe.size() > 4 || !SCOPE_VALUES.containsAll(safe)) throw new IllegalArgumentException("invalid share scopes");
        return safe;
    }

    private String pgArray(List<String> values) { return "{" + String.join(",", values) + "}"; }
    private int count(String value) { return Math.max(0, Integer.parseInt(value)); }
    private String displayCount(double value) { return Math.round(value) + "회"; }

    private record IntentRow(UUID intentId, String customerId, String status, long version,
                             String paymentContinuity, String explanationMode, String helpCondition,
                             List<String> shareScopes, boolean disclaimerAccepted,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime approvedAt) {}

    private record TransactionObservation(OffsetDateTime occurredAt, double amount, String counterparty) {}
}
