package com.alzswell.common.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PublicDeploymentStartupValidator {

    private final boolean publicExposure;
    private final boolean aiHttpsRequired;
    private final Environment environment;

    public PublicDeploymentStartupValidator(
            @Value("${app.deployment.public-exposure:false}") boolean publicExposure,
            @Value("${app.ai-retrieval.tls.require-https:false}") boolean aiHttpsRequired,
            Environment environment
    ) {
        this.publicExposure = publicExposure;
        this.aiHttpsRequired = aiHttpsRequired;
        this.environment = environment;
    }

    @PostConstruct
    void validateProfile() {
        if (publicExposure && Arrays.stream(environment.getActiveProfiles())
                .noneMatch("production"::equals)) {
            throw new IllegalStateException("외부 공개 배포는 production 프로필로만 기동할 수 있습니다.");
        }
        if (publicExposure && !aiHttpsRequired) {
            throw new IllegalStateException("외부 공개 배포는 내부 AI HTTPS를 강제해야 합니다.");
        }
    }
}
