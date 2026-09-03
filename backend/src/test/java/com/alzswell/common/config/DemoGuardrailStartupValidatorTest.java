package com.alzswell.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DemoGuardrailStartupValidatorTest {

    @Test
    void acceptsThePublicSyntheticDemoBoundary() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, false, false, true, false
        );

        assertThatCode(validator::validatePublicDemoBoundary).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhenAnyExternalExecutionGuardIsOpen() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, true, "AIR_GAPPED_DEMO", false, false, true, false, false, true, false
        );

        assertThatThrownBy(validator::validatePublicDemoBoundary)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("안전 가드레일");
    }

    @Test
    void refusesToExposeCustomerProfileApiInThePublicDemo() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, true, false, true, false
        );

        assertThatThrownBy(validator::validatePublicDemoBoundary)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("안전 가드레일");
    }

    @Test
    void allowsPersistedCustomerProfileApiInPrivateMode() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, true, true, false, false
        );

        assertThatCode(validator::validatePublicDemoBoundary).doesNotThrowAnyException();
    }

    @Test
    void refusesToExposeLocalSyntheticAuthInThePublicDemo() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, false, true, true, false
        );

        assertThatThrownBy(validator::validatePublicDemoBoundary)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("안전 가드레일");
    }

    @Test
    void allowsPublicSyntheticMemberAuthOnlyInsideTheSyntheticBoundary() {
        DemoGuardrailStartupValidator validator = new DemoGuardrailStartupValidator(
                true, false, "AIR_GAPPED_DEMO", false, false, true, true, true, true, true
        );

        assertThatCode(validator::validatePublicDemoBoundary).doesNotThrowAnyException();
    }
}
