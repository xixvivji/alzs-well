package com.alzswell.knowledge.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class InternalRagKnowledgeRetrievalAdapterTest {
    @Test
    void countsCitationsThatSpringRejects() {
        InternalKnowledgeSearchClient client=mock(InternalKnowledgeSearchClient.class);
        AiCitationValidator validator=mock(AiCitationValidator.class);
        RetrievalQuery query=new RetrievalQuery("  안심차단  신청 ",LocalDate.of(2026,8,14),null,
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);
        AiSearchHit hit=mock(AiSearchHit.class);
        when(client.search(any())).thenAnswer(invocation->{
            AiSearchRequest request=invocation.getArgument(0);
            assertThat(request.query()).isEqualTo("안심차단 신청");
            return new AiSearchResponse("1.0.0",request.requestId(),AiCitationValidator.hash(request.query()),List.of(hit));
        });
        when(validator.validate(hit,query)).thenReturn(Optional.empty());
        RetrievalResult result=new InternalRagKnowledgeRetrievalAdapter(client,validator).retrieve(query);
        assertThat(result.hits()).isEmpty();
        assertThat(result.rejectedCitations()).isOne();
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void rejectsMismatchedTopLevelResponse() {
        InternalKnowledgeSearchClient client=mock(InternalKnowledgeSearchClient.class);
        when(client.search(any())).thenReturn(new AiSearchResponse("1.0.0",UUID.randomUUID(),"sha256:bad",List.of()));
        RetrievalQuery query=new RetrievalQuery("안심차단",LocalDate.of(2026,8,14),null,
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);
        assertThatThrownBy(()->new InternalRagKnowledgeRetrievalAdapter(client,mock(AiCitationValidator.class)).retrieve(query))
                .isInstanceOf(AiRetrievalException.class).hasMessageNotContaining("안심차단");
    }
}
