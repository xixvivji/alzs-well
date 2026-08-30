package com.alzswell.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alzswell.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class SensitiveTextPolicyTest {

    private final SensitiveTextPolicy policy = new SensitiveTextPolicy();

    @Test
    void acceptsOrdinaryOperationalText() {
        assertThat(policy.validate("정기 납부 확인 요청", "note"))
                .isEqualTo("정기 납부 확인 요청");
    }

    @Test
    void rejectsEmailWithoutBacktrackingRegex() {
        assertThatThrownBy(() -> policy.validate("contact.user+demo@example.co.kr", "note"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsAccountLikeDigitSequences() {
        assertThatThrownBy(() -> policy.validate("계좌 번호 123456", "note"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void handlesLongPunctuationInputInLinearPath() {
        String value = "%".repeat(20_000);
        assertThat(policy.validate(value, "note")).isEqualTo(value);
    }
}
