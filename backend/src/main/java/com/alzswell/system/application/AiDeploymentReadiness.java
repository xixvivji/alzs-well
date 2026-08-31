package com.alzswell.system.application;

import com.alzswell.knowledge.application.InternalKnowledgeSearchClient;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.AiHealthResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiDeploymentReadiness {
    private static final List<String> REQUIRED_CHECKS=List.of(
            "database","retrievalAuditPrivileges","approvedEmbedding",
            "vectorIndex","searchProbe","assistanceContracts");
    private final InternalKnowledgeSearchClient client;
    private final boolean enabled;
    private final boolean strict;
    private final String expectedStatus;
    private final String expectedEmbeddingBackend;
    private final int expectedEmbeddingDimensions;
    private final String expectedRevision;
    private final String expectedArtifact;
    private final String expectedGoldenSet;
    private final String expectedIndex;
    private final String expectedEnvironment;

    public AiDeploymentReadiness(InternalKnowledgeSearchClient client,
            @Value("${app.ai-retrieval.enabled:false}") boolean enabled,
            @Value("${app.ai-retrieval.strict-readiness:false}") boolean strict,
            @Value("${app.ai-retrieval.expected-model-status:}") String expectedStatus,
            @Value("${app.ai-retrieval.expected-embedding-backend:}") String expectedEmbeddingBackend,
            @Value("${app.ai-retrieval.expected-embedding-dimensions:0}") int expectedEmbeddingDimensions,
            @Value("${app.ai-retrieval.expected-model-revision:}") String expectedRevision,
            @Value("${app.ai-retrieval.expected-artifact-sha256:}") String expectedArtifact,
            @Value("${app.ai-retrieval.expected-golden-set-sha256:}") String expectedGoldenSet,
            @Value("${app.ai-retrieval.expected-index-version:}") String expectedIndex,
            @Value("${app.ai-retrieval.expected-deployment-environment:}") String expectedEnvironment) {
        this.client=client;
        this.enabled=enabled;
        this.strict=strict;
        this.expectedStatus=expectedStatus;
        this.expectedEmbeddingBackend=expectedEmbeddingBackend;
        this.expectedEmbeddingDimensions=expectedEmbeddingDimensions;
        this.expectedRevision=expectedRevision;
        this.expectedArtifact=expectedArtifact;
        this.expectedGoldenSet=expectedGoldenSet;
        this.expectedIndex=expectedIndex;
        this.expectedEnvironment=expectedEnvironment;
    }

    public Result verify() {
        if(!enabled) return new Result("DISABLED",false);
        if(!strict) return new Result("OPTIONAL",true);
        try {
            AiHealthResponse health=client.health();
            boolean configurationComplete=!expectedStatus.isBlank()
                    &&!expectedEmbeddingBackend.isBlank()&&expectedEmbeddingDimensions>0
                    &&!expectedRevision.isBlank()&&!expectedArtifact.isBlank()
                    &&!expectedGoldenSet.isBlank()&&!expectedIndex.isBlank()
                    &&!expectedEnvironment.isBlank();
            boolean valid=health!=null&&configurationComplete
                    &&"READY".equals(health.status())&&"ai-rag".equals(health.service())
                    && health.checks()!=null
                    && REQUIRED_CHECKS.stream().allMatch(check->"UP".equals(health.checks().get(check)))
                    &&expectedEmbeddingBackend.equals(health.embeddingConfiguredBackend())
                    &&expectedEmbeddingBackend.equals(health.embeddingBackend())
                    &&expectedEmbeddingDimensions==health.embeddingDimensions()
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
        } catch(RuntimeException exception) {
            return new Result("DOWN",false);
        }
    }

    private static boolean matches(String expected,String actual) {
        return expected.isBlank() || expected.equals(actual);
    }

    public record Result(String status,boolean ready) {}
}
