package com.alzswell.copilot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.alzswell.common.http.InternalAiHttpClientFactory;
import com.alzswell.copilot.application.CopilotPort.CopilotDraft;
import com.alzswell.copilot.application.CopilotPort.CopilotFacts;
import com.alzswell.knowledge.api.KnowledgeResponses.Passage;
import com.alzswell.knowledge.api.KnowledgeResponses.SearchHit;
import com.alzswell.knowledge.application.AiCitationValidator;
import com.alzswell.knowledge.application.DeterministicKnowledgeRetrievalAdapter;
import com.alzswell.knowledge.application.InternalRagKnowledgeRetrievalAdapter;
import com.alzswell.knowledge.application.JdkInternalKnowledgeSearchClient;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort;
import com.alzswell.knowledge.application.KnowledgeRetrievalPort.RetrievalResult;
import com.alzswell.knowledge.application.ResilientKnowledgeRetrievalAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
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
    void policyAbstainCannotBeBypassedByTheDeterministicAnswerTemplate() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        when(retrieval.retrieve(any())).thenReturn(new RetrievalResult(
                List.of(), "INTERNAL_RAG_POLICY_ABSTAIN", false, 0));

        CopilotDraft result = new RetrievalGroundedCopilotAdapter(
                true, retrieval, deterministic, CLOCK).generate(FACTS);

        assertThat(result.generatedBy()).isEqualTo("POLICY_GUARDRAIL");
        assertThat(result.retrievalMode()).isEqualTo("INTERNAL_RAG_POLICY_ABSTAIN");
        assertThat(result.suggestedQuestions()).isEmpty();
        assertThat(result.summary()).contains("초안을 만들 수 없습니다");
    }

    @Test
    void noMatchMayUseOnlyThePreapprovedNonAiTemplate() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        when(retrieval.retrieve(any())).thenReturn(new RetrievalResult(
                List.of(), "INTERNAL_RAG_NO_MATCH", false, 0));

        CopilotDraft result = new RetrievalGroundedCopilotAdapter(
                true, retrieval, deterministic, CLOCK).generate(FACTS);

        assertThat(result.generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(result.modelInvoked()).isFalse();
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void httpPolicyAbstainFlowsThroughSpringRetrievalToCopilotGuardrail() throws Exception {
        HttpCopilotResult result = generateThroughHttp("POLICY_ABSTAIN", "POLICY_GUARDRAIL");

        assertThat(result.draft().generatedBy()).isEqualTo("POLICY_GUARDRAIL");
        assertThat(result.draft().retrievalMode()).isEqualTo("INTERNAL_RAG_POLICY_ABSTAIN");
        assertThat(result.draft().suggestedQuestions()).isEmpty();
        assertThat(result.draft().citations()).isEmpty();
        assertThat(result.draft().modelInvoked()).isFalse();
        assertThat(result.draft().externalEgressAttempted()).isFalse();
        verifyNoInteractions(result.deterministicRetrieval());
    }

    @Test
    void httpNoMatchUsesOnlyPreapprovedNonAiTemplateWithoutCitations() throws Exception {
        HttpCopilotResult result = generateThroughHttp("NO_MATCH", "NO_RELEVANT_MATCH");

        assertThat(result.draft().generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(result.draft().fallbackUsed()).isTrue();
        assertThat(result.draft().modelInvoked()).isFalse();
        assertThat(result.draft().externalEgressAttempted()).isFalse();
        assertThat(result.draft().citations()).isEmpty();
        verifyNoInteractions(result.deterministicRetrieval());
    }

    @Test
    void doesNotLabelLocalKeywordEvidenceAsInternalRag() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        when(retrieval.retrieve(any())).thenReturn(new RetrievalResult(List.of(), "DETERMINISTIC_KEYWORD", false, 0));

        CopilotDraft result = new RetrievalGroundedCopilotAdapter(true, retrieval, deterministic, CLOCK).generate(FACTS);

        assertThat(result.generatedBy()).isEqualTo("DETERMINISTIC_TEMPLATE");
        assertThat(result.retrievalMode()).isEqualTo("NONE");
    }

    @Test
    void mapsTheDemoRecurringReasonCodeToTheApprovedSearchTerm() {
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        when(retrieval.retrieve(any())).thenReturn(new RetrievalResult(
                List.of(), "INTERNAL_RAG_HYBRID", false, 0));
        CopilotFacts demoFacts = new CopilotFacts("CONSULTATION_NOTE", "UNABLE_TO_CONFIRM",
                List.of("MISSED_RECURRING"), List.of());

        new RetrievalGroundedCopilotAdapter(true, retrieval, deterministic, CLOCK).generate(demoFacts);

        verify(retrieval).retrieve(argThat(query ->
                query.query().equals("정기납부 미처리 고객 상담 안내")));
    }

    private HttpCopilotResult generateThroughHttp(String outcome, String reasonCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/search", exchange -> {
            try {
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                ObjectNode response = mapper.createObjectNode();
                response.put("contractVersion", "1.0.0");
                response.put("requestId", request.path("requestId").asText());
                response.put("queryHash", sha256(request.path("query").asText()));
                response.put("outcome", outcome);
                response.put("retryable", false);
                response.put("reasonCode", reasonCode);
                response.putArray("results");
                byte[] body = mapper.writeValueAsBytes(response);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            InternalAiHttpClientFactory httpClientFactory = new InternalAiHttpClientFactory(
                    false, "", "", "PKCS12", "", "", "PKCS12");
            JdkInternalKnowledgeSearchClient client = new JdkInternalKnowledgeSearchClient(
                    mapper, httpClientFactory, true, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "synthetic-internal-service-token-0001", 500, 1_000);
            InternalRagKnowledgeRetrievalAdapter internal = new InternalRagKnowledgeRetrievalAdapter(
                    client, mock(AiCitationValidator.class));
            DeterministicKnowledgeRetrievalAdapter deterministicRetrieval =
                    mock(DeterministicKnowledgeRetrievalAdapter.class);
            ResilientKnowledgeRetrievalAdapter retrieval = new ResilientKnowledgeRetrievalAdapter(
                    true, internal, deterministicRetrieval);
            CopilotDraft draft = new RetrievalGroundedCopilotAdapter(
                    true, retrieval, deterministic, CLOCK).generate(FACTS);
            return new HttpCopilotResult(draft, deterministicRetrieval);
        } finally {
            server.stop(0);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record HttpCopilotResult(
            CopilotDraft draft,
            DeterministicKnowledgeRetrievalAdapter deterministicRetrieval
    ) {}
}
