package com.alzswell.demo.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoAiAssistanceAuditWriter {

    private final DemoAuditWriter auditWriter;
    private final Clock clock;

    public DemoAiAssistanceAuditWriter(DemoAuditWriter auditWriter, Clock clock) {
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallback(UUID sessionId, UUID demoRunId, String operation, String reasonCode) {
        auditWriter.write(sessionId, demoRunId, "DEMO_AI_ASSISTANCE_FALLBACK_USED", Map.of(
                "operation", operation,
                "reasonCode", reasonCode,
                "fallbackUsed", true,
                "customerInputStored", false,
                "healthInferenceUsed", false,
                "financialActionExecuted", false,
                "externalContactRequested", false
        ), OffsetDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accepted(
            UUID sessionId,
            UUID demoRunId,
            String operation,
            String generatorId,
            boolean modelInvoked
    ) {
        auditWriter.write(sessionId, demoRunId, "DEMO_AI_ASSISTANCE_GENERATED", Map.of(
                "operation", operation,
                "generatorId", generatorId,
                "modelInvoked", modelInvoked,
                "fallbackUsed", false,
                "customerInputStored", false,
                "healthInferenceUsed", false,
                "financialActionExecuted", false,
                "externalContactRequested", false
        ), OffsetDateTime.now(clock));
    }
}
