package com.alzswell.inbox.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class InboxResponses {
    public record InboxMessage(UUID messageId, String customerId, String messageType, String title, String body,
            String relatedResourceType, UUID relatedResourceId, boolean read, OffsetDateTime readAt,
            long version, OffsetDateTime createdAt, boolean externalDeliveryExecuted) {}
    public record InboxPage(List<InboxMessage> items, String nextCursor, boolean hasNext) {}
    public record NotificationPreference(String customerId, boolean changeAlertEnabled, boolean followUpEnabled,
            boolean serviceNoticeEnabled, long version, OffsetDateTime updatedAt, boolean externalDeliveryEnabled) {}
    public record NotificationPreview(String templateCode, String title, String body,
            boolean externalDeliveryExecuted, boolean syntheticDataOnly) {}
    private InboxResponses() {}
}
