package com.alzswell.casework.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class CaseworkRequests {
    private CaseworkRequests() {}

    public record AssignmentCommand(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String assignedTeam,
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String assignedTo,
            @Positive long expectedVersion
    ) {}

    public record ReviewCommand(
            @NotBlank @Pattern(regexp = "START_REVIEW|COMPLETE_REVIEW|REOPEN_REVIEW") String actionCode,
            @NotBlank @Size(max = 500) String note,
            @Positive long expectedVersion
    ) {}

    public record GuidancePlanCommand(
            @NotEmpty @Size(max = 4)
            List<@Pattern(regexp = "FDS_REVIEW|DELAYED_TRANSFER_GUIDANCE|SECURITY_SETTINGS_GUIDANCE|BRANCH_CONSULTATION") String> selectedActionCodes,
            @Positive long expectedVersion
    ) {}

    public record NoteCommand(@NotBlank @Size(max = 500) String noteText) {}

    public record FollowUpCommand(
            @NotBlank @Pattern(regexp = "CUSTOMER_RECHECK|BRANCH_CONSULTATION|INTERNAL_REVIEW")
            String followUpType,
            @NotNull @Future OffsetDateTime scheduledAt,
            @NotBlank @Size(max = 300) String purpose,
            @Positive long expectedCaseVersion
    ) {}

    public record FollowUpUpdateCommand(
            @NotBlank @Pattern(regexp = "RESCHEDULE|COMPLETE|CANCEL") String actionCode,
            OffsetDateTime scheduledAt,
            @Size(max = 500) String outcome,
            @Positive long expectedVersion
    ) {}
}
