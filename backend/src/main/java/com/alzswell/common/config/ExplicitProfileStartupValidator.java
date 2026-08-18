package com.alzswell.common.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 명시적인 실행 환경 없이 개발 기본값으로 기동되는 것을 차단한다. */
@Component
public class ExplicitProfileStartupValidator {
    private static final Set<String> ALLOWED_PROFILES = Set.of("development", "test", "production");
    private final Environment environment;

    public ExplicitProfileStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length != 1 || Arrays.stream(activeProfiles).noneMatch(ALLOWED_PROFILES::contains)) {
            throw new IllegalStateException(
                    "SPRING_PROFILES_ACTIVE는 development, test, production 중 하나를 명시해야 합니다."
            );
        }
    }
}
