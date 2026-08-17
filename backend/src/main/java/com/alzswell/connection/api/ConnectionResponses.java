package com.alzswell.connection.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ConnectionResponses {
    private ConnectionResponses() {}

    public record Scope(String scopeCode, String displayName, boolean readOnly, String consentStatus) {}
    public record InstitutionSummary(String institutionId, String displayName, String institutionType,
                                     String providerMode, boolean connectionAvailable, LocalDate dataAsOf) {}
    public record InstitutionList(List<InstitutionSummary> items, int total) {}
    public record InstitutionDetail(InstitutionSummary institution, List<Scope> supportedScopes) {}
    public record ConnectionSummary(UUID connectionId, String customerId, InstitutionSummary institution,
                                    String connectionStatus, OffsetDateTime consentedAt,
                                    OffsetDateTime consentExpiresAt, OffsetDateTime lastSyncedAt,
                                    String providerMode, long version) {}
    public record ConnectionList(List<ConnectionSummary> items, int total) {}
    public record ConnectionDetail(ConnectionSummary connection, List<Scope> consentScopes) {}
}
