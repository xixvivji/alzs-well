package com.alzswell.transfer.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TransferTemplateResponses {
    private TransferTemplateResponses() {}

    public record Template(
            UUID templateId, String templateName, UUID sourceAccountId,
            String sourceAccountDisplayName, String maskedSourceAccountNumber,
            UUID beneficiaryId, String beneficiaryDisplayName, String maskedBeneficiaryAccount,
            BigDecimal amount, String currency, String purposeCode, String status,
            long version, OffsetDateTime createdAt, boolean syntheticData,
            boolean externalActionAvailable, boolean externalActionExecuted
    ) {}

    public record TemplateList(List<Template> items, int total, int maxTemplates,
                               boolean syntheticData, boolean externalActionAvailable) {}

    public record Deletion(UUID templateId, String status, long version,
                           OffsetDateTime deletedAt, boolean alreadyDeleted,
                           boolean externalActionExecuted) {}
}
