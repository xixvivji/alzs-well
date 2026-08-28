package com.alzswell.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuditTimestampTest {
    @Test
    void canonicalizesOffsetAndPostgreSqlTimestampPrecision() {
        OffsetDateTime input = OffsetDateTime.parse("2026-08-27T12:34:56.123456789+09:00");

        OffsetDateTime canonical = AuditTimestamp.canonical(input);

        assertThat(canonical.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(canonical).isEqualTo(OffsetDateTime.parse("2026-08-27T03:34:56.123456Z"));
        assertThat(canonical.getNano() % 1_000).isZero();
    }
}
