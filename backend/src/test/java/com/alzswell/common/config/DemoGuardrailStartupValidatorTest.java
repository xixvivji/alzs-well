package com.alzswell.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DemoGuardrailStartupValidatorTest {

    @Test
    void acceptsThePublicSyntheticDemoBoundary() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, false, false, true
        );

        assertThatCode(validator::validatePublicDemoBoundary).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhenAnyExternalExecutionGuardIsOpen() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, true, "AIR_GAPPED_DEMO", false, false, true, false, false, true
        );

        assertThatThrownBy(validator::validatePublicDemoBoundary)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("안전 가드레일");
    }

    @Test
    void refusesToExposeCustomerProfileApiInThePublicDemo() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, true, false, true
        );

        assertThatThrownBy(validator::validatePublicDemoBoundary)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("안전 가드레일");
    }

    @Test
    void allowsPersistedCustomerProfileApiInPrivateMode() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, true, true, false
        );

        assertThatCode(validator::validatePublicDemoBoundary).doesNotThrowAnyException();
    }

    @Test
    void refusesToExposeLocalSyntheticAuthInThePublicDemo() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, false, true, true
        );

        assertThatThrownBy(validator::validatePublicDemoBoundary)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("안전 가드레일");
    }
}
