package com.alzswell.knowledge.application;

import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JdkInternalKnowledgeSearchClient implements InternalKnowledgeSearchClient {
    private static final int MAX_RESPONSE_BYTES=262_144;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI searchUri;
    private final String token;
    private final Duration requestTimeout;

    public JdkInternalKnowledgeSearchClient(ObjectMapper objectMapper,
            @Value("${app.ai-retrieval.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.ai-retrieval.internal-token:}") String token,
            @Value("${app.ai-retrieval.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${app.ai-retrieval.request-timeout-ms:1500}") long requestTimeoutMs) {
        this.objectMapper=objectMapper;
        this.searchUri=searchUri(baseUrl);
        this.token=token;
        this.requestTimeout=positiveDuration(requestTimeoutMs,"request timeout");
        this.httpClient=HttpClient.newBuilder().connectTimeout(positiveDuration(connectTimeoutMs,"connect timeout"))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public AiSearchResponse search(AiSearchRequest request) {
        if(token.length()<32) throw new AiRetrievalException("AI retrieval credentials are not configured");
        try {
            byte[] body=objectMapper.writeValueAsBytes(request);
            HttpRequest httpRequest=HttpRequest.newBuilder(searchUri).timeout(requestTimeout)
                    .header("Content-Type","application/json")
                    .header("Accept","application/json")
                    .header("X-Internal-Service-Token",token)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<InputStream> response=httpClient.send(httpRequest,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream input=response.body()) {
                if(response.statusCode()!=200) throw new AiRetrievalException("AI retrieval returned an unexpected status");
                byte[] responseBody=input.readNBytes(MAX_RESPONSE_BYTES+1);
                if(responseBody.length>MAX_RESPONSE_BYTES) throw new AiRetrievalException("AI retrieval response is too large");
                return objectMapper.readValue(responseBody,AiSearchResponse.class);
            }
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiRetrievalException("AI retrieval was interrupted",exception);
        } catch(IOException|IllegalArgumentException exception) {
            throw new AiRetrievalException("AI retrieval request failed",exception);
        }
    }

    private static URI searchUri(String baseUrl) {
        try {
            URI base=URI.create(baseUrl);
            if(!("http".equals(base.getScheme())||"https".equals(base.getScheme()))||base.getHost()==null
                    ||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null)
                throw new IllegalArgumentException("invalid AI retrieval base URL");
            String prefix=base.toString().replaceAll("/+$","");
            return URI.create(prefix+"/internal/v1/search");
        } catch(IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid AI retrieval base URL",exception);
        }
    }

    private static Duration positiveDuration(long millis,String name) {
        if(millis<1||millis>30_000) throw new IllegalStateException("Invalid AI retrieval "+name);
        return Duration.ofMillis(millis);
    }
}
