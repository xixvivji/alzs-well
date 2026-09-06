package com.alzswell.common.config;

/** Generic external providers stay disabled; only the explicit synthetic copilot exception is allowed. */
public final class CopilotNetworkBoundary {
    private CopilotNetworkBoundary() {}

    public static boolean valid(String mode, boolean generationEnabled, String approvedDocuments) {
        return ("AIR_GAPPED_DEMO".equals(mode) && !generationEnabled)
                || ("CONTROLLED_BEDROCK_SYNTHETIC".equals(mode) && generationEnabled
                    && approvedDocuments != null && !approvedDocuments.isBlank());
    }
}
