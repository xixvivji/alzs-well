package com.alzswell.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alzswell.knowledge.application.AiRetrievalException;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient;
import com.alzswell.knowledge.application.InternalKnowledgeSearchClient.AiHealthResponse;
import org.junit.jupiter.api.Test;

class AiDeploymentReadinessTest {
    private final InternalKnowledgeSearchClient client=mock(InternalKnowledgeSearchClient.class);

    @Test void strictStagingAcceptsOnlyMatchingApprovedRuntime() {
        when(client.health()).thenReturn(health("STAGED_APPROVED","revision","artifact","golden","index","AWS_STAGING",true,false));
        var result=readiness(true).verify();
        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.ready()).isTrue();
    }

    @Test void strictStagingRejectsHashFallbackOrMetadataMismatch() {
        when(client.health()).thenReturn(health("STAGED_APPROVED","revision","artifact","golden","wrong","AWS_STAGING",true,true));
        var result=readiness(true).verify();
        assertThat(result.status()).isEqualTo("MISMATCH");
        assertThat(result.ready()).isFalse();
    }

    @Test void optionalAiDoesNotProbeBeforeTheSidecarIsReady() {
        var result=readiness(false).verify();
        assertThat(result.status()).isEqualTo("OPTIONAL");
        assertThat(result.ready()).isTrue();
    }

    @Test void strictAiFailureTakesTheStagingTargetOutOfReadiness() {
        when(client.health()).thenThrow(new AiRetrievalException("down"));
        var result=readiness(true).verify();
        assertThat(result.status()).isEqualTo("DOWN");
        assertThat(result.ready()).isFalse();
    }

    private AiDeploymentReadiness readiness(boolean strict) {
        return new AiDeploymentReadiness(client,true,strict,"STAGED_APPROVED","revision","artifact","golden","index","AWS_STAGING");
    }

    private static AiHealthResponse health(String status,String revision,String artifact,String golden,
            String index,String environment,boolean approved,boolean fallback) {
        return new AiHealthResponse("UP","ai-rag","local-arctic-ko","local-arctic-ko","arctic",1024,
                status,revision,artifact,golden,index,true,environment,approved,fallback);
    }
}
