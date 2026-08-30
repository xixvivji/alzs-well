package com.alzswell.assistance.application;

import static org.assertj.core.api.Assertions.*;

import com.alzswell.assistance.application.InternalFinancialAiClient.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JdkInternalFinancialAiClientTest {
    private HttpServer server;
    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void sendsAuthenticatedRequestsToAllAssistanceEndpoints() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        UUID requestId = UUID.randomUUID();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/intent-structure", exchange -> respond(exchange, token,
                "{\"contractVersion\":\"1.0.0\",\"requestId\":\"" + requestId + "\","
                        + "\"suggestion\":{\"paymentContinuity\":\"KEEP_ESSENTIAL_PAYMENTS\","
                        + "\"explanationMode\":\"SIMPLE_TEXT\",\"helpCondition\":\"ON_CUSTOMER_REQUEST\",\"shareScopes\":[]},"
                        + "\"summary\":\"요약\",\"evidence\":[],\"needsClarification\":false,\"clarifyingQuestions\":[],"
                        + "\"generatedBy\":\"test\",\"modelInvoked\":false,\"fallbackUsed\":true,"
                        + "\"healthInferenceUsed\":false,\"financialActionExecuted\":false}"));
        server.createContext("/internal/v1/change-analysis", exchange -> respond(exchange, token,
                "{\"contractVersion\":\"1.0.0\",\"requestId\":\"" + requestId + "\",\"baselineDays\":60,"
                        + "\"recentDays\":30,\"changes\":[],\"diagnosisInferred\":false,\"financialActionExecuted\":false}"));
        server.createContext("/internal/v1/plain-language", exchange -> respond(exchange, token,
                "{\"contractVersion\":\"1.0.0\",\"requestId\":\"" + requestId + "\",\"title\":\"확인\","
                        + "\"text\":\"쉬운 설명\",\"speechText\":\"쉬운 설명\",\"generationMode\":\"CONSTRAINED_NLG_V1\","
                        + "\"modelInvoked\":false,\"fallbackUsed\":false,\"diagnosisInferred\":false,"
                        + "\"financialActionExecuted\":false}"));
        server.start();
        String secret = "12345678901234567890123456789012";
        JdkInternalFinancialAiClient client = client(secret);

        assertThat(client.structureIntent(new IntentStructureRequest("1.0.0", requestId, "공과금 유지"))
                .suggestion().paymentContinuity()).isEqualTo("KEEP_ESSENTIAL_PAYMENTS");
        assertThat(client.analyzeChanges(new ChangeAnalysisRequest("1.0.0", requestId, 60, 30, List.of()))
                .changes()).isEmpty();
        assertThat(client.plainLanguage(new PlainLanguageRequest("1.0.0", requestId, "SIMPLE_TEXT",
                new PlainLanguageFact("REPEATED_CONFIRMATION_COUNT", 2, 8, 30, "COUNT"))).text())
                .isEqualTo("쉬운 설명");
        assertThat(token).hasValue(secret);
    }

    @Test
    void rejectsMissingCredentialsAndUnexpectedResponse() throws Exception {
        JdkInternalFinancialAiClient missing = new JdkInternalFinancialAiClient(
                new ObjectMapper(), "http://127.0.0.1:1", "", 100, 100);
        assertThatThrownBy(() -> missing.structureIntent(
                new IntentStructureRequest("1.0.0", UUID.randomUUID(), "공과금 유지")))
                .isInstanceOf(AiAssistanceException.class);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/intent-structure", exchange -> {
            byte[] body = "private diagnostic".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        assertThatThrownBy(() -> client("12345678901234567890123456789012").structureIntent(
                new IntentStructureRequest("1.0.0", UUID.randomUUID(), "공과금 유지")))
                .isInstanceOf(AiAssistanceException.class).hasMessageNotContaining("private diagnostic");
    }

    @Test
    void validatesConfiguration() {
        assertThatThrownBy(() -> new JdkInternalFinancialAiClient(
                new ObjectMapper(), "ftp://example.com", "12345678901234567890123456789012", 500, 1000))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JdkInternalFinancialAiClient(
                new ObjectMapper(), "http://127.0.0.1:8000", "12345678901234567890123456789012", 0, 1000))
                .isInstanceOf(IllegalStateException.class);
    }

    private JdkInternalFinancialAiClient client(String token) {
        return new JdkInternalFinancialAiClient(new ObjectMapper().findAndRegisterModules(),
                "http://127.0.0.1:" + server.getAddress().getPort(), token, 500, 1000);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
                                AtomicReference<String> token, String json) throws java.io.IOException {
        token.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
        exchange.getRequestBody().readAllBytes();
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body); exchange.close();
    }
}
