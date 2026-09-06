package com.alzswell.copilot.application;

import com.alzswell.assistance.application.InternalFinancialAiClient;
import com.alzswell.assistance.application.InternalFinancialAiClient.StaffDraftRequest;
import com.alzswell.copilot.application.CopilotPort.CopilotDraft;
import com.alzswell.copilot.application.CopilotPort.CopilotFacts;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Only bounded synthetic codes and server-approved passages leave Spring. */
@Component
public class OptionalStaffDraftGenerator {
    private final boolean enabled;
    private final InternalFinancialAiClient client;
    private final java.util.Set<String> allowedDocuments;

    public OptionalStaffDraftGenerator(@Value("${app.copilot.generation-enabled:false}") boolean enabled,
                                       InternalFinancialAiClient client,
                                       @Value("${app.copilot.egress-document-ids:}") String allowedDocuments) {
        this.enabled = enabled;
        this.client = client;
        this.allowedDocuments = java.util.Arrays.stream(allowedDocuments.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public CopilotDraft generate(CopilotFacts facts, List<String> passages, CopilotDraft template) {
        if (!enabled || !facts.syntheticData()) return template;
        if (template.citations().isEmpty() || template.citations().stream()
                .anyMatch(citation -> !allowedDocuments.contains(citation.documentId()))) return template;
        try {
            var response = client.staffDraft(new StaffDraftRequest(true, facts.reasonCodes(),
                    facts.customerResponseCode() == null ? "UNKNOWN" : facts.customerResponseCode(), passages));
            var draft = response.draft();
            if (response.fallbackUsed() || !response.modelInvoked() || !response.externalEgressAttempted()
                    || !"BEDROCK_GENERATIVE_DRAFT".equals(response.generatedBy()) || draft == null
                    || !valid(draft.summary()) || !validList(draft.suggestedQuestions()) || !validList(draft.checklist())) {
                return fallback(template, response.modelInvoked(), response.externalEgressAttempted());
            }
            return new CopilotDraft(draft.summary(), draft.suggestedQuestions(), draft.checklist(),
                    facts.reasonCodes(), response.generatedBy(), false, true, true,
                    template.retrievalMode(), template.citations());
        } catch (RuntimeException exception) {
            // Remote timeout: invocation/egress cannot be established, never claim generated output.
            return fallback(template, false, false);
        }
    }

    private static CopilotDraft fallback(CopilotDraft template, boolean invoked, boolean egress) {
        return new CopilotDraft(template.summary(), template.suggestedQuestions(), template.checklist(),
                template.basisReasonCodes(), "RAG_GROUNDED_TEMPLATE", true, invoked, egress,
                template.retrievalMode(), template.citations());
    }

    private static boolean validList(List<String> values) {
        return values != null && !values.isEmpty() && values.size() <= 5 && values.stream().allMatch(OptionalStaffDraftGenerator::valid);
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank() && value.length() <= 600
                && !value.contains("<") && !value.contains(">")
                && !value.toLowerCase(java.util.Locale.ROOT).contains("http:")
                && !value.toLowerCase(java.util.Locale.ROOT).contains("https:");
    }
}
