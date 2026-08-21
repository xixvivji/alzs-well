package com.alzswell.intent.api;
import java.time.OffsetDateTime; import java.util.List; import java.util.UUID;
public final class FinancialIntentResponses {private FinancialIntentResponses(){}
 public record Intent(UUID intentId,String customerId,String status,long version,String paymentContinuity,String explanationMode,
  String helpCondition,List<String> shareScopes,boolean disclaimerAccepted,OffsetDateTime createdAt,OffsetDateTime updatedAt,
  OffsetDateTime approvedAt,OffsetDateTime revokedAt,boolean legallyBinding,boolean healthInferenceUsed){}
 public record Preparation(String readiness,Intent latestApproved,boolean legalDisclaimerRequired){}
 public record Versions(List<Intent> items,int total){}
 public record StaffSummary(UUID intentId,String customerId,long version,String paymentContinuity,String explanationMode,
  String helpCondition,List<String> sharedScopes,boolean legallyBinding,boolean nonConsentedFieldsExcluded){}
}
