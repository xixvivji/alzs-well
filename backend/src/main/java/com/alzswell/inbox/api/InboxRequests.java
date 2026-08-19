package com.alzswell.inbox.api;

import jakarta.validation.constraints.*;
import java.util.Map;

public final class InboxRequests {
    public record MarkReadCommand(@NotNull @Positive Long expectedVersion) {}
    public record NotificationPreferenceCommand(
            @NotNull @Positive Long expectedVersion,
            @NotNull Boolean changeAlertEnabled,
            @NotNull Boolean followUpEnabled,
            @NotNull Boolean serviceNoticeEnabled) {}
    public record NotificationPreviewCommand(
            @NotBlank @Size(max=50) String templateCode,
            @NotNull @Size(max=10) Map<@Size(max=40) String, @Size(max=120) String> facts) {}
    private InboxRequests() {}
}
