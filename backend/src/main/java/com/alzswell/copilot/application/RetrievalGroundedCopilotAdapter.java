package com.alzswell.copilot.application;

import com.alzswell.copilot.application.CopilotPort.CopilotCitation;
import com.alzswell.knowledge.api.KnowledgeResponses.SearchHit;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.RetrievalQuery;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.RetrievalResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RetrievalGroundedCopilotAdapter implements CopilotPort {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Map<String, String> REASON_TERMS = Map.of(
            "MISSED_RECURRING", "정기납부 미처리 고객 상담 안내",
            "MISSED_RECURRING_PAYMENT", "정기납부 미처리 고객 상담 안내",
            "DUPLICATE_TRANSFER", "중복 송금 고객 상담 안내",
            "REPEATED_CONFIRMATION", "거래 반복 확인 고객 상담 안내"
    );

    private final boolean enabled;
    private final KnowledgeRetrievalPort retrievalPort;
    private final DeterministicCopilotAdapter deterministic;
    private final Clock clock;

    public RetrievalGroundedCopilotAdapter(@Value("${app.copilot.rag-enabled:false}") boolean enabled,
            KnowledgeRetrievalPort retrievalPort, DeterministicCopilotAdapter deterministic, Clock clock) {
        this.enabled = enabled;
        this.retrievalPort = retrievalPort;
        this.deterministic = deterministic;
        this.clock = clock;
    }

    @Override
    public CopilotDraft generate(CopilotFacts facts) {
        if (!enabled) return deterministic.generate(facts);
        try {
            RetrievalResult result = retrievalPort.retrieve(new RetrievalQuery(
                    safeQuery(facts), LocalDate.now(clock.withZone(SERVICE_ZONE)), "STAFF",
                    List.of("PROTECTION_STAFF"), List.of("STAFF"), 3));
            if (result.fallbackUsed() || !"INTERNAL_RAG_HYBRID".equals(result.retrievalMode())
                    || result.hits().isEmpty()) return deterministic.generate(facts);
            CopilotDraft base = deterministic.generate(facts);
            List<CopilotCitation> citations = result.hits().stream().map(this::citation).toList();
            List<String> checklist = new java.util.ArrayList<>(base.checklist());
            citations.stream().map(CopilotCitation::citationLabel).distinct().limit(3)
                    .map(label -> "승인 근거 확인: " + label).forEach(checklist::add);
            return new CopilotDraft(
                    "승인된 내부 근거를 바탕으로 고객의 금융생활 변화를 추가 확인해야 합니다.",
                    base.suggestedQuestions(), checklist,
                    facts.reasonCodes(), "RAG_GROUNDED_TEMPLATE", false, false, false,
                    result.retrievalMode(), citations);
        } catch (RuntimeException exception) {
            return deterministic.generate(facts);
        }
    }

    private String safeQuery(CopilotFacts facts) {
        List<String> terms = facts.reasonCodes().stream().map(code -> REASON_TERMS.getOrDefault(code, "금융생활 변화 고객 상담 안내"))
                .distinct().toList();
        return terms.isEmpty() ? "금융생활 변화 고객 상담 안내" : String.join(" ", terms);
    }

    private CopilotCitation citation(SearchHit hit) {
        var passage = hit.passage();
        return new CopilotCitation(passage.documentId(), passage.versionLabel(), passage.passageId(),
                passage.citationLabel(), passage.sourceUrl(), hit.retrievalMode());
    }
}
