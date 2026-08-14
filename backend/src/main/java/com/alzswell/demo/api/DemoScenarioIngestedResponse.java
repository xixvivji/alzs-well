package com.alzswell.demo.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DemoScenarioIngestedResponse(
        String scenarioId,
        UUID demoRunId,
        String customerId,
        String alertId,
        String caseId,
        String scenarioSeed,
        String snapshotHash,
        DatePeriod baselinePeriod,
        DatePeriod observationPeriod,
        List<String> reasonCodes,
        String preDecision,
        String state,
        String algorithmVersion,
        String policyVersion,
        CommandMetadata command
) {
    public DemoScenarioIngestedResponse {
        reasonCodes = List.copyOf(reasonCodes);
    }

    public record DatePeriod(LocalDate from, LocalDate to) {
    }
}
