package com.alzswell.copilot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.alzswell.assistance.application.InternalFinancialAiClient;
import com.alzswell.assistance.application.InternalFinancialAiClient.StaffDraftResponse;
import com.alzswell.assistance.application.InternalFinancialAiClient.StaffDraftText;
import com.alzswell.copilot.application.CopilotPort.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OptionalStaffDraftGeneratorTest {
    private final InternalFinancialAiClient client = mock(InternalFinancialAiClient.class);
    private final CopilotFacts facts = new CopilotFacts("CONSULTATION_NOTE", "UNSURE", List.of("DUPLICATE_TRANSFER"), List.of(), true);
    private final CopilotDraft template = new CopilotDraft("기본 안내", List.of("확인 질문"), List.of("확인 사항"),
            List.of("DUPLICATE_TRANSFER"), "RAG_GROUNDED_TEMPLATE", false, false, false, "INTERNAL_RAG_HYBRID",
            List.of(new CopilotCitation("DOC", "1", UUID.randomUUID(), "근거", "https://example.com", "INTERNAL_RAG_HYBRID")));

    @Test
    void requiresEnablementSyntheticDataAndDocumentEgressApproval() {
        assertThat(new OptionalStaffDraftGenerator(false, client, "DOC").generate(facts, List.of("근거"), template)).isSameAs(template);
        assertThat(new OptionalStaffDraftGenerator(true, client, "").generate(facts, List.of("근거"), template)).isSameAs(template);
        var real = new CopilotFacts("CONSULTATION_NOTE", "UNSURE", List.of(), List.of());
        assertThat(new OptionalStaffDraftGenerator(true, client, "DOC").generate(real, List.of("근거"), template)).isSameAs(template);
        verifyNoInteractions(client);
    }

    @Test
    void acceptsBoundedDraftButRetainsServerCitationsAndReasonCodes() {
        when(client.staffDraft(any())).thenReturn(new StaffDraftResponse(new StaffDraftText("확인 필요", List.of("기억하시나요?"), List.of("원문 확인")),
                "BEDROCK_GENERATIVE_DRAFT", true, true, false));
        var result = new OptionalStaffDraftGenerator(true, client, " DOC ").generate(facts, List.of("근거"), template);
        assertThat(result.generatedBy()).isEqualTo("BEDROCK_GENERATIVE_DRAFT");
        assertThat(result.citations()).isEqualTo(template.citations());
        assertThat(result.basisReasonCodes()).isEqualTo(facts.reasonCodes());
        assertThat(result.fallbackUsed()).isFalse();
        verify(client).staffDraft(new InternalFinancialAiClient.StaffDraftRequest(true, facts.reasonCodes(), "UNSURE", List.of("근거")));
    }

    @Test
    void rejectsUnsafeAndIncompleteOutput() {
        var generator = new OptionalStaffDraftGenerator(true, client, "DOC");
        for (String text : List.of("", "<script>", "HTTPS://bad.example", "x".repeat(601))) {
            when(client.staffDraft(any())).thenReturn(new StaffDraftResponse(new StaffDraftText(text, List.of("질문"), List.of("확인")),
                    "BEDROCK_GENERATIVE_DRAFT", true, true, false));
            assertThat(generator.generate(facts, List.of("근거"), template).fallbackUsed()).isTrue();
        }
        when(client.staffDraft(any())).thenReturn(new StaffDraftResponse(null, "DETERMINISTIC_TEMPLATE", false, false, true));
        assertThat(generator.generate(facts, List.of("근거"), template).summary()).isEqualTo(template.summary());
        when(client.staffDraft(any())).thenThrow(new IllegalStateException("private error"));
        assertThat(generator.generate(facts, List.of("근거"), template).fallbackUsed()).isTrue();
    }
}
