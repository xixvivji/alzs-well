package com.alzswell.knowledge.application;

import com.alzswell.common.http.InternalAiHttpClientFactory;
import com.alzswell.common.http.InternalAiHttpClientFactory.Transport;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JdkInternalKnowledgeSearchClient implements InternalKnowledgeSearchClient {
    private static final int MAX_RESPONSE_BYTES=262_144;
    private final ObjectMapper objectMapper;
    private final InternalAiHttpClientFactory httpClientFactory;
    private final Optional<Transport> transport;
    private final String token;
    private final Duration requestTimeout;

    public JdkInternalKnowledgeSearchClient(ObjectMapper objectMapper,
            InternalAiHttpClientFactory httpClientFactory,
            @Value("${app.ai-retrieval.enabled:false}") boolean enabled,
            @Value("${app.ai-retrieval.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.ai-retrieval.internal-token:}") String token,
            @Value("${app.ai-retrieval.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${app.ai-retrieval.request-timeout-ms:1500}") long requestTimeoutMs) {
        this.objectMapper=objectMapper;
        this.httpClientFactory=httpClientFactory;
        this.transport=enabled
                ?Optional.of(httpClientFactory.create(baseUrl,connectTimeoutMs))
                :Optional.empty();
        this.token=token;
        this.requestTimeout=positiveDuration(requestTimeoutMs,"request timeout");
    }

    @Override
    public AiHealthResponse health() {
        Transport activeTransport=requiredTransport();
        try {
            URI healthUri=httpClientFactory.endpoint(activeTransport.baseUri(),"/readiness");
            HttpRequest request=HttpRequest.newBuilder(healthUri).timeout(requestTimeout)
                    .header("Accept","application/json").GET().build();
            HttpResponse<InputStream> response=activeTransport.client()
                    .send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream input=response.body()) {
                if(response.statusCode()!=200)
                    throw new AiRetrievalException("AI health returned HTTP "+response.statusCode());
                byte[] responseBody=input.readNBytes(MAX_RESPONSE_BYTES+1);
                if(responseBody.length>MAX_RESPONSE_BYTES)
                    throw new AiRetrievalException("AI health response is too large");
                return objectMapper.readValue(responseBody,AiHealthResponse.class);
            }
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiRetrievalException("AI health request was interrupted",exception);
        } catch(IOException|IllegalArgumentException exception) {
            throw new AiRetrievalException("AI health request failed",exception);
        }
    }

    @Override
    public AiSearchResponse search(AiSearchRequest request) {
        Transport activeTransport=requiredTransport();
        if(token.length()<32) throw new AiRetrievalException("AI retrieval credentials are not configured");
        try {
            byte[] body=objectMapper.writeValueAsBytes(request);
            URI searchUri=httpClientFactory.endpoint(activeTransport.baseUri(),"/internal/v1/search");
            HttpRequest httpRequest=HttpRequest.newBuilder(searchUri).timeout(requestTimeout)
                    .header("Content-Type","application/json")
                    .header("Accept","application/json")
                    .header("X-Internal-Service-Token",token)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<InputStream> response=activeTransport.client()
                    .send(httpRequest,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream input=response.body()) {
                byte[] responseBody=input.readNBytes(MAX_RESPONSE_BYTES+1);
                if(responseBody.length>MAX_RESPONSE_BYTES) throw new AiRetrievalException("AI retrieval response is too large");
                if(response.statusCode()!=200&&response.statusCode()!=503)
                    throw new AiRetrievalException("AI retrieval returned HTTP "+response.statusCode());
                JsonNode payload=objectMapper.readTree(responseBody);
                if(response.statusCode()==200&&"POLICY_ABSTAIN".equals(payload.path("outcome").asText()))
                    return new AiSearchResponse(null,null,null,"POLICY_ABSTAIN",false,
                            "POLICY_GUARDRAIL",java.util.List.of());
                AiSearchResponse parsed=objectMapper.treeToValue(payload,AiSearchResponse.class);
                if(response.statusCode()==503&&!("INDEX_UNAVAILABLE".equals(parsed.outcome())
                        &&parsed.retryable()&&isUnavailableReason(parsed.reasonCode())
                        &&parsed.results()!=null&&parsed.results().isEmpty()))
                    throw new AiRetrievalException("AI retrieval returned an invalid unavailable response");
                if(response.statusCode()==200&&"INDEX_UNAVAILABLE".equals(parsed.outcome()))
                    throw new AiRetrievalException("AI retrieval unavailable response must use HTTP 503");
                return parsed;
            }
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiRetrievalException("AI retrieval was interrupted",exception);
        } catch(IOException|IllegalArgumentException exception) {
            throw new AiRetrievalException("AI retrieval request failed",exception);
        }
    }

    private static Duration positiveDuration(long millis,String name) {
        if(millis<1||millis>30_000) throw new IllegalStateException("Invalid AI retrieval "+name);
        return Duration.ofMillis(millis);
    }

    private Transport requiredTransport() {
        return transport.orElseThrow(()->new AiRetrievalException("AI retrieval is disabled"));
    }

    private static boolean isUnavailableReason(String reasonCode) {
        return "STORAGE_UNAVAILABLE".equals(reasonCode)
                ||"SEARCH_TIMEOUT".equals(reasonCode)
                ||"EMBEDDING_MODEL_UNAVAILABLE".equals(reasonCode)
                ||"EMBEDDING_VECTOR_INVALID".equals(reasonCode);
    }
}
