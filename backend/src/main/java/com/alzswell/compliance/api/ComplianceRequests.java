package com.alzswell.compliance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class ComplianceRequests {
    private ComplianceRequests() {}
    public record AuditExportRequest(
            @NotNull OffsetDateTime from,
            @NotNull OffsetDateTime to,
            @NotEmpty @Size(max=10) List<@Pattern(regexp="[A-Z][A-Z0-9_]{2,39}") String> sourceTypes,
            @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{2,59}") String purposeCode,
            @NotBlank @Size(max=100) @Pattern(regexp="[A-Za-z0-9._:/-]+") String approvalReference) {}
}
