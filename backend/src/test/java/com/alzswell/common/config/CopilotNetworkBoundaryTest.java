package com.alzswell.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CopilotNetworkBoundaryTest {
    @Test
    void requiresExplicitModeAndApprovedDocumentsWithoutWeakeningDefault() {
        assertThat(CopilotNetworkBoundary.valid("AIR_GAPPED_DEMO", false, "")).isTrue();
        assertThat(CopilotNetworkBoundary.valid("AIR_GAPPED_DEMO", true, "DOC")).isFalse();
        assertThat(CopilotNetworkBoundary.valid("CONTROLLED_BEDROCK_SYNTHETIC", true, "DOC")).isTrue();
        assertThat(CopilotNetworkBoundary.valid("CONTROLLED_BEDROCK_SYNTHETIC", false, "DOC")).isFalse();
        assertThat(CopilotNetworkBoundary.valid("CONTROLLED_BEDROCK_SYNTHETIC", true, "")).isFalse();
        assertThat(CopilotNetworkBoundary.valid("CONTROLLED_BEDROCK_SYNTHETIC", true, null)).isFalse();
        assertThat(CopilotNetworkBoundary.valid("PUBLIC", true, "DOC")).isFalse();
    }
}
