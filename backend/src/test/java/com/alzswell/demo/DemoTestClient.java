package com.alzswell.demo;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alzswell.common.security.DemoCapabilityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

final class DemoTestClient {

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
        return new Session(
                UUID.fromString(body.at("/data/sessionId").asText()),
                body.at("/data/scenarioSeed").asText(),
                result.getResponse().getHeader(DemoCapabilityService.CUSTOMER_RESPONSE_HEADER),
                result.getResponse().getHeader(DemoCapabilityService.STAFF_RESPONSE_HEADER),
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
