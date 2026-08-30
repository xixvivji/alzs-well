package com.alzswell.system.application;

import com.alzswell.knowledge.application.AiRetrievalException;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.AiHealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiDeploymentReadiness {
    private final InternalKnowledgeSearchClient client;
    private final boolean enabled;
    private final boolean strict;
    private final String expectedStatus;
    private final String expectedRevision;
    private final String expectedArtifact;
    private final String expectedGoldenSet;
    private final String expectedIndex;
    private final String expectedEnvironment;

    public AiDeploymentReadiness(InternalKnowledgeSearchClient client,
            @Value("${app.ai-retrieval.enabled:false}") boolean enabled,
            @Value("${app.ai-retrieval.strict-readiness:false}") boolean strict,
            @Value("${app.ai-retrieval.expected-model-status:}") String expectedStatus,
            @Value("${app.ai-retrieval.expected-model-revision:}") String expectedRevision,
            @Value("${app.ai-retrieval.expected-artifact-sha256:}") String expectedArtifact,
            @Value("${app.ai-retrieval.expected-golden-set-sha256:}") String expectedGoldenSet,
            @Value("${app.ai-retrieval.expected-index-version:}") String expectedIndex,
            @Value("${app.ai-retrieval.expected-deployment-environment:}") String expectedEnvironment) {
        this.client=client;
        this.enabled=enabled;
        this.strict=strict;
        this.expectedStatus=expectedStatus;
        this.expectedRevision=expectedRevision;
        this.expectedArtifact=expectedArtifact;
        this.expectedGoldenSet=expectedGoldenSet;
        this.expectedIndex=expectedIndex;
        this.expectedEnvironment=expectedEnvironment;
    }

    public Result verify() {
        if(!enabled) return new Result("DISABLED",true);
        if(!strict) return new Result("OPTIONAL",true);
        try {
            AiHealthResponse health=client.health();
            boolean valid="UP".equals(health.status())
                    && matches(expectedStatus,health.modelStatus())
                    && matches(expectedRevision,health.modelRevision())
                    && matches(expectedArtifact,health.artifactSha256())
                    && matches(expectedGoldenSet,health.goldenSetSha256())
                    && matches(expectedIndex,health.indexVersion())
                    && matches(expectedEnvironment,health.deploymentEnvironment())
                    && (!"STAGED_APPROVED".equals(expectedStatus)
                        || (health.stagedApprovalEnabled() && health.arcticRolloutEnabled()
                            && !health.embeddingFallbackUsed()));
            return new Result(valid ? "UP" : "MISMATCH",valid);
        } catch(AiRetrievalException exception) {
            return new Result("DOWN",false);
        }
    }

    private static boolean matches(String expected,String actual) {
        return expected.isBlank() || expected.equals(actual);
    }

    public record Result(String status,boolean ready) {}
}
