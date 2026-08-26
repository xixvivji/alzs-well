package com.alzswell.copilot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.alzswell.copilot.application.CopilotPort.CopilotDraft;
import com.alzswell.copilot.application.CopilotPort.CopilotFacts;
import com.alzswell.knowledge.api.KnowledgeResponses.Passage;
import com.alzswell.knowledge.api.KnowledgeResponses.SearchHit;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.RetrievalResult;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalGroundedCopilotAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-25T15:30:00Z"), ZoneOffset.UTC);
    private static final CopilotFacts FACTS = new CopilotFacts("CONSULTATION_NOTE", "UNSURE",
            List.of("DUPLICATE_TRANSFER"), List.of("TRANSFER_PURPOSE"));
    private final DeterministicCopilotAdapter deterministic = new DeterministicCopilotAdapter();

    @Test
    void keepsDeterministicPathWhenCopilotRagIsDisabled() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);

        CopilotDraft result = new RetrievalGroundedCopilotAdapter(false, retrieval, deterministic, CLOCK).generate(FACTS);

        assertThat(result.generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(result.citations()).isEmpty();
        verifyNoInteractions(retrieval);
    }

    @Test
    void groundsDraftOnlyWhenValidatedEvidenceExists() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        UUID passageId = UUID.fromString("95000000-0000-0000-0000-000000000001");
        Passage passage = new Passage(passageId, "DOC-SYN-001", "1.0.0", "신청 방법",
                "합성 승인 근거", List.of("중복 송금"), "합성 안내 > 신청 방법", null,
                LocalDate.of(2026, 1, 1), null);
        when(retrieval.retrieve(any())).thenReturn(new RetrievalResult(
                List.of(new SearchHit(passage, 1, "INTERNAL_RAG_HYBRID")),
                "INTERNAL_RAG_HYBRID", false, 0));

        CopilotDraft result = new RetrievalGroundedCopilotAdapter(true, retrieval, deterministic, CLOCK).generate(FACTS);

        assertThat(result.generatedBy()).isEqualTo("RAG_GROUNDED_TEMPLATE");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.modelInvoked()).isFalse();
        assertThat(result.externalEgressAttempted()).isFalse();
        assertThat(result.retrievalMode()).isEqualTo("INTERNAL_RAG_HYBRID");
        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.passageId()).isEqualTo(passageId);
            assertThat(citation.documentId()).isEqualTo("DOC-SYN-001");
        });
        verify(retrieval).retrieve(argThat(query -> query.asOf().equals(LocalDate.of(2026, 8, 26))
                && query.requestedAudience().equals("STAFF")
                && query.principalRoles().equals(List.of("PROTECTION_STAFF"))
                && !query.query().contains("TRANSFER_PURPOSE")));
    }

    @Test
    void fallsBackWhenEvidenceIsEmptyOrRetrievalFails() {
        KnowledgeRetrievalPort empty = mock(KnowledgeRetrievalPort.class);
        when(empty.retrieve(any())).thenReturn(new RetrievalResult(List.of(), "INTERNAL_RAG_HYBRID", false, 0));
        CopilotDraft emptyResult = new RetrievalGroundedCopilotAdapter(true, empty, deterministic, CLOCK).generate(FACTS);

        KnowledgeRetrievalPort failed = mock(KnowledgeRetrievalPort.class);
        when(failed.retrieve(any())).thenThrow(new IllegalStateException("synthetic failure"));
        CopilotDraft failedResult = new RetrievalGroundedCopilotAdapter(true, failed, deterministic, CLOCK).generate(FACTS);

        assertThat(emptyResult.generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(failedResult.generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(emptyResult.citations()).isEmpty();
        assertThat(failedResult.citations()).isEmpty();
    }

    @Test
    void doesNotTreatRetrievalFallbackAsGroundedEvidence() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        when(retrieval.retrieve(any())).thenReturn(new RetrievalResult(List.of(), "DETERMINISTIC_FALLBACK", true, 0));

        CopilotDraft result = new RetrievalGroundedCopilotAdapter(true, retrieval, deterministic, CLOCK).generate(FACTS);

        assertThat(result.generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void doesNotLabelLocalKeywordEvidenceAsInternalRag() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        when(retrieval.retrieve(any())).thenReturn(new RetrievalResult(List.of(), "DETERMINISTIC_KEYWORD", false, 0));

        CopilotDraft result = new RetrievalGroundedCopilotAdapter(true, retrieval, deterministic, CLOCK).generate(FACTS);

        assertThat(result.generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(result.retrievalMode()).isEqualTo("NONE");
    }
}
