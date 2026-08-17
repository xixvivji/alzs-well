package com.alzswell.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공개 합성데모가 실제 데이터·외부 실행·외부 모델을 허용한 채 기동되는 것을 막는다.
 */
@Component
public class DemoGuardrailStartupValidator {

    private final boolean syntheticDataOnly;
    private final boolean externalActionsEnabled;
    private final String networkMode;
    private final boolean externalEgressEnabled;
    private final boolean remoteModelEnabled;
    private final boolean syntheticProviderOnly;
    private final boolean customerProfileApiEnabled;
    private final boolean publicExposure;

    public DemoGuardrailStartupValidator(
            @Value("${app.guardrails.synthetic-data-only:true}") boolean syntheticDataOnly,
            @Value("${app.guardrails.external-actions-enabled:false}") boolean externalActionsEnabled,
            @Value("${app.guardrails.network-mode:AIR_GAPPED_DEMO}") String networkMode,
            @Value("${app.guardrails.external-egress-enabled:false}") boolean externalEgressEnabled,
            @Value("${app.guardrails.remote-model-enabled:false}") boolean remoteModelEnabled,
            @Value("${app.guardrails.synthetic-provider-only:true}") boolean syntheticProviderOnly,
            @Value("${app.features.customer-profile-api-enabled:false}") boolean customerProfileApiEnabled,
            @Value("${app.deployment.public-exposure:false}") boolean publicExposure
    ) {
        this.syntheticDataOnly = syntheticDataOnly;
        this.externalActionsEnabled = externalActionsEnabled;
        this.networkMode = networkMode;
        this.externalEgressEnabled = externalEgressEnabled;
        this.remoteModelEnabled = remoteModelEnabled;
        this.syntheticProviderOnly = syntheticProviderOnly;
        this.customerProfileApiEnabled = customerProfileApiEnabled;
        this.publicExposure = publicExposure;
    }

    @PostConstruct
    void validatePublicDemoBoundary() {
        boolean safe = syntheticDataOnly
                && !externalActionsEnabled
                && "AIR_GAPPED_DEMO".equals(networkMode)
                && !externalEgressEnabled
                && !remoteModelEnabled
                && syntheticProviderOnly
                && (!customerProfileApiEnabled || !publicExposure);
        if (!safe) {
            throw new IllegalStateException(
                    "공개 합성데모 안전 가드레일이 해제되어 애플리케이션 기동을 중단합니다."
            );
        }
    }
}
