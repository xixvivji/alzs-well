package com.alzswell.knowledge.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class KnowledgeGovernanceRequests {
    private KnowledgeGovernanceRequests() {}

    public record SourceTransformation(
            @NotBlank @Pattern(regexp="CREDENTIAL_REDACTION") String type,
            @NotBlank @Size(max=80) @Pattern(regexp="[A-Z][A-Z0-9_]{2,79}") String ruleId,
            @NotBlank @Size(max=80) @Pattern(regexp="[A-Z][A-Z0-9_]{2,79}") String replacement
    ) {}

    public record RegisterDocumentCommand(
            @NotBlank @Pattern(regexp="[A-Z0-9]+(?:-[A-Z0-9]+)*") @Size(max=80) String documentId,
            @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]{0,39}") String versionLabel,
            @NotBlank @Size(max=200) String title,
            @NotBlank @Size(max=160) String issuer,
            @NotBlank @Pattern(regexp="OFFICIAL_EXTERNAL|INTERNAL_POLICY|SYNTHETIC_FIXTURE") String sourceType,
            @NotBlank @Size(max=500) @Pattern(regexp="(?!/)(?!.*(?:^|/)[.][.](?:/|$))[A-Za-z0-9._/-]+") String sourcePath,
            @Size(max=1000) @Pattern(regexp="https://[^ ]+") String sourceUrl,
            @NotBlank @Pattern(regexp="sha256:[0-9a-f]{64}") String sourceHash,
            @NotNull @Size(max=10) List<@Valid SourceTransformation> sourceTransformations,
            @NotBlank @Pattern(regexp="LAW|REGULATION|PUBLIC_GUIDE|PUBLIC_NOTICE|FORM|INTERNAL_POLICY|SYNTHETIC_FIXTURE") String documentType,
            @NotBlank @Pattern(regexp="PUBLIC_OFFICIAL|INTERNAL|CONFIDENTIAL") String classification,
            @NotBlank @Pattern(regexp="CUSTOMER|STAFF|BOTH") String audience,
            @NotEmpty @Size(max=6) List<@Pattern(regexp="CUSTOMER|PROTECTION_STAFF|DETECTION_ADMIN|COMPLIANCE_REVIEWER|KNOWLEDGE_ADMIN|SECURITY_ADMIN") String> allowedRoles,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull LocalDate checkedAt,
            @NotBlank @Pattern(regexp="REVIEW_REQUIRED|INTERNAL_USE_APPROVED|PUBLIC_REUSE_ALLOWED|SYNTHETIC_UNRESTRICTED") String usageRights,
            @Size(max=80) @Pattern(regexp="[A-Z0-9]+(?:-[A-Z0-9]+)*") String supersedesDocumentId,
            @Size(max=40) @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]{0,39}") String supersedesVersionLabel
    ) {}

    public record PublishDocumentCommand(
            @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]{0,39}") String versionLabel,
            @Positive long expectedVersion,
            @NotBlank @Size(max=120) @Pattern(regexp="[A-Za-z0-9._:-]+") String approvalReference
    ) {}
}
