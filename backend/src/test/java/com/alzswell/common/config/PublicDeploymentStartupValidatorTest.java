package com.alzswell.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PublicDeploymentStartupValidatorTest {

    @Test
    void acceptsPublicProductionDeployment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        var validator = new PublicDeploymentStartupValidator(true, environment);

        assertThatCode(validator::validateProfile).doesNotThrowAnyException();
    }

    @Test
    void rejectsPublicDevelopmentDeployment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("development");
        var validator = new PublicDeploymentStartupValidator(true, environment);

        assertThatThrownBy(validator::validateProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production");
    }
}
