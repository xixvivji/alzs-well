package com.alzswell.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.alzswell.knowledge.application.KnowledgeRetrievalPort.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResilientKnowledgeRetrievalAdapterTest {
    private final RetrievalQuery query=new RetrievalQuery("안심차단",LocalDate.of(2026,8,14),"STAFF",
            List.of("PROTECTION_STAFF"),List.of("STAFF"),5);

    @Test
    void usesInternalRetrievalWhenEnabled() {
        InternalRagKnowledgeRetrievalAdapter internal=mock(InternalRagKnowledgeRetrievalAdapter.class);
        DeterministicKnowledgeRetrievalAdapter deterministic=mock(DeterministicKnowledgeRetrievalAdapter.class);
        RetrievalResult expected=new RetrievalResult(List.of(),"INTERNAL_RAG_HYBRID",false,0);
        when(internal.retrieve(query)).thenReturn(expected);
        assertThat(new ResilientKnowledgeRetrievalAdapter(true,internal,deterministic).retrieve(query)).isSameAs(expected);
        verifyNoInteractions(deterministic);
    }

    @Test
    void fallsBackForControlledAiFailures() {
        InternalRagKnowledgeRetrievalAdapter internal=mock(InternalRagKnowledgeRetrievalAdapter.class);
        DeterministicKnowledgeRetrievalAdapter deterministic=mock(DeterministicKnowledgeRetrievalAdapter.class);
        when(internal.retrieve(query)).thenThrow(new AiRetrievalException("timeout"));
        when(deterministic.retrieve(query)).thenReturn(new RetrievalResult(List.of(),"DETERMINISTIC_KEYWORD",false,0));
        RetrievalResult result=new ResilientKnowledgeRetrievalAdapter(true,internal,deterministic).retrieve(query);
        assertThat(result.retrievalMode()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void fallsBackWhenSpringRejectsEveryAiCitation() {
        InternalRagKnowledgeRetrievalAdapter internal=mock(InternalRagKnowledgeRetrievalAdapter.class);
        DeterministicKnowledgeRetrievalAdapter deterministic=mock(DeterministicKnowledgeRetrievalAdapter.class);
        when(internal.retrieve(query)).thenReturn(new RetrievalResult(
                List.of(),"INTERNAL_RAG_HYBRID",false,2));
        when(deterministic.retrieve(query)).thenReturn(new RetrievalResult(
                List.of(),"DETERMINISTIC_KEYWORD",false,0));

        RetrievalResult result=new ResilientKnowledgeRetrievalAdapter(true,internal,deterministic).retrieve(query);

        assertThat(result.retrievalMode()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.rejectedCitations()).isEqualTo(2);
        verify(deterministic).retrieve(query);
    }

    @Test
    void keepsDeterministicPathWhenDisabled() {
        InternalRagKnowledgeRetrievalAdapter internal=mock(InternalRagKnowledgeRetrievalAdapter.class);
        DeterministicKnowledgeRetrievalAdapter deterministic=mock(DeterministicKnowledgeRetrievalAdapter.class);
        RetrievalResult expected=new RetrievalResult(List.of(),"DETERMINISTIC_KEYWORD",false,0);
        when(deterministic.retrieve(query)).thenReturn(expected);
        assertThat(new ResilientKnowledgeRetrievalAdapter(false,internal,deterministic).retrieve(query)).isSameAs(expected);
        verifyNoInteractions(internal);
    }
}
