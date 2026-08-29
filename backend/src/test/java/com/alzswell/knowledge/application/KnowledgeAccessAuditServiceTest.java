package com.alzswell.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.alzswell.common.security.AuditActor;
import com.alzswell.knowledge.application.KnowledgeAccessPolicy.AccessContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeAccessAuditServiceTest {
    private static final Instant NANOS_INSTANT = Instant.parse("2026-08-27T12:34:56.123456789Z");

    @Test
    void hashesAndPersistsTheSameMicrosecondTimestamp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        KnowledgeAccessAuditService service = new KnowledgeAccessAuditService(
                jdbc, mapper, Clock.fixed(NANOS_INSTANT, ZoneOffset.ofHours(9)));
        AuditActor actor = new AuditActor(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "CUSTOMER-DEMO-001",
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "STAFF");
        AccessContext access = new AccessContext(
                List.of("PROTECTION_STAFF"), List.of("STAFF"), "KNOWLEDGE_READ", actor);

        service.record(
                "DOCUMENT_DETAIL",
                access,
                "DOC-SYN-001",
                null,
                LocalDate.of(2026, 8, 27),
                List.of("DOC-SYN-001"),
                "ALLOWED",
                Map.of("queryType", "DOCUMENT_DETAIL"));

        assertThat(mockingDetails(jdbc).getInvocations()).hasSize(1);
        Invocation invocation = mockingDetails(jdbc).getInvocations().iterator().next();
        assertThat(invocation.getMethod().getName()).isEqualTo("update");
        Object[] rawArguments = invocation.getRawArguments();
        assertThat(rawArguments[0]).isEqualTo(KnowledgeAccessAuditService.INSERT_EVENT_SQL);
        Object[] values = (Object[]) rawArguments[1];
        OffsetDateTime persistedAt = (OffsetDateTime) values[13];
        assertThat(persistedAt).isEqualTo(
                OffsetDateTime.ofInstant(NANOS_INSTANT, ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));

        String expected = sha256(values[0] + "|" + values[1] + "|" + values[3] + "|" + values[4] + "|"
                + values[7] + "|" + values[8] + "|" + values[9] + "|" + List.of("DOC-SYN-001") + "|"
                + values[11] + "|" + values[12] + "|" + persistedAt);
        assertThat(values[14]).isEqualTo(expected);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
