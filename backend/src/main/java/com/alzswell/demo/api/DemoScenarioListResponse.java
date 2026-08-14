package com.alzswell.demo.api;

import java.util.List;

public record DemoScenarioListResponse(List<DemoScenarioItem> items) {
    public DemoScenarioListResponse {
        items = List.copyOf(items);
    }

    public record DemoScenarioItem(
            String scenarioId,
            String title,
            int baselineMonths,
            int observationMonths,
            List<String> supportedContextFixtures,
            boolean syntheticData
    ) {
        public DemoScenarioItem {
            supportedContextFixtures = List.copyOf(supportedContextFixtures);
        }
    }
}
