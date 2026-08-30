package com.alzswell.demo.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AiFinancialAssistanceRequests {
    private AiFinancialAssistanceRequests() {}

    public record IntentSuggestion(
            @NotBlank @Size(min = 4, max = 500) String utterance
    ) {}

    public record IntentDraft(
            @PositiveOrZero long expectedVersion,
            @NotBlank @Pattern(regexp = "KEEP_ESSENTIAL_PAYMENTS|REVIEW_BEFORE_CHANGE")
            String paymentContinuity,
            @NotBlank @Pattern(regexp = "SIMPLE_TEXT|VOICE_AND_TEXT|STAFF_EXPLANATION")
            String explanationMode,
            @NotBlank @Pattern(regexp = "ON_REPEATED_CHANGE|ON_CUSTOMER_REQUEST|NEVER_AUTOMATIC")
            String helpCondition,
            @Size(max = 4) List<@Pattern(regexp = "PAYMENT_PREFERENCE|EXPLANATION_PREFERENCE|HELP_CONDITION|ACCESSIBILITY") String> shareScopes
    ) {}

    public record IntentApproval(
            @Positive long expectedVersion,
            @AssertTrue boolean disclaimerAccepted
    ) {}

    public record PlainLanguage(
            @NotBlank @Pattern(regexp = "MISSED_RECURRING_COUNT|DUPLICATE_TRANSFER_COUNT|REPEATED_CONFIRMATION_COUNT|NEW_COUNTERPARTY_COUNT|UNUSUAL_TIME_COUNT|UNUSUAL_AMOUNT_COUNT")
            String featureCode
    ) {}
}
