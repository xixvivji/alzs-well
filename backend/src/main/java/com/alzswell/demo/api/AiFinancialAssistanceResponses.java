package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AiFinancialAssistanceResponses {
    private AiFinancialAssistanceResponses() {}

    public record IntentSuggestion(
            IntentFields suggestion, String summary, List<FieldEvidence> evidence,
            boolean needsClarification, List<String> clarifyingQuestions,
            String generatedBy, boolean modelInvoked, boolean fallbackUsed,
            boolean healthInferenceUsed, boolean financialActionExecuted
    ) {}

    public record IntentFields(String paymentContinuity, String explanationMode,
                               String helpCondition, List<String> shareScopes) {}

    public record FieldEvidence(String field, String excerpt, double confidence) {}

    public record Intent(
            UUID intentId, String customerId, String status, long version,
            String paymentContinuity, String explanationMode, String helpCondition,
            List<String> shareScopes, boolean disclaimerAccepted,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime approvedAt,
            boolean legallyBinding, boolean healthInferenceUsed
    ) {}

    public record ChangeAnalysis(
            int baselineDays, int recentDays, int analysisWindowDays,
            List<ChangeItem> changes, String analysisMode, boolean fallbackUsed,
            boolean syntheticData, boolean diagnosisInferred, boolean financialActionExecuted
    ) {}

    public record ChangeItem(
            String featureCode, double baselineValue, double recentValue, double delta,
            String direction, double ewmaScore, double cusumScore,
            boolean changeDetected, boolean persistent, boolean dataSufficient,
            String method, String explanation
    ) {}

    public record PlainLanguage(
            String featureCode, String title, String text, String speechText,
            String explanationMode, String generationMode, boolean modelInvoked,
            boolean fallbackUsed, boolean diagnosisInferred, boolean financialActionExecuted
    ) {}
}
