package com.alzswell.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alzswell.demo.domain.DemoSession;
import com.alzswell.demo.domain.DemoSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DemoCapabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void issuesIndependentOpaqueTokensAndStoresOnlyTheirHashes() {
        DemoSessionRepository repository = mock(DemoSessionRepository.class);
        DemoCapabilityService service = new DemoCapabilityService(repository, clock);

        DemoCapabilityService.IssuedCapabilities issued = service.issue();

        assertThat(issued.customerCapability()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(issued.staffCapability()).hasSizeGreaterThanOrEqualTo(43)
                .isNotEqualTo(issued.customerCapability());
        assertThat(issued.customerCapabilityHash()).startsWith("sha256:")
                .isNotEqualTo(issued.customerCapability());
        assertThat(issued.staffCapabilityHash()).startsWith("sha256:")
                .isNotEqualTo(issued.staffCapability());
    }

    @Test
    void bindsHashesToSessionRoleAndExpiry() {
        DemoSessionRepository repository = mock(DemoSessionRepository.class);
        DemoCapabilityService service = new DemoCapabilityService(repository, clock);
        DemoCapabilityService.IssuedCapabilities issued = service.issue();
        UUID sessionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        DemoSession session = new DemoSession(
                sessionId,
                runId,
                7L,
                issued.customerCapabilityHash(),
                issued.staffCapabilityHash(),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC)
        );
        when(repository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(service.validate(
                issued.customerCapability(), sessionId, DemoCapabilityService.RequiredRole.CUSTOMER
        ).status()).isEqualTo(DemoCapabilityService.ValidationStatus.ALLOWED);
        assertThat(service.validate(
                issued.customerCapability(), sessionId, DemoCapabilityService.RequiredRole.STAFF
        ).status()).isEqualTo(DemoCapabilityService.ValidationStatus.SCOPE_FORBIDDEN);
        assertThat(service.validate(
                issued.staffCapability(), sessionId, DemoCapabilityService.RequiredRole.STAFF
        ).status()).isEqualTo(DemoCapabilityService.ValidationStatus.ALLOWED);
        assertThat(service.validate(
                "tampered-" + issued.customerCapability(), sessionId,
                DemoCapabilityService.RequiredRole.CUSTOMER
        ).status()).isEqualTo(DemoCapabilityService.ValidationStatus.NOT_FOUND);
    }

    @Test
    void rejectsExpiredSessionWithoutRevealingItsExistence() {
        DemoSessionRepository repository = mock(DemoSessionRepository.class);
        DemoCapabilityService service = new DemoCapabilityService(repository, clock);
        DemoCapabilityService.IssuedCapabilities issued = service.issue();
        UUID sessionId = UUID.randomUUID();
        DemoSession expired = new DemoSession(
                sessionId,
                UUID.randomUUID(),
                9L,
                issued.customerCapabilityHash(),
                issued.staffCapabilityHash(),
                OffsetDateTime.ofInstant(NOW.minusSeconds(600), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC)
        );
        when(repository.findById(sessionId)).thenReturn(Optional.of(expired));

        assertThat(service.validate(
                issued.customerCapability(), sessionId, DemoCapabilityService.RequiredRole.CUSTOMER
        ).status()).isEqualTo(DemoCapabilityService.ValidationStatus.NOT_FOUND);
    }
}
