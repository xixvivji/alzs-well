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
            return new AiSearchResponse("1.0.0",request.requestId(),AiCitationValidator.hash(request.query()),
                    "RESULTS",false,null,List.of(hit));
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
        when(client.search(any())).thenReturn(new AiSearchResponse("1.0.0",UUID.randomUUID(),"sha256:bad",
                "NO_MATCH",false,"NO_RELEVANT_MATCH",List.of()));
        RetrievalQuery query=new RetrievalQuery("안심차단",LocalDate.of(2026,8,14),null,
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);
        assertThatThrownBy(()->new InternalRagKnowledgeRetrievalAdapter(client,mock(AiCitationValidator.class)).retrieve(query))
                .isInstanceOf(AiRetrievalException.class).hasMessageNotContaining("안심차단");
    }

    @Test
    void preservesPolicyAbstainAndNoMatchWithoutInventingResults() {
        InternalKnowledgeSearchClient client=mock(InternalKnowledgeSearchClient.class);
        RetrievalQuery query=new RetrievalQuery("안심차단",LocalDate.of(2026,8,14),null,
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);
        when(client.search(any())).thenAnswer(invocation->{
            AiSearchRequest request=invocation.getArgument(0);
            return new AiSearchResponse("1.0.0",request.requestId(),AiCitationValidator.hash(request.query()),
                    "POLICY_ABSTAIN",false,"POLICY_GUARDRAIL",List.of());
        });

        RetrievalResult policy=new InternalRagKnowledgeRetrievalAdapter(
                client,mock(AiCitationValidator.class)).retrieve(query);

        assertThat(policy.retrievalMode()).isEqualTo("INTERNAL_RAG_POLICY_ABSTAIN");
        assertThat(policy.hits()).isEmpty();

        doAnswer(invocation->{
            AiSearchRequest request=invocation.getArgument(0);
            return new AiSearchResponse("1.0.0",request.requestId(),AiCitationValidator.hash(request.query()),
                    "NO_MATCH",false,"NO_RELEVANT_MATCH",List.of());
        }).when(client).search(any());
        RetrievalResult noMatch=new InternalRagKnowledgeRetrievalAdapter(
                client,mock(AiCitationValidator.class)).retrieve(query);
        assertThat(noMatch.retrievalMode()).isEqualTo("INTERNAL_RAG_NO_MATCH");
        assertThat(noMatch.hits()).isEmpty();
    }

    @Test
    void marksOnlyIndexUnavailableAsAnOperationalFailure() {
        InternalKnowledgeSearchClient client=mock(InternalKnowledgeSearchClient.class);
        when(client.search(any())).thenAnswer(invocation->{
            AiSearchRequest request=invocation.getArgument(0);
            return new AiSearchResponse("1.0.0",request.requestId(),AiCitationValidator.hash(request.query()),
                    "INDEX_UNAVAILABLE",true,"STORAGE_UNAVAILABLE",List.of());
        });
        RetrievalQuery query=new RetrievalQuery("안심차단",LocalDate.of(2026,8,14),null,
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);

        assertThatThrownBy(()->new InternalRagKnowledgeRetrievalAdapter(
                client,mock(AiCitationValidator.class)).retrieve(query))
                .isInstanceOf(AiRetrievalException.class)
                .hasMessageContaining("INDEX".toLowerCase());
    }

    @Test
    void malformedPolicyAbstainStillFailsClosedWithoutDeterministicBypass() {
        InternalKnowledgeSearchClient client=mock(InternalKnowledgeSearchClient.class);
        AiSearchHit injected=mock(AiSearchHit.class);
        when(client.search(any())).thenReturn(new AiSearchResponse(
                "broken",UUID.randomUUID(),"sha256:broken","POLICY_ABSTAIN",true,
                "STORAGE_UNAVAILABLE",List.of(injected)));
        RetrievalQuery query=new RetrievalQuery("안심차단",LocalDate.of(2026,8,14),null,
                List.of("PROTECTION_STAFF"),List.of("STAFF"),5);

        RetrievalResult result=new InternalRagKnowledgeRetrievalAdapter(
                client,mock(AiCitationValidator.class)).retrieve(query);

        assertThat(result.retrievalMode()).isEqualTo("INTERNAL_RAG_POLICY_ABSTAIN");
        assertThat(result.hits()).isEmpty();
        assertThat(result.fallbackUsed()).isFalse();
    }
}
