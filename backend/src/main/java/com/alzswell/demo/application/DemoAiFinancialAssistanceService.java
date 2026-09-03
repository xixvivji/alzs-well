package com.alzswell.demo.application;

import com.alzswell.assistance.application.AiAssistanceException;
import com.alzswell.assistance.application.InternalFinancialAiClient;
import com.alzswell.assistance.application.InternalFinancialAiClient.*;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.SensitiveTextPolicy;
import com.alzswell.demo.api.AiFinancialAssistanceRequests.IntentDraft;
import com.alzswell.demo.api.AiFinancialAssistanceResponses;
import com.alzswell.demo.api.BaselineListResponse;
import com.alzswell.demo.api.DemoErrorCode;
import com.alzswell.demo.domain.DemoSession;
import com.alzswell.detection.api.DetectionErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
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
    private static final Set<String> INTENT_CLARIFICATION_QUESTIONS = Set.of(
            "필수 납부를 계속 유지할지, 변경 전에 확인할지 선택해 주세요.",
            "필수 납부를 중단하려는 뜻인지 직접 확인해 주세요.",
            "필수 납부 유지와 중단 의향이 함께 보여 직접 확인해 주세요.",
            "쉬운 글, 음성 안내, 행원 설명 중 원하는 방식을 선택해 주세요.",
            "설명 방식이 여러 가지로 들립니다. 가장 원하는 한 가지를 선택해 주세요.",
            "어떤 상황에서 도움을 요청할지 선택해 주세요.",
            "도움 요청 조건이 서로 다르게 들립니다. 원하는 조건을 선택해 주세요.",
            "행원과 공유할 항목을 직접 선택해 주세요.",
            "공유할 항목을 선택해 주세요."
    );
    private static final List<String> FORBIDDEN_LANGUAGE = List.of(
            "치매", "알츠하이머", "진단", "사기 거래", "사기거래", "위험 고객", "위험고객",
            "계좌 정지", "계좌를 정지", "거래 정지", "자동 연락", "보호자 연락", "자동 송금", "자동 이체"
    );
    private static final Pattern SAFE_GENERATOR_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:/@-]{0,199}$");
    private static final List<Pattern> PAYMENT_KEEP_NEGATION_PATTERNS = List.of(
            Pattern.compile("(?:납부|공과금|보험료).{0,12}(?:중단|멈추|그만두|끊)하지\\s*(?:않|말)"),
            Pattern.compile("(?:납부|결제).{0,12}(?:바꾸지|변경하지)\\s*(?:않|말)"),
            Pattern.compile("(?:납부|결제)\\s*방식은?\\s*(?:그대로|유지)")
    );
    private static final List<Pattern> PAYMENT_STOP_PATTERNS = List.of(
            Pattern.compile("(?:납부|공과금|보험료).{0,12}(?:중단|멈추|그만)"),
            Pattern.compile("(?:납부하|납부를\\s*하|돈을\\s*내|요금을\\s*내)지\\s*(?:않|말)"),
            Pattern.compile("(?:납부|공과금|보험료).{0,12}유지하지\\s*(?:않|말)"),
            Pattern.compile("(?:공과금|보험료|요금)(?:은|는|을|를)?\\s*(?:계속\\s*)?(?:내|납부하)지\\s*(?:않|말)")
    );
    private static final double VALUE_TOLERANCE = 0.000_001;

    private final DemoSessionService sessionService;
    private final SyntheticFinanceQueryService financeQueryService;
    private final InternalFinancialAiClient aiClient;
    private final DemoAuditWriter auditWriter;
    private final DemoAiAssistanceAuditWriter assistanceAuditWriter;
    private final SensitiveTextPolicy sensitiveTextPolicy;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;

    public DemoAiFinancialAssistanceService(
            DemoSessionService sessionService,
            SyntheticFinanceQueryService financeQueryService,
            InternalFinancialAiClient aiClient,
            DemoAuditWriter auditWriter,
            DemoAiAssistanceAuditWriter assistanceAuditWriter,
            SensitiveTextPolicy sensitiveTextPolicy,
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${app.ai-assistance.enabled:false}") boolean enabled
    ) {
        this.sessionService = sessionService;
        this.financeQueryService = financeQueryService;
        this.aiClient = aiClient;
        this.auditWriter = auditWriter;
        this.assistanceAuditWriter = assistanceAuditWriter;
        this.sensitiveTextPolicy = sensitiveTextPolicy;
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
    }

    public AiFinancialAssistanceResponses.IntentSuggestion suggest(
            UUID sessionId, UUID demoRunId, String customerId, String utterance
    ) {
        requireRun(sessionId, demoRunId, customerId);
        String safeUtterance = sensitiveTextPolicy.validate(utterance, "utterance");
        if (enabled) {
            try {
                UUID requestId = UUID.randomUUID();
                IntentStructureResponse response = aiClient.structureIntent(
                        new IntentStructureRequest("1.0.0", requestId, safeUtterance));
                requireSafeIntentResponse(response, requestId, safeUtterance);
                if (response.fallbackUsed()) {
                    assistanceAuditWriter.fallback(sessionId, demoRunId, "INTENT_STRUCTURE",
                            "UPSTREAM_DECLARED_FALLBACK");
                } else {
                    assistanceAuditWriter.accepted(sessionId, demoRunId, "INTENT_STRUCTURE",
                            response.generatedBy(), response.modelInvoked());
                }
                return map(response);
            } catch (AiAssistanceException | IllegalArgumentException exception) {
                log.warn("AI intent assistance failed; using deterministic fallback: {}",
                        exception.getClass().getSimpleName());
                assistanceAuditWriter.fallback(sessionId, demoRunId, "INTENT_STRUCTURE",
                        fallbackReason(exception));
            }
        } else {
            assistanceAuditWriter.fallback(sessionId, demoRunId, "INTENT_STRUCTURE", "FEATURE_DISABLED");
        }
        return fallbackIntent(safeUtterance);
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
            int inserted = jdbc.update("""
                    insert into demo_financial_intent(
                        demo_session_id,demo_run_id,intent_id,customer_id,status,version,
                        payment_continuity,explanation_mode,help_condition,share_scopes,
                        disclaimer_accepted,created_at,updated_at
                    ) values(?,?,?,?,'DRAFT',1,?,?,?,?::varchar[],false,?,?)
                    on conflict (demo_session_id, demo_run_id) do nothing
                    """, sessionId, session.getDemoRunId(), intentId, customerId,
                    command.paymentContinuity(), command.explanationMode(), command.helpCondition(),
                    pgArray(scopes), now, now);
            if (inserted != 1) throw new BusinessException(DemoErrorCode.AI_INTENT_VERSION_CONFLICT);
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
        return analyzeFeatures(features,
                () -> assistanceAuditWriter.accepted(
                        sessionId, demoRunId, "CHANGE_ANALYSIS", "FASTAPI_EWMA_CUSUM", false),
                reason -> assistanceAuditWriter.fallback(
                        sessionId, demoRunId, "CHANGE_ANALYSIS", reason));
    }

    /** 로그인 합성 회원의 불변 기준선 snapshot만 사용해 식별정보 없이 AI 분석을 수행한다. */
    @Transactional(readOnly = true)
    public AiFinancialAssistanceResponses.ChangeAnalysis analyzeMember(String customerId) {
        List<MemberBaseline> baselines = jdbc.query("""
                select feature_code,baseline_value,current_value
                  from customer_baseline_snapshot
                 where customer_id=? and readiness='READY'
                 order by feature_code
                """, (rs, row) -> new MemberBaseline(
                        memberFeatureCode(rs.getString("feature_code")),
                        normalizedCount(rs.getBigDecimal("baseline_value")),
                        normalizedCount(rs.getBigDecimal("current_value"))), customerId);
        if (baselines.isEmpty()) throw new BusinessException(DetectionErrorCode.SNAPSHOT_NOT_READY);
        List<FeatureSeries> features = baselines.stream()
                .map(item -> featureSeries(item.featureCode(), item.baselineValue(), item.currentValue()))
                .toList();
        return analyzeFeatures(features, () -> {}, reason -> {});
    }

    private AiFinancialAssistanceResponses.ChangeAnalysis analyzeFeatures(
            List<FeatureSeries> features, Runnable accepted, Consumer<String> fallback
    ) {
        if (enabled) {
            try {
                List<ChangeAnalysisResponse> windows = new ArrayList<>();
                for (int baselineDays : List.of(30, 60, 90)) {
                    UUID requestId = UUID.randomUUID();
                    ChangeAnalysisResponse response = aiClient.analyzeChanges(
                            new ChangeAnalysisRequest("1.0.0", requestId, baselineDays, 30, features));
                    requireSafeChangeResponse(response, requestId, features, baselineDays);
                    windows.add(response);
                }
                accepted.run();
                return map(windows, false, "FASTAPI_EWMA_CUSUM");
            } catch (AiAssistanceException | IllegalArgumentException exception) {
                log.warn("AI change analysis failed; using baseline fallback: {} ({})",
                        exception.getClass().getSimpleName(), exception.getMessage());
                fallback.accept(fallbackReason(exception));
            }
        } else {
            fallback.accept("FEATURE_DISABLED");
        }
        return fallbackChanges(features);
    }

    private String memberFeatureCode(String featureCode) {
        return switch (featureCode) {
            case "MISSED_PAYMENT", "MISSED_RECURRING_PAYMENT", "MISSED_RECURRING_COUNT" ->
                    "MISSED_RECURRING_COUNT";
            case "DUPLICATE_TRANSFER", "DUPLICATE_TRANSFER_COUNT" -> "DUPLICATE_TRANSFER_COUNT";
            case "REPEATED_CONFIRMATION", "REPEATED_CONFIRMATION_COUNT" ->
                    "REPEATED_CONFIRMATION_COUNT";
            default -> throw new IllegalArgumentException("unsupported member baseline feature: " + featureCode);
        };
    }

    private String normalizedCount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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
                requireSafeLanguageResponse(response, requestId, featureCode,
                        change.baselineValue(), change.recentValue(), 30, mode);
                assistanceAuditWriter.accepted(sessionId, demoRunId, "PLAIN_LANGUAGE",
                        response.generationMode(), response.modelInvoked());
                return new AiFinancialAssistanceResponses.PlainLanguage(featureCode, response.title(), response.text(),
                        response.speechText(), mode, response.generationMode(), response.modelInvoked(),
                        response.fallbackUsed(), false, false);
            } catch (AiAssistanceException | IllegalArgumentException exception) {
                log.warn("AI plain-language generation failed; using constrained fallback: {}",
                        exception.getClass().getSimpleName());
                assistanceAuditWriter.fallback(sessionId, demoRunId, "PLAIN_LANGUAGE",
                        fallbackReason(exception));
            }
        } else {
            assistanceAuditWriter.fallback(sessionId, demoRunId, "PLAIN_LANGUAGE", "FEATURE_DISABLED");
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

    private void requireSafeIntentResponse(
            IntentStructureResponse response, UUID requestId, String safeUtterance
    ) {
        if (response == null) throw new IllegalArgumentException("missing AI intent response");
        IntentSuggestion value = response.suggestion();
        if (!"1.0.0".equals(response.contractVersion()) || !requestId.equals(response.requestId())
                || response.healthInferenceUsed()
                || response.financialActionExecuted() || value == null
                || response.modelInvoked() == response.fallbackUsed()
                || response.summary() == null || response.evidence() == null
                || response.clarifyingQuestions() == null || response.generatedBy() == null
                || !PAYMENT_VALUES.contains(value.paymentContinuity())
                || !EXPLANATION_VALUES.contains(value.explanationMode())
                || !HELP_VALUES.contains(value.helpCondition())
                || value.shareScopes() == null || value.shareScopes().size() > 4
                || !SCOPE_VALUES.containsAll(value.shareScopes())
                || new HashSet<>(value.shareScopes()).size() != value.shareScopes().size()
                || response.evidence().size() != EVIDENCE_FIELDS.size()
                || response.evidence().stream().anyMatch(java.util.Objects::isNull)
                || !response.evidence().stream().map(IntentFieldEvidence::field).collect(
                        java.util.stream.Collectors.toSet()).equals(Set.copyOf(EVIDENCE_FIELDS))
                || response.evidence().stream().anyMatch(item -> !EVIDENCE_FIELDS.contains(item.field())
                    || item.excerpt() == null || !Double.isFinite(item.confidence())
                    || item.confidence() < 0 || item.confidence() > 1)) {
            throw new IllegalArgumentException("unsafe AI intent response");
        }
        requireSafeCustomerText(response.summary(), "AI intent summary", 300);
        if (!expectedIntentSummary(value).equals(response.summary())
                || response.needsClarification() != !response.clarifyingQuestions().isEmpty()) {
            throw new IllegalArgumentException("AI intent summary does not match structured fields");
        }
        if (response.clarifyingQuestions().size() > 4) {
            throw new IllegalArgumentException("too many AI clarification questions");
        }
        response.clarifyingQuestions().forEach(question -> {
            requireSafeCustomerText(question, "AI clarification question", 200);
            if (!INTENT_CLARIFICATION_QUESTIONS.contains(question)) {
                throw new IllegalArgumentException("unapproved AI clarification question");
            }
        });
        if (!SAFE_GENERATOR_ID.matcher(response.generatedBy()).matches()) {
            throw new IllegalArgumentException("unsafe AI generator identifier");
        }
        String normalizedInput = normalizedForGrounding(safeUtterance);
        String paymentOverride = paymentNegationOverride(normalizedInput);
        if (paymentOverride != null && !paymentOverride.equals(value.paymentContinuity())) {
            throw new IllegalArgumentException("AI intent reverses explicit payment negation");
        }
        if ("REVIEW_BEFORE_CHANGE".equals(paymentOverride)
                && !response.clarifyingQuestions().contains(
                        "필수 납부를 중단하려는 뜻인지 직접 확인해 주세요.")) {
            throw new IllegalArgumentException("AI intent omits direct confirmation for payment stop wording");
        }
        response.evidence().forEach(item -> {
            String excerpt = requireSafeCustomerText(item.excerpt(), "AI intent evidence", 120);
            if (!normalizedInput.contains(normalizedForGrounding(excerpt))) {
                throw new IllegalArgumentException("ungrounded AI intent evidence");
            }
        });
    }

    private void requireSafeChangeResponse(
            ChangeAnalysisResponse response, UUID requestId, List<FeatureSeries> features,
            int expectedBaselineDays
    ) {
        if (response == null || !"1.0.0".equals(response.contractVersion())
                || !requestId.equals(response.requestId()) || response.diagnosisInferred()
                || response.financialActionExecuted() || response.changes() == null
                || response.baselineDays() != expectedBaselineDays || response.recentDays() != 30
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
        Map<String, FeatureSeries> featureByCode = features.stream().collect(
                java.util.stream.Collectors.toMap(FeatureSeries::featureCode, feature -> feature));
        for (ChangeSignal item : response.changes()) {
            ExpectedChange expected = recompute(featureByCode.get(item.featureCode()), expectedBaselineDays, 30);
            if (!close(item.baselineValue(), expected.baselineValue())
                    || !close(item.recentValue(), expected.recentValue())
                    || !close(item.delta(), expected.delta())
                    || !close(item.ewmaScore(), expected.ewmaScore())
                    || !close(item.cusumScore(), expected.cusumScore())
                    || !expected.direction().equals(item.direction())
                    || expected.changeDetected() != item.changeDetected()
                    || expected.persistent() != item.persistent()
                    || expected.dataSufficient() != item.dataSufficient()
                    || !expected.explanation().equals(item.explanation())) {
                throw new IllegalArgumentException("AI change response does not match input series: "
                        + expectedBaselineDays + ":" + item.featureCode());
            }
            requireSafeCustomerText(item.explanation(), "AI change explanation", 300);
        }
    }

    private void requireSafeLanguageResponse(
            PlainLanguageResponse response,
            UUID requestId,
            String featureCode,
            double baselineValue,
            double recentValue,
            int recentDays,
            String explanationMode
    ) {
        if (response == null || response.title() == null || response.text() == null
                || response.speechText() == null || response.generationMode() == null) {
            throw new IllegalArgumentException("missing AI plain-language response");
        }
        if (!"1.0.0".equals(response.contractVersion()) || !requestId.equals(response.requestId())
                || response.diagnosisInferred()
                || response.financialActionExecuted() || response.modelInvoked() || response.fallbackUsed()
                || response.text().length() > 300
                || response.speechText().length() > 300 || response.title().length() > 80
                || !"CONSTRAINED_NLG_V1".equals(response.generationMode())) {
            throw new IllegalArgumentException("unsafe AI language response");
        }
        String title = requireSafeCustomerText(response.title(), "AI plain-language title", 80);
        String text = requireSafeCustomerText(response.text(), "AI plain-language text", 300);
        String speech = requireSafeCustomerText(response.speechText(), "AI speech text", 300);
        String label = featureLabel(featureCode);
        String expectedTitle = label + " 변화를 확인해 주세요";
        String expectedText = expectedPlainLanguage(
                label, baselineValue, recentValue, recentDays, explanationMode);
        if (!expectedTitle.equals(title) || !expectedText.equals(text) || !expectedText.equals(speech)) {
            throw new IllegalArgumentException("AI plain-language response does not match supplied facts");
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
        String paymentOverride = paymentNegationOverride(normalized);
        boolean stopPayment = "REVIEW_BEFORE_CHANGE".equals(paymentOverride);
        String payment = "KEEP_ESSENTIAL_PAYMENTS".equals(paymentOverride) || (paymentOverride == null
                && (normalized.contains("계속") || normalized.contains("유지")))
                ? "KEEP_ESSENTIAL_PAYMENTS" : "REVIEW_BEFORE_CHANGE";
        String explanation = normalized.contains("음성") || normalized.contains("읽어")
                ? "VOICE_AND_TEXT" : normalized.contains("행원") ? "STAFF_EXPLANATION" : "SIMPLE_TEXT";
        String help = normalized.contains("반복") ? "ON_REPEATED_CHANGE"
                : normalized.contains("자동") ? "NEVER_AUTOMATIC" : "ON_CUSTOMER_REQUEST";
        List<String> questions = stopPayment
                ? List.of("필수 납부를 중단하려는 뜻인지 직접 확인해 주세요.",
                        "행원과 공유할 항목을 직접 선택해 주세요.")
                : List.of("행원과 공유할 항목을 직접 선택해 주세요.");
        String evidenceExcerpt = stopPayment ? "필수 납부 중단 의향 직접 확인" : fallbackEvidenceExcerpt(utterance);
        return new AiFinancialAssistanceResponses.IntentSuggestion(
                new AiFinancialAssistanceResponses.IntentFields(payment, explanation, help, List.of()),
                "고객이 직접 확인할 수 있도록 안전한 기본값으로 정리했습니다.",
                List.of(new AiFinancialAssistanceResponses.FieldEvidence(
                        "paymentContinuity", evidenceExcerpt, 0.5)),
                true, questions,
                "spring-deterministic-fallback-v1", false, true, false, false);
    }

    private FeatureSeries featureSeries(String featureCode, String baseline, String current) {
        List<Double> values = new ArrayList<>(java.util.Collections.nCopies(120, 0.0));
        int baselineCount = count(baseline);
        spread(values, 0, 30, baselineCount);
        spread(values, 30, 60, baselineCount);
        spread(values, 60, 90, baselineCount);
        spread(values, 90, 120, count(current));
        return new FeatureSeries(featureCode, List.copyOf(values), "COUNT");
    }

    private List<FeatureSeries> transactionFeatureSeries(
            UUID sessionId, UUID runId, String customerId, LocalDate windowEnd
    ) {
        LocalDate windowStart = windowEnd.minusDays(119);
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
            if (index < 0 || index >= 120) continue;
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
        return new ArrayList<>(java.util.Collections.nCopies(120, 0.0));
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
            List<ChangeAnalysisResponse> responses, boolean fallback, String mode
    ) {
        ChangeAnalysisResponse response = responses.stream()
                .filter(item -> item.baselineDays() == 60).findFirst().orElseThrow();
        List<AiFinancialAssistanceResponses.ChangeItem> changes = response.changes().stream().map(item ->
                new AiFinancialAssistanceResponses.ChangeItem(item.featureCode(), item.baselineValue(),
                        item.recentValue(), item.delta(), item.direction(), item.ewmaScore(), item.cusumScore(),
                        item.changeDetected(), item.persistent(), item.dataSufficient(), item.method(),
                        item.explanation())).toList();
        List<AiFinancialAssistanceResponses.ChangeWindow> windows = responses.stream().map(item ->
                new AiFinancialAssistanceResponses.ChangeWindow(item.baselineDays(), item.recentDays(),
                        item.changes().stream().map(change -> new AiFinancialAssistanceResponses.ChangeItem(
                                change.featureCode(), change.baselineValue(), change.recentValue(), change.delta(),
                                change.direction(), change.ewmaScore(), change.cusumScore(), change.changeDetected(),
                                change.persistent(), change.dataSufficient(), change.method(), change.explanation()
                        )).toList())).toList();
        return new AiFinancialAssistanceResponses.ChangeAnalysis(response.baselineDays(), response.recentDays(),
                response.baselineDays() + response.recentDays(), changes, mode, fallback, windows,
                true, false, false);
    }

    private AiFinancialAssistanceResponses.ChangeAnalysis fallbackChanges(List<FeatureSeries> features) {
        List<AiFinancialAssistanceResponses.ChangeWindow> windows = List.of(30, 60, 90).stream()
                .map(days -> fallbackWindow(features, days)).toList();
        List<AiFinancialAssistanceResponses.ChangeItem> items = windows.stream()
                .filter(window -> window.baselineDays() == 60).findFirst().orElseThrow().changes();
        return new AiFinancialAssistanceResponses.ChangeAnalysis(60, 30, 90, items,
                "BASELINE_RULE_FALLBACK", true, windows, true, false, false);
    }

    private AiFinancialAssistanceResponses.ChangeWindow fallbackWindow(
            List<FeatureSeries> features, int baselineDays
    ) {
        List<AiFinancialAssistanceResponses.ChangeItem> items = features.stream().map(feature -> {
            List<Double> values = feature.dailyValues().subList(
                    feature.dailyValues().size() - baselineDays - 30, feature.dailyValues().size());
            double baseline = values.subList(0, baselineDays).stream()
                    .mapToDouble(Double::doubleValue).sum() * 30.0 / baselineDays;
            double current = values.subList(baselineDays, baselineDays + 30).stream()
                    .mapToDouble(Double::doubleValue).sum();
            double delta = current - baseline;
            boolean changed = Math.abs(delta) >= Math.max(1, baseline * 0.5);
            String explanation = changed
                    ? "최근 30일 동안 " + featureLabel(feature.featureCode()) + "이 평소 "
                        + displayCount(baseline) + "에서 " + displayCount(current) + "로 달라졌습니다."
                    : "최근 30일 동안 " + featureLabel(feature.featureCode()) + "은 평소 범위와 비슷했습니다.";
            requireSafeCustomerText(explanation, "fallback change explanation", 300);
            return new AiFinancialAssistanceResponses.ChangeItem(feature.featureCode(), baseline, current, delta,
                    delta > 0 ? "INCREASE" : delta < 0 ? "DECREASE" : "STABLE", 0, 0,
                    changed, current >= 3, true, "BASELINE_RULE_FALLBACK", explanation);
        }).toList();
        return new AiFinancialAssistanceResponses.ChangeWindow(baselineDays, 30, items);
    }

    private AiFinancialAssistanceResponses.PlainLanguage fallbackLanguage(
            String featureCode, AiFinancialAssistanceResponses.ChangeItem change, String mode
    ) {
        String label = featureLabel(featureCode);
        String text = "최근 30일 동안 " + label + "이 평소 " + displayCount(change.baselineValue())
                + "에서 " + displayCount(change.recentValue()) + "로 달라졌습니다. "
                + ("STAFF_EXPLANATION".equals(mode) ? "잘 모르겠다면 행원과 함께 확인할 수 있습니다."
                : "알고 있는 변화인지 천천히 확인해 주세요.");
        requireSafeCustomerText(label + " 변화를 확인해 주세요", "fallback language title", 80);
        requireSafeCustomerText(text, "fallback language text", 300);
        return new AiFinancialAssistanceResponses.PlainLanguage(featureCode, label + " 변화를 확인해 주세요",
                text, text, mode, "SPRING_CONSTRAINED_FALLBACK_V1", false, true, false, false);
    }

    private String featureLabel(String featureCode) {
        return switch (featureCode) {
            case "MISSED_RECURRING_COUNT" -> "정기납부";
            case "DUPLICATE_TRANSFER_COUNT" -> "같은 송금";
            case "REPEATED_CONFIRMATION_COUNT" -> "거래결과 확인";
            case "NEW_COUNTERPARTY_COUNT" -> "새로운 분과의 거래";
            case "UNUSUAL_TIME_COUNT" -> "평소와 다른 시간대의 거래";
            default -> "평소와 다른 금액의 거래";
        };
    }

    private String expectedIntentSummary(IntentSuggestion value) {
        String payment = "KEEP_ESSENTIAL_PAYMENTS".equals(value.paymentContinuity())
                ? "필수 납부를 유지" : "납부 변경 전 확인";
        String explanation = switch (value.explanationMode()) {
            case "SIMPLE_TEXT" -> "쉬운 글";
            case "VOICE_AND_TEXT" -> "글과 음성";
            case "STAFF_EXPLANATION" -> "행원 설명";
            default -> throw new IllegalArgumentException("unsupported explanation mode");
        };
        String help = switch (value.helpCondition()) {
            case "ON_REPEATED_CHANGE" -> "반복된 변화가 있을 때 도움 요청";
            case "ON_CUSTOMER_REQUEST" -> "고객이 요청할 때 도움 제공";
            case "NEVER_AUTOMATIC" -> "자동 도움 요청 안 함";
            default -> throw new IllegalArgumentException("unsupported help condition");
        };
        String share = value.shareScopes().isEmpty()
                ? "행원 공유 없음" : value.shareScopes().size() + "개 항목 공유";
        return payment + ", " + explanation + " 방식, " + help + ", " + share + "으로 정리했습니다.";
    }

    private String expectedPlainLanguage(
            String label,
            double baselineValue,
            double recentValue,
            int recentDays,
            String explanationMode
    ) {
        String first = Math.abs(recentValue - baselineValue) < 0.01
                ? "최근 " + recentDays + "일 동안 " + label + "은 평소와 비슷했습니다."
                : "최근 " + recentDays + "일 동안 " + label + "이 평소 "
                    + countText(baselineValue) + "에서 " + countText(recentValue) + "로 "
                    + (recentValue > baselineValue ? "늘었습니다." : "줄었습니다.");
        String ending = switch (explanationMode) {
            case "SIMPLE_TEXT" -> "알고 있는 변화인지 천천히 확인해 주세요.";
            case "VOICE_AND_TEXT" -> "지금 들은 내용이 알고 있는 변화인지 확인해 주세요.";
            case "STAFF_EXPLANATION" -> "잘 모르겠다면 행원과 함께 확인할 수 있습니다.";
            default -> throw new IllegalArgumentException("unsupported explanation mode");
        };
        return first + " " + ending;
    }

    private String changeFeatureLabel(String featureCode) {
        return switch (featureCode) {
            case "MISSED_RECURRING_COUNT" -> "정기납부 누락";
            case "DUPLICATE_TRANSFER_COUNT" -> "중복송금";
            case "REPEATED_CONFIRMATION_COUNT" -> "거래결과 재확인";
            case "NEW_COUNTERPARTY_COUNT" -> "새 수취인 거래";
            case "UNUSUAL_TIME_COUNT" -> "평소와 다른 시간대 거래";
            case "UNUSUAL_AMOUNT_COUNT" -> "평소 범위를 벗어난 금액 거래";
            default -> throw new IllegalArgumentException("unsupported change feature");
        };
    }

    private ExpectedChange recompute(FeatureSeries feature, int baselineDays, int recentDays) {
        if (feature == null || feature.dailyValues() == null
                || feature.dailyValues().size() < baselineDays + recentDays
                || feature.dailyValues().stream().anyMatch(value -> value == null
                    || !Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException("invalid change-analysis input series");
        }
        List<Double> values = feature.dailyValues().subList(
                feature.dailyValues().size() - baselineDays - recentDays,
                feature.dailyValues().size());
        List<Double> baseline = values.subList(0, baselineDays);
        List<Double> recent = values.subList(baselineDays, baselineDays + recentDays);
        double baselineDaily = baseline.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double baselineValue = baselineDaily * recentDays;
        double recentValue = recent.stream().mapToDouble(Double::doubleValue).sum();
        double delta = recentValue - baselineValue;
        double variance = baseline.stream().mapToDouble(value -> Math.pow(value - baselineDaily, 2))
                .average().orElse(0);
        double scale = Math.max(Math.max(Math.sqrt(variance), Math.sqrt(Math.max(baselineDaily, 0) + 0.25)), 0.5);
        double ewma = baselineDaily;
        for (double value : recent) ewma = 0.3 * value + 0.7 * ewma;
        double ewmaScore = (ewma - baselineDaily) / scale;
        double positiveAllowance = 0.25 * scale;
        double negativeAllowance = Math.min(0.25 * scale, baselineDaily * 0.25);
        double positiveCusum = 0;
        double negativeCusum = 0;
        int increasedDays = 0;
        int decreasedDays = 0;
        for (double value : recent) {
            positiveCusum = Math.max(0,
                    positiveCusum + value - baselineDaily - positiveAllowance);
            negativeCusum = Math.max(0,
                    negativeCusum + baselineDaily - value - negativeAllowance);
            if (value > baselineDaily + 0.25 * scale) increasedDays++;
            if (value < baselineDaily - negativeAllowance) decreasedDays++;
        }
        double cusumScore = Math.max(positiveCusum, negativeCusum) / scale;
        int persistenceDays = Math.max(3, (int) Math.ceil(recentDays * 0.1));
        boolean persistent = delta > 0
                ? increasedDays >= persistenceDays
                : delta < 0 && decreasedDays >= persistenceDays;
        boolean meaningfulDelta = Math.abs(delta) >= Math.max(1, baselineValue * 0.5);
        boolean detected = meaningfulDelta
                && (Math.abs(ewmaScore) >= 1.5 || cusumScore >= 3) && persistent;
        String direction = delta > 0.25 ? "INCREASE" : delta < -0.25 ? "DECREASE" : "STABLE";
        String explanation = detected
                ? "최근 " + recentDays + "일 동안 " + changeFeatureLabel(feature.featureCode())
                    + "이 평소 " + countText(baselineValue) + "에서 " + countText(recentValue)
                    + "로 지속적으로 " + ("INCREASE".equals(direction) ? "증가했습니다." : "감소했습니다.")
                : "최근 " + recentDays + "일 동안 " + changeFeatureLabel(feature.featureCode())
                    + "은 평소 범위와 뚜렷하게 다른 장기 변화가 없습니다.";
        return new ExpectedChange(round(baselineValue, 2), round(recentValue, 2), round(delta, 2),
                direction, round(ewmaScore, 4), round(cusumScore, 4), detected, persistent,
                values.size() >= baselineDays + recentDays, explanation);
    }

    private String requireSafeCustomerText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || FORBIDDEN_LANGUAGE.stream().anyMatch(value::contains)) {
            throw new IllegalArgumentException("unsafe customer-facing AI text");
        }
        try {
            return sensitiveTextPolicy.validate(value, fieldName);
        } catch (BusinessException exception) {
            throw new IllegalArgumentException("unsafe customer-facing AI text", exception);
        }
    }

    private String normalizedForGrounding(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String fallbackEvidenceExcerpt(String utterance) {
        return List.of("공과금", "보험료", "계속", "유지", "변경 전", "먼저 확인", "음성", "읽어",
                        "행원", "직원", "반복", "요청할 때", "자동으로 하지")
                .stream().filter(utterance::contains).findFirst().orElse("선호 항목을 직접 확인");
    }

    private boolean matchesAny(List<Pattern> patterns, String value) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    private String paymentNegationOverride(String value) {
        if (matchesAny(PAYMENT_KEEP_NEGATION_PATTERNS, value)) return "KEEP_ESSENTIAL_PAYMENTS";
        if (matchesAny(PAYMENT_STOP_PATTERNS, value)) return "REVIEW_BEFORE_CHANGE";
        return null;
    }

    private String fallbackReason(RuntimeException exception) {
        return exception instanceof AiAssistanceException
                ? "UPSTREAM_UNAVAILABLE_OR_INVALID" : "UPSTREAM_RESPONSE_REJECTED";
    }

    private boolean close(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) <= VALUE_TOLERANCE;
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
    }

    private String countText(double value) {
        long rounded = BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
        return Math.abs(value - rounded) < 0.01 ? rounded + "회" : String.format(java.util.Locale.ROOT, "%.1f회", value);
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

    private record ExpectedChange(
            double baselineValue,
            double recentValue,
            double delta,
            String direction,
            double ewmaScore,
            double cusumScore,
            boolean changeDetected,
            boolean persistent,
            boolean dataSufficient,
            String explanation
    ) {}

    private record TransactionObservation(OffsetDateTime occurredAt, double amount, String counterparty) {}
    private record MemberBaseline(String featureCode, String baselineValue, String currentValue) {}
}
