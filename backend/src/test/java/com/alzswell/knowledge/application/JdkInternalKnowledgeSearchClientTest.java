package com.alzswell.knowledge.application;

import static org.assertj.core.api.Assertions.*;

import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class JdkInternalKnowledgeSearchClientTest {
    private HttpServer server;
    @AfterEach void stop(){if(server!=null) server.stop(0);}

    @Test
    void sendsAuthenticatedContractAndParsesResponse() throws Exception {
        AtomicReference<String> token=new AtomicReference<>();
        AtomicReference<String> protocol=new AtomicReference<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        UUID requestId=UUID.randomUUID();
        server.createContext("/internal/v1/search",exchange->{
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            protocol.set(exchange.getProtocol());
            exchange.getRequestBody().readAllBytes();
            byte[] response=("{\"contractVersion\":\"1.0.0\",\"requestId\":\""+requestId
                    +"\",\"queryHash\":\"sha256:abc\",\"results\":[]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        String secret="12345678901234567890123456789012";
        JdkInternalKnowledgeSearchClient client=new JdkInternalKnowledgeSearchClient(
                new ObjectMapper().findAndRegisterModules(),"http://127.0.0.1:"+server.getAddress().getPort(),secret,500,1000);
        AiSearchResponse response=client.search(new AiSearchRequest("1.0.0",requestId,"안심차단",
                List.of("KNOWLEDGE_SEARCH"),List.of("PROTECTION_STAFF"),List.of("STAFF"),
                LocalDate.of(2026,8,14),5));
        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(token).hasValue(secret);
        assertThat(protocol).hasValue("HTTP/1.1");
    }

    @Test
    void doesNotExposeErrorBodyWhenStatusIsUnexpected() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/search",exchange->{
            byte[] response="secret diagnostic".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        JdkInternalKnowledgeSearchClient client=new JdkInternalKnowledgeSearchClient(
                new ObjectMapper().findAndRegisterModules(),"http://127.0.0.1:"+server.getAddress().getPort(),
                "12345678901234567890123456789012",500,1000);
        AiSearchRequest request=new AiSearchRequest("1.0.0",UUID.randomUUID(),"안심차단",List.of("KNOWLEDGE_SEARCH"),
                List.of("PROTECTION_STAFF"),List.of("STAFF"),LocalDate.of(2026,8,14),5);
        assertThatThrownBy(()->client.search(request)).isInstanceOf(AiRetrievalException.class)
                .hasMessageNotContaining("secret diagnostic");
    }
}
