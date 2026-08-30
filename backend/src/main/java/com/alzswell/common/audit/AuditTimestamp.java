package com.alzswell.common.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Canonical timestamp representation shared by audit hashes and PostgreSQL timestamptz columns. */
public final class AuditTimestamp {
    private AuditTimestamp() {}

    public static OffsetDateTime canonical(OffsetDateTime value) {
        return Objects.requireNonNull(value, "audit timestamp")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
