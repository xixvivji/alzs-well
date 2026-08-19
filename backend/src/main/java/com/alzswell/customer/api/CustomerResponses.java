package com.alzswell.customer.api;

import java.time.OffsetDateTime;

public final class CustomerResponses {
    private CustomerResponses() {}

    public record CustomerSummary(
            String customerId,
            String displayName,
            String organization,
            String region,
            String status,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record DisplayProfile(
            String customerId,
            String displayName,
            long version,
            OffsetDateTime updatedAt
    ) {}

    public record Preferences(
            String customerId,
            boolean smsNotificationEnabled,
            boolean pushNotificationEnabled,
            boolean inAppNotificationEnabled,
            long version,
            OffsetDateTime updatedAt
    ) {}

    public record AccessibilitySettings(
            String customerId,
            boolean largeFont,
            boolean highContrast,
            boolean speechGuidance,
            boolean oneHandMode,
            long version,
            OffsetDateTime updatedAt
    ) {}

    public record DataFreshness(
            String accounts,
            String transactions,
            String baseline
    ) {}

    public record DataSummary(
            String customerId,
            int institutions,
            int accounts,
            int transactionsSynced,
            OffsetDateTime lastSyncAt,
            DataFreshness dataFreshness,
            OffsetDateTime updatedAt
    ) {}
}
