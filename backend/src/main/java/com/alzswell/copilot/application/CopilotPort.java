package com.alzswell.copilot.application;

import java.util.List;
import java.util.UUID;

public interface CopilotPort {
    CopilotDraft generate(CopilotFacts facts);

    record CopilotFacts(String draftType, String customerResponseCode,
                        List<String> reasonCodes, List<String> unconfirmedItems) {
    }

    record CopilotDraft(String summary, List<String> suggestedQuestions, List<String> checklist,
                        List<String> basisReasonCodes, String generatedBy, boolean fallbackUsed,
                        boolean modelInvoked, boolean externalEgressAttempted,
                        String retrievalMode, List<CopilotCitation> citations) {
        public CopilotDraft {
            suggestedQuestions = List.copyOf(suggestedQuestions);
            checklist = List.copyOf(checklist);
            basisReasonCodes = List.copyOf(basisReasonCodes);
            citations = List.copyOf(citations);
        }
    }

    record CopilotCitation(String documentId, String versionLabel, UUID passageId,
                           String citationLabel, String sourceUrl, String retrievalMode) {
    }
}
