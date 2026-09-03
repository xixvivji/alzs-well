package com.alzswell.demo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alzswell.assistance.application.InternalFinancialAiClient;
import com.alzswell.assistance.application.InternalFinancialAiClient.ChangeAnalysisRequest;
import com.alzswell.assistance.application.InternalFinancialAiClient.ChangeAnalysisResponse;
import com.alzswell.assistance.application.InternalFinancialAiClient.ChangeSignal;
import com.alzswell.assistance.application.InternalFinancialAiClient.IntentFieldEvidence;
import com.alzswell.assistance.application.InternalFinancialAiClient.IntentStructureRequest;
import com.alzswell.assistance.application.InternalFinancialAiClient.IntentStructureResponse;
import com.alzswell.assistance.application.InternalFinancialAiClient.IntentSuggestion;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.SensitiveTextPolicy;
import com.alzswell.demo.api.AiFinancialAssistanceRequests.IntentDraft;
import com.alzswell.demo.api.DemoErrorCode;
import com.alzswell.demo.api.BaselineListResponse;
import com.alzswell.demo.domain.DemoSession;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DemoAiFinancialAssistanceServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String CUSTOMER_ID = DemoSessionService.CUSTOMER_ID;

    private final DemoSessionService sessionService = mock(DemoSessionService.class);
    private final SyntheticFinanceQueryService financeQueryService = mock(SyntheticFinanceQueryService.class);
    private final InternalFinancialAiClient aiClient = mock(InternalFinancialAiClient.class);
    private final DemoAuditWriter auditWriter = mock(DemoAuditWriter.class);
    private final DemoAiAssistanceAuditWriter assistanceAuditWriter = mock(DemoAiAssistanceAuditWriter.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC);

    private DemoAiFinancialAssistanceService service;

    @BeforeEach
    void setUp() {
        DemoSession session = new DemoSession(
                SESSION_ID, RUN_ID, 1, "sha256:customer-capability",
                OffsetDateTime.now(clock), OffsetDateTime.now(clock).plusHours(2));
        session.ingest("FIN_MGMT_AB_001", "sha256:snapshot", CUSTOMER_ID,
                DemoSessionService.ALERT_ID, DemoSessionService.CASE_ID, OffsetDateTime.now(clock));
        when(sessionService.requireFinancialFixture(SESSION_ID, CUSTOMER_ID)).thenReturn(session);
        service = new DemoAiFinancialAssistanceService(
                sessionService, financeQueryService, aiClient, auditWriter, assistanceAuditWriter,
                new SensitiveTextPolicy(), jdbcTemplate, clock, true);
    }

    @Test
    void rejectsSensitiveIntentBeforeCallingFastApi() {
        assertThatThrownBy(() -> service.suggest(
                SESSION_ID, RUN_ID, CUSTOMER_ID, "주민등록번호 900101-1234567을 참고해 주세요"))
                .isInstanceOf(BusinessException.class);

        verify(aiClient, never()).structureIntent(any());
        verify(assistanceAuditWriter, never()).fallback(any(), any(), anyString(), anyString());
    }

    @Test
    void rejectsUngroundedOrForbiddenAiIntentAndRemovesRawFallbackEvidence() {
        when(aiClient.structureIntent(any(IntentStructureRequest.class))).thenAnswer(invocation -> {
            IntentStructureRequest request = invocation.getArgument(0);
            return new IntentStructureResponse(
                    "1.0.0", request.requestId(),
                    new IntentSuggestion("KEEP_ESSENTIAL_PAYMENTS", "SIMPLE_TEXT",
                            "ON_CUSTOMER_REQUEST", List.of()),
                    "치매 진단 결과로 정리했습니다.",
                    List.of(
                            new IntentFieldEvidence("paymentContinuity", "공과금", 0.9),
                            new IntentFieldEvidence("explanationMode", "천천히", 0.9),
                            new IntentFieldEvidence("helpCondition", "근거 없는 문장", 0.9),
                            new IntentFieldEvidence("shareScopes", "공과금", 0.9)
                    ), false, List.of(), "safe-model:1.0", true, false, false, false
            );
        });
        String utterance = "공과금은 계속 납부하고 천천히 설명해 주세요";

        var result = service.suggest(SESSION_ID, RUN_ID, CUSTOMER_ID, utterance);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.evidence()).allSatisfy(evidence -> assertThat(evidence.excerpt())
                .isNotEqualTo(utterance));
        verify(assistanceAuditWriter).fallback(
                SESSION_ID, RUN_ID, "INTENT_STRUCTURE", "UPSTREAM_RESPONSE_REJECTED");
    }

    @Test
    void acceptsIntentOnlyWhenSummaryQuestionsAndEvidenceMatchTheStructuredFields() {
        when(aiClient.structureIntent(any(IntentStructureRequest.class))).thenAnswer(invocation -> {
            IntentStructureRequest request = invocation.getArgument(0);
            return new IntentStructureResponse(
                    "1.0.0", request.requestId(),
                    new IntentSuggestion("KEEP_ESSENTIAL_PAYMENTS", "SIMPLE_TEXT",
                            "ON_CUSTOMER_REQUEST", List.of("PAYMENT_PREFERENCE")),
                    "필수 납부를 유지, 쉬운 글 방식, 고객이 요청할 때 도움 제공, 1개 항목 공유으로 정리했습니다.",
                    List.of(
                            new IntentFieldEvidence("paymentContinuity", "공과금", 0.9),
                            new IntentFieldEvidence("explanationMode", "천천히", 0.9),
                            new IntentFieldEvidence("helpCondition", "요청할 때", 0.9),
                            new IntentFieldEvidence("shareScopes", "공유", 0.9)
                    ), false, List.of(), "hash-local:v1", false, true, false, false
            );
        });

        var result = service.suggest(SESSION_ID, RUN_ID, CUSTOMER_ID,
                "공과금은 계속 납부하고 천천히 설명해 주세요. 요청할 때 도움받고 행원에게 공유해 주세요.");

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.generatedBy()).isEqualTo("hash-local:v1");
        verify(assistanceAuditWriter).fallback(
                SESSION_ID, RUN_ID, "INTENT_STRUCTURE", "UPSTREAM_DECLARED_FALLBACK");
    }

    @Test
    void fallbackIntentHandlesPaymentNegationWithoutReversingCustomerMeaning() {
        var stop = service.suggest(
                SESSION_ID, RUN_ID, CUSTOMER_ID, "공과금은 계속 납부하지 말아 주세요");
        var keep = service.suggest(
                SESSION_ID, RUN_ID, CUSTOMER_ID, "공과금 납부를 중단하지 말아 주세요");

        assertThat(stop.suggestion().paymentContinuity()).isEqualTo("REVIEW_BEFORE_CHANGE");
        assertThat(stop.clarifyingQuestions()).contains("필수 납부를 중단하려는 뜻인지 직접 확인해 주세요.");
        assertThat(keep.suggestion().paymentContinuity()).isEqualTo("KEEP_ESSENTIAL_PAYMENTS");
    }

    @Test
    void rejectsUpstreamIntentThatReversesExplicitPaymentNegation() {
        when(aiClient.structureIntent(any(IntentStructureRequest.class))).thenAnswer(invocation -> {
            IntentStructureRequest request = invocation.getArgument(0);
            return new IntentStructureResponse(
                    "1.0.0", request.requestId(),
                    new IntentSuggestion("KEEP_ESSENTIAL_PAYMENTS", "SIMPLE_TEXT",
                            "ON_CUSTOMER_REQUEST", List.of()),
                    "필수 납부를 유지, 쉬운 글 방식, 고객이 요청할 때 도움 제공, 행원 공유 없음으로 정리했습니다.",
                    List.of(
                            new IntentFieldEvidence("paymentContinuity", "공과금", 0.9),
                            new IntentFieldEvidence("explanationMode", "천천히", 0.9),
                            new IntentFieldEvidence("helpCondition", "요청할 때", 0.9),
                            new IntentFieldEvidence("shareScopes", "공유", 0.9)
                    ), false, List.of(), "safe-model:1.0", true, false, false, false
            );
        });

        var result = service.suggest(SESSION_ID, RUN_ID, CUSTOMER_ID,
                "공과금은 계속 납부하지 말아 주세요. 천천히 설명하고 요청할 때 도움받고 공유하지 마세요.");

        assertThat(result.suggestion().paymentContinuity()).isEqualTo("REVIEW_BEFORE_CHANGE");
        assertThat(result.clarifyingQuestions()).contains("필수 납부를 중단하려는 뜻인지 직접 확인해 주세요.");
        verify(assistanceAuditWriter).fallback(
                SESSION_ID, RUN_ID, "INTENT_STRUCTURE", "UPSTREAM_RESPONSE_REJECTED");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void convertsConcurrentFirstIntentInsertConflictToVersionConflict() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        IntentDraft draft = new IntentDraft(0, "KEEP_ESSENTIAL_PAYMENTS", "SIMPLE_TEXT",
                "ON_CUSTOMER_REQUEST", List.of("PAYMENT_PREFERENCE"));

        assertThatThrownBy(() -> service.saveDraft(SESSION_ID, RUN_ID, CUSTOMER_ID, draft))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(DemoErrorCode.AI_INTENT_VERSION_CONFLICT));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recomputesChangeFactsAndRejectsTamperedFastApiValues() {
        BaselineListResponse.BaselineItem baseline = new BaselineListResponse.BaselineItem(
                "baseline-1", "REPEATED_CONFIRMATION_COUNT", "2", "8", "COUNT", "READY",
                "합성 기준선", List.of("REPEATED_CONFIRMATION"), "baseline-rules-v2.0.0",
                OffsetDateTime.now(clock));
        when(financeQueryService.baselines(SESSION_ID, CUSTOMER_ID)).thenReturn(new BaselineListResponse(
                CUSTOMER_ID,
                new BaselineListResponse.DatePeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 29)),
                new BaselineListResponse.DatePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30)),
                List.of(baseline), null));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(aiClient.analyzeChanges(any(ChangeAnalysisRequest.class))).thenAnswer(invocation -> {
            ChangeAnalysisRequest request = invocation.getArgument(0);
            List<ChangeSignal> forged = request.features().stream().map(feature -> new ChangeSignal(
                    feature.featureCode(), 0, 0, 0, "STABLE", 0, 0,
                    false, false, true, "EWMA_CUSUM_V1", "최근 변화가 없습니다."
            )).toList();
            return new ChangeAnalysisResponse(
                    "1.0.0", request.requestId(), request.baselineDays(), 30, forged,
                    "검증되지 않은 요약", List.of("검증되지 않은 질문"), List.of("검증되지 않은 항목"),
                    "EXPLAINABLE_CHANGE_GUIDANCE_V1", false, false);
        });

        var result = service.analyze(SESSION_ID, RUN_ID, CUSTOMER_ID);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.changes().getFirst().baselineValue()).isEqualTo(2);
        assertThat(result.changes().getFirst().recentValue()).isEqualTo(8);
        verify(assistanceAuditWriter).fallback(
                SESSION_ID, RUN_ID, "CHANGE_ANALYSIS", "UPSTREAM_RESPONSE_REJECTED");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void acceptsFastApiCompatibleIncreaseDecreaseAndStableChangeSignals() {
        List<BaselineListResponse.BaselineItem> baselines = List.of(
                baseline("MISSED_RECURRING_COUNT", "0", "8"),
                baseline("DUPLICATE_TRANSFER_COUNT", "10", "0"),
                baseline("REPEATED_CONFIRMATION_COUNT", "0", "0")
        );
        when(financeQueryService.baselines(SESSION_ID, CUSTOMER_ID)).thenReturn(new BaselineListResponse(
                CUSTOMER_ID,
                new BaselineListResponse.DatePeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 29)),
                new BaselineListResponse.DatePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30)),
                baselines, null));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(aiClient.analyzeChanges(any(ChangeAnalysisRequest.class))).thenAnswer(invocation -> {
            ChangeAnalysisRequest request = invocation.getArgument(0);
            List<ChangeSignal> changes = request.features().stream()
                    .map(this::fastApiCompatibleSignal).toList();
            return new ChangeAnalysisResponse(
                    "1.0.0", request.requestId(), request.baselineDays(), 30, changes,
                    "최근 30일 동안 정기납부 누락 등 2개 항목에서 평소와 다른 장기 변화가 확인됐습니다. "
                            + "이상이나 질환을 뜻하지 않으며, 알고 있는 생활 변화인지 먼저 확인해 주세요.",
                    List.of("최근 납부일이나 납부 방법을 바꾸셨나요?",
                            "같은 곳에 두 번 보낸 것으로 알고 계신가요?"),
                    List.of("표시된 기간과 횟수가 내 금융생활과 맞는지 확인합니다.",
                            "알고 있는 변화인지 또는 도움이 필요한지 직접 선택합니다.",
                            "납부일·납부 방법 변경 여부를 확인합니다.",
                            "같은 송금의 목적과 본인 인지 여부를 확인합니다."),
                    "EXPLAINABLE_CHANGE_GUIDANCE_V1", false, false);
        });

        var result = service.analyze(SESSION_ID, RUN_ID, CUSTOMER_ID);

        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.windowComparisons()).extracting(
                com.alzswell.demo.api.AiFinancialAssistanceResponses.ChangeWindow::baselineDays)
                .containsExactly(30, 60, 90);
        assertThat(result.changes()).filteredOn(item -> item.featureCode().equals("MISSED_RECURRING_COUNT"))
                .singleElement().satisfies(item -> {
                    assertThat(item.direction()).isEqualTo("INCREASE");
                    assertThat(item.changeDetected()).isTrue();
                    assertThat(item.explanation()).endsWith("지속적으로 증가했습니다.");
                });
        assertThat(result.changes()).filteredOn(item -> item.featureCode().equals("DUPLICATE_TRANSFER_COUNT"))
                .singleElement().satisfies(item -> {
                    assertThat(item.direction()).isEqualTo("DECREASE");
                    assertThat(item.changeDetected()).isTrue();
                    assertThat(item.explanation()).endsWith("지속적으로 감소했습니다.");
                });
        assertThat(result.changes()).filteredOn(item -> item.featureCode().equals("REPEATED_CONFIRMATION_COUNT"))
                .singleElement().satisfies(item -> {
                    assertThat(item.direction()).isEqualTo("STABLE");
                    assertThat(item.changeDetected()).isFalse();
                });
        verify(assistanceAuditWriter, never()).fallback(any(), any(), anyString(), anyString());
        verify(assistanceAuditWriter).accepted(
                SESSION_ID, RUN_ID, "CHANGE_ANALYSIS", "FASTAPI_EWMA_CUSUM", false);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsGuidanceThatDoesNotMatchTheVerifiedChangeFacts() {
        var baseline = baseline("REPEATED_CONFIRMATION_COUNT", "1", "5");
        when(financeQueryService.baselines(SESSION_ID, CUSTOMER_ID)).thenReturn(new BaselineListResponse(
                CUSTOMER_ID,
                new BaselineListResponse.DatePeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 29)),
                new BaselineListResponse.DatePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30)),
                List.of(baseline), null));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(aiClient.analyzeChanges(any(ChangeAnalysisRequest.class))).thenAnswer(invocation -> {
            ChangeAnalysisRequest request = invocation.getArgument(0);
            List<ChangeSignal> changes = request.features().stream()
                    .map(this::fastApiCompatibleSignal).toList();
            return new ChangeAnalysisResponse("1.0.0", request.requestId(), request.baselineDays(), 30,
                    changes, "고객이 위험하므로 즉시 조치해야 합니다.",
                    List.of("임의 질문"), List.of("임의 조치"),
                    "EXPLAINABLE_CHANGE_GUIDANCE_V1", false, false);
        });

        var result = service.analyze(SESSION_ID, RUN_ID, CUSTOMER_ID);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.guidanceMode()).isEqualTo("SPRING_EXPLAINABLE_GUIDANCE_FALLBACK_V1");
        assertThat(result.summary()).doesNotContain("위험");
        verify(assistanceAuditWriter).fallback(
                SESSION_ID, RUN_ID, "CHANGE_ANALYSIS", "UPSTREAM_RESPONSE_REJECTED");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void analyzesOnlyTheAuthenticatedMembersStoredSyntheticBaseline() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("feature_code")).thenReturn("REPEATED_CONFIRMATION");
        when(resultSet.getBigDecimal("baseline_value")).thenReturn(new BigDecimal("1.0000"));
        when(resultSet.getBigDecimal("current_value")).thenReturn(new BigDecimal("5.0000"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> List.of(
                        ((RowMapper) invocation.getArgument(1)).mapRow(resultSet, 0)));
        when(aiClient.analyzeChanges(any(ChangeAnalysisRequest.class))).thenAnswer(invocation -> {
            ChangeAnalysisRequest request = invocation.getArgument(0);
            var signal = new ChangeSignal("REPEATED_CONFIRMATION_COUNT", 1, 5, 4,
                    "INCREASE", 1.5003, 7.8303, true, true, true, "EWMA_CUSUM_V1",
                    "최근 30일 동안 거래결과 재확인이 평소 1회에서 5회로 지속적으로 증가했습니다.");
            return new ChangeAnalysisResponse("1.0.0", request.requestId(),
                    request.baselineDays(), 30, List.of(signal),
                    "최근 30일 동안 거래결과 재확인 등 1개 항목에서 평소와 다른 장기 변화가 확인됐습니다. "
                            + "이상이나 질환을 뜻하지 않으며, 알고 있는 생활 변화인지 먼저 확인해 주세요.",
                    List.of("거래 결과가 잘 보이지 않아 여러 번 확인하셨나요?"),
                    List.of("표시된 기간과 횟수가 내 금융생활과 맞는지 확인합니다.",
                            "알고 있는 변화인지 또는 도움이 필요한지 직접 선택합니다.",
                            "화면 이해나 거래 결과 확인에 어려움이 있었는지 확인합니다."),
                    "EXPLAINABLE_CHANGE_GUIDANCE_V1", false, false);
        });

        var result = service.analyzeMember("SYN_V3_PUBLIC_MEMBER_000001");

        assertThat(result.windowComparisons()).hasSize(3);
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.featureCode()).isEqualTo("REPEATED_CONFIRMATION_COUNT");
            assertThat(change.recentValue()).isEqualTo(5);
        });
        verify(aiClient, times(3)).analyzeChanges(any(ChangeAnalysisRequest.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void acceptsOnlyTheExactPlainLanguageSentenceGroundedInTheSuppliedFactAndMode() {
        DemoAiFinancialAssistanceService spyService = spy(service);
        var change = new com.alzswell.demo.api.AiFinancialAssistanceResponses.ChangeItem(
                "REPEATED_CONFIRMATION_COUNT", 2, 8, 6, "INCREASE", 1.7, 4.2,
                true, true, true, "EWMA_CUSUM_V1", "검증된 변화 설명");
        doReturn(new com.alzswell.demo.api.AiFinancialAssistanceResponses.ChangeAnalysis(
                60, 30, 90, List.of(change), "FASTAPI_EWMA_CUSUM", false,
                List.of(new com.alzswell.demo.api.AiFinancialAssistanceResponses.ChangeWindow(
                        60, 30, List.of(change))),
                "변화 요약", List.of("확인 질문"), List.of("확인 항목"), "EXPLAINABLE_CHANGE_GUIDANCE_V1",
                true, false, false))
                .when(spyService).analyze(SESSION_ID, RUN_ID, CUSTOMER_ID);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(aiClient.plainLanguage(any())).thenAnswer(invocation -> {
            var request = (InternalFinancialAiClient.PlainLanguageRequest) invocation.getArgument(0);
            String text = "최근 30일 동안 거래결과 확인이 평소 2회에서 8회로 늘었습니다. "
                    + "알고 있는 변화인지 천천히 확인해 주세요.";
            return new InternalFinancialAiClient.PlainLanguageResponse(
                    "1.0.0", request.requestId(), "거래결과 확인 변화를 확인해 주세요",
                    text, text, "CONSTRAINED_NLG_V1", false, false, false, false);
        });

        var result = spyService.plainLanguage(
                SESSION_ID, RUN_ID, CUSTOMER_ID, "REPEATED_CONFIRMATION_COUNT");

        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.speechText()).isEqualTo(result.text());
        verify(assistanceAuditWriter, never()).fallback(any(), any(), anyString(), anyString());
        verify(assistanceAuditWriter).accepted(
                SESSION_ID, RUN_ID, "PLAIN_LANGUAGE", "CONSTRAINED_NLG_V1", false);
    }

    private BaselineListResponse.BaselineItem baseline(String featureCode, String baseline, String current) {
        return new BaselineListResponse.BaselineItem(
                "baseline-" + featureCode, featureCode, baseline, current, "COUNT", "READY",
                "합성 기준선", List.of(featureCode), "baseline-rules-v2.0.0", OffsetDateTime.now(clock));
    }

    private ChangeSignal fastApiCompatibleSignal(InternalFinancialAiClient.FeatureSeries feature) {
        return switch (feature.featureCode()) {
            case "MISSED_RECURRING_COUNT" -> new ChangeSignal(
                    feature.featureCode(), 0, 8, 8, "INCREASE", 1.9059, 14.25,
                    true, true, true, "EWMA_CUSUM_V1",
                    "최근 30일 동안 정기납부 누락이 평소 0회에서 8회로 지속적으로 증가했습니다.");
            case "DUPLICATE_TRANSFER_COUNT" -> new ChangeSignal(
                    feature.featureCode(), 10, 0, -10, "DECREASE", -0.4364, 9.8198,
                    true, true, true, "EWMA_CUSUM_V1",
                    "최근 30일 동안 중복송금이 평소 10회에서 0회로 지속적으로 감소했습니다.");
            case "REPEATED_CONFIRMATION_COUNT" -> stableSignal(feature.featureCode(), "거래결과 재확인");
            case "NEW_COUNTERPARTY_COUNT" -> stableSignal(feature.featureCode(), "새 수취인 거래");
            case "UNUSUAL_TIME_COUNT" -> stableSignal(feature.featureCode(), "평소와 다른 시간대 거래");
            case "UNUSUAL_AMOUNT_COUNT" -> stableSignal(feature.featureCode(), "평소 범위를 벗어난 금액 거래");
            default -> throw new IllegalArgumentException("unexpected feature");
        };
    }

    private ChangeSignal stableSignal(String featureCode, String label) {
        return new ChangeSignal(featureCode, 0, 0, 0, "STABLE", 0, 0,
                false, false, true, "EWMA_CUSUM_V1",
                "최근 30일 동안 " + label + "은 평소 범위와 뚜렷하게 다른 장기 변화가 없습니다.");
    }
}
