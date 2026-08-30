package com.alzswell.assistance.application;

import java.util.List;
import java.util.UUID;

public interface InternalFinancialAiClient {
    IntentStructureResponse structureIntent(IntentStructureRequest request);
    ChangeAnalysisResponse analyzeChanges(ChangeAnalysisRequest request);
    PlainLanguageResponse plainLanguage(PlainLanguageRequest request);

    record IntentStructureRequest(String contractVersion, UUID requestId, String utterance) {}
    record IntentSuggestion(String paymentContinuity, String explanationMode, String helpCondition,
                            List<String> shareScopes) {}
    record IntentFieldEvidence(String field, String excerpt, double confidence) {}
    record IntentStructureResponse(String contractVersion, UUID requestId, IntentSuggestion suggestion,
                                   String summary, List<IntentFieldEvidence> evidence,
                                   boolean needsClarification, List<String> clarifyingQuestions,
                                   String generatedBy, boolean modelInvoked, boolean fallbackUsed,
                                   boolean healthInferenceUsed, boolean financialActionExecuted) {}

    record FeatureSeries(String featureCode, List<Double> dailyValues, String unit) {}
    record ChangeAnalysisRequest(String contractVersion, UUID requestId, int baselineDays,
                                 int recentDays, List<FeatureSeries> features) {}
    record ChangeSignal(String featureCode, double baselineValue, double recentValue, double delta,
                        String direction, double ewmaScore, double cusumScore,
                        boolean changeDetected, boolean persistent, boolean dataSufficient,
                        String method, String explanation) {}
    record ChangeAnalysisResponse(String contractVersion, UUID requestId, int baselineDays,
                                  int recentDays, List<ChangeSignal> changes,
                                  boolean diagnosisInferred, boolean financialActionExecuted) {}

    record PlainLanguageFact(String featureCode, double baselineValue, double recentValue,
                             int recentDays, String unit) {}
    record PlainLanguageRequest(String contractVersion, UUID requestId, String explanationMode,
                                PlainLanguageFact fact) {}
    record PlainLanguageResponse(String contractVersion, UUID requestId, String title, String text,
                                 String speechText, String generationMode, boolean modelInvoked,
                                 boolean fallbackUsed, boolean diagnosisInferred,
                                 boolean financialActionExecuted) {}
}
