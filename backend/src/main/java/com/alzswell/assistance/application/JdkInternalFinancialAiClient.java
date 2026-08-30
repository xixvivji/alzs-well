package com.alzswell.assistance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JdkInternalFinancialAiClient implements InternalFinancialAiClient {
    private static final int MAX_RESPONSE_BYTES = 131_072;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI intentUri;
    private final URI changeUri;
    private final URI plainLanguageUri;
    private final String token;
    private final Duration requestTimeout;

    public JdkInternalFinancialAiClient(
            ObjectMapper objectMapper,
            @Value("${app.ai-retrieval.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.ai-retrieval.internal-token:}") String token,
            @Value("${app.ai-retrieval.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${app.ai-retrieval.request-timeout-ms:1500}") long requestTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.intentUri = endpointUri(baseUrl, "/internal/v1/intent-structure");
        this.changeUri = endpointUri(baseUrl, "/internal/v1/change-analysis");
        this.plainLanguageUri = endpointUri(baseUrl, "/internal/v1/plain-language");
        this.token = token;
        this.requestTimeout = positiveDuration(requestTimeoutMs, "request timeout");
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(positiveDuration(connectTimeoutMs, "connect timeout"))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public IntentStructureResponse structureIntent(IntentStructureRequest request) {
        return post(intentUri, request, IntentStructureResponse.class);
    }

    @Override
    public ChangeAnalysisResponse analyzeChanges(ChangeAnalysisRequest request) {
        return post(changeUri, request, ChangeAnalysisResponse.class);
    }

    @Override
    public PlainLanguageResponse plainLanguage(PlainLanguageRequest request) {
        return post(plainLanguageUri, request, PlainLanguageResponse.class);
    }

    private <T> T post(URI uri, Object body, Class<T> responseType) {
        if (token.length() < 32) throw new AiAssistanceException("AI assistance credentials are not configured");
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(body);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Internal-Service-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new AiAssistanceException("AI assistance returned HTTP " + response.statusCode());
                }
                byte[] responseBody = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (responseBody.length > MAX_RESPONSE_BYTES) {
                    throw new AiAssistanceException("AI assistance response is too large");
                }
                return objectMapper.readValue(responseBody, responseType);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiAssistanceException("AI assistance request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AiAssistanceException("AI assistance request failed", exception);
        }
    }

    private static URI endpointUri(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl);
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException("invalid AI assistance base URL");
            }
            return URI.create(base.toString().replaceAll("/+$", "") + path);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid AI assistance base URL", exception);
        }
    }

    private static Duration positiveDuration(long millis, String name) {
        if (millis < 1 || millis > 30_000) throw new IllegalStateException("Invalid AI assistance " + name);
        return Duration.ofMillis(millis);
    }
}
