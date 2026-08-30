package com.alzswell.demo;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.alzswell.common.security.DemoCapabilityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

final class DemoTestClient {

    static final String STAFF_BOOTSTRAP_TOKEN =
            "test-bootstrap-token-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    DemoTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    Session create() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/demo/sessions"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID sessionId = UUID.fromString(body.at("/data/sessionId").asText());
        MvcResult staffResult = mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/v1/demo/staff/sessions/{sessionId}/capability", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + STAFF_BOOTSTRAP_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(
                sessionId,
                body.at("/data/scenarioSeed").asText(),
                result.getResponse().getHeader(DemoCapabilityService.CUSTOMER_RESPONSE_HEADER),
                staffResult.getResponse().getHeader(DemoCapabilityService.STAFF_RESPONSE_HEADER),
                null
        );
    }

    Session ingest(Session session, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(customer(
                        MockMvcRequestBuilders.post(
                                "/api/v1/demo/sessions/{sessionId}/scenarios/FIN_MGMT_AB_001/ingest",
                                session.sessionId()
                        ).header("Idempotency-Key", idempotencyKey),
                        session,
                        false
                ))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID runId = UUID.fromString(body.at("/data/demoRunId").asText());
        return session.withRun(runId);
    }

    MockHttpServletRequestBuilder customer(MockHttpServletRequestBuilder request, Session session) {
        return customer(request, session, true);
    }

    MockHttpServletRequestBuilder customer(
            MockHttpServletRequestBuilder request,
            Session session,
            boolean includeRun
    ) {
        request.header(DemoCapabilityService.REQUEST_HEADER, session.customerCapability());
        if (includeRun && session.demoRunId() != null) {
            request.header(DemoCapabilityService.RUN_HEADER, session.demoRunId());
        }
        return request;
    }

    MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request, Session session) {
        request.header(DemoCapabilityService.REQUEST_HEADER, session.staffCapability());
        if (session.demoRunId() != null) {
            request.header(DemoCapabilityService.RUN_HEADER, session.demoRunId());
        }
        return request;
    }

    JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    record Session(
            UUID sessionId,
            String scenarioSeed,
            String customerCapability,
            String staffCapability,
            UUID demoRunId
    ) {
        Session withRun(UUID runId) {
            return new Session(sessionId, scenarioSeed, customerCapability, staffCapability, runId);
        }
    }
}
