package com.alzswell.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ExplicitProfileStartupValidatorTest {

    @Test
    void acceptsExactlyOneKnownProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        assertDoesNotThrow(() -> new ExplicitProfileStartupValidator(environment).validate());
    }

    @Test
    void rejectsMissingUnknownOrMultipleProfiles() {
        assertThrows(IllegalStateException.class,
                () -> new ExplicitProfileStartupValidator(new MockEnvironment()).validate());

        MockEnvironment unknown = new MockEnvironment();
        unknown.setActiveProfiles("default");
        assertThrows(IllegalStateException.class,
                () -> new ExplicitProfileStartupValidator(unknown).validate());

        MockEnvironment multiple = new MockEnvironment();
        multiple.setActiveProfiles("development", "production");
        assertThrows(IllegalStateException.class,
                () -> new ExplicitProfileStartupValidator(multiple).validate());
    }
}
