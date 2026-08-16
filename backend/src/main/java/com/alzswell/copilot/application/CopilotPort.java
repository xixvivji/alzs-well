package com.alzswell.copilot.application;

import java.util.List;

public interface CopilotPort {
    CopilotDraft generate(CopilotFacts facts);

    record CopilotFacts(String draftType, String customerResponseCode,
                        List<String> reasonCodes, List<String> unconfirmedItems) {
    }

    record CopilotDraft(String summary, List<String> suggestedQuestions, List<String> checklist,
                        List<String> basisReasonCodes, String generatedBy, boolean fallbackUsed,
                        boolean modelInvoked, boolean externalEgressAttempted) {
    }
}
