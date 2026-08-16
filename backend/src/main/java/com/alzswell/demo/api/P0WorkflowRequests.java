package com.alzswell.demo.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class P0WorkflowRequests {

    public record CopilotDraftCommand(@NotBlank String draftType) {
    }

    public record CaseNoteCommand(
            @NotNull @Positive @JsonAlias("expectedCaseVersion") Long caseVersion,
            @NotBlank @Size(max = 500) String note
    ) {
    }

    public record FollowUpCommand(
            @NotNull @Positive @JsonAlias("expectedCaseVersion") Long caseVersion,
            @NotNull OffsetDateTime scheduledAt,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    private P0WorkflowRequests() {
    }

    public record ContextCommand(
            @NotBlank String responseCode,
            @NotBlank String demoBranchCode
    ) {
    }

    public record CaseReviewCommand(
            @NotBlank String action,
            @NotNull @Positive @JsonAlias("expectedCaseVersion") Long caseVersion,
            @Size(max = 500) String note,
            OffsetDateTime followUpAt
    ) {
    }

    public record GuidancePlanCommand(
            @NotNull @Positive @JsonAlias("expectedCaseVersion") Long caseVersion,
            @NotBlank String decision,
            @NotEmpty @Size(max = 10) List<@NotBlank String> selectedActionCodes,
            @Size(max = 500) String staffNote
    ) {
        public GuidancePlanCommand {
            selectedActionCodes = selectedActionCodes == null ? null : List.copyOf(selectedActionCodes);
        }
    }
}
