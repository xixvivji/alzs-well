package com.alzswell.intent.api;
import jakarta.validation.constraints.*; import java.util.List;
public final class FinancialIntentRequests {private FinancialIntentRequests(){}
 public record Draft(@NotBlank @Pattern(regexp="KEEP_ESSENTIAL_PAYMENTS|REVIEW_BEFORE_CHANGE") String paymentContinuity,
  @NotBlank @Pattern(regexp="SIMPLE_TEXT|VOICE_AND_TEXT|STAFF_EXPLANATION") String explanationMode,
  @NotBlank @Pattern(regexp="ON_REPEATED_CHANGE|ON_CUSTOMER_REQUEST|NEVER_AUTOMATIC") String helpCondition,
  @NotNull @Size(max=4) List<@Pattern(regexp="PAYMENT_PREFERENCE|EXPLANATION_PREFERENCE|HELP_CONDITION|ACCESSIBILITY") String> shareScopes){}
 public record Update(@Positive long expectedVersion,@NotBlank @Pattern(regexp="KEEP_ESSENTIAL_PAYMENTS|REVIEW_BEFORE_CHANGE") String paymentContinuity,
  @NotBlank @Pattern(regexp="SIMPLE_TEXT|VOICE_AND_TEXT|STAFF_EXPLANATION") String explanationMode,
  @NotBlank @Pattern(regexp="ON_REPEATED_CHANGE|ON_CUSTOMER_REQUEST|NEVER_AUTOMATIC") String helpCondition,
  @NotNull @Size(max=4) List<@Pattern(regexp="PAYMENT_PREFERENCE|EXPLANATION_PREFERENCE|HELP_CONDITION|ACCESSIBILITY") String> shareScopes){}
 public record Approve(@Positive long expectedVersion,@AssertTrue boolean disclaimerAccepted){}
 public record Revoke(@Positive long expectedVersion,@NotBlank @Size(max=300) String reason){}
}
