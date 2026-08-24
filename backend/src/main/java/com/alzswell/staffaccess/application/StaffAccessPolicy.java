package com.alzswell.staffaccess.application;

import java.util.Map;
import java.util.Set;

public final class StaffAccessPolicy {
    public static final String ALERT_MANAGEMENT = "ALERT_MANAGEMENT";
    public static final String CUSTOMER_CONSENT_MANAGEMENT = "CUSTOMER_CONSENT_MANAGEMENT";
    public static final String FINANCIAL_INTENT_REVIEW = "FINANCIAL_INTENT_REVIEW";
    public static final String PRIVACY_REQUEST_ASSISTANCE = "PRIVACY_REQUEST_ASSISTANCE";
    public static final String PROTECTION_CASE_MANAGEMENT = "PROTECTION_CASE_MANAGEMENT";
    public static final String PROTECTION_ENROLLMENT_REVIEW = "PROTECTION_ENROLLMENT_REVIEW";
    public static final String TRUSTED_CONTACT_MANAGEMENT = "TRUSTED_CONTACT_MANAGEMENT";

    public static final Map<String, Set<String>> PURPOSE_SCOPES = Map.of(
            ALERT_MANAGEMENT, Set.of("ALERT_READ", "ALERT_RESPOND"),
            CUSTOMER_CONSENT_MANAGEMENT, Set.of("CONSENT_READ", "CONSENT_WRITE"),
            FINANCIAL_INTENT_REVIEW, Set.of("FINANCIAL_INTENT_READ"),
            PRIVACY_REQUEST_ASSISTANCE, Set.of("PRIVACY_REQUEST_WRITE"),
            PROTECTION_CASE_MANAGEMENT, Set.of(
                    "CASE_READ", "CASE_ASSIGN", "CASE_REVIEW", "CASE_GUIDANCE", "CASE_NOTE", "CASE_FOLLOW_UP"),
            PROTECTION_ENROLLMENT_REVIEW, Set.of("PROTECTION_ENROLLMENT_READ"),
            TRUSTED_CONTACT_MANAGEMENT, Set.of("TRUSTED_CONTACT_READ", "TRUSTED_CONTACT_WRITE"));

    public static final Map<String, Set<String>> PURPOSE_ELIGIBLE_ROLES = Map.of(
            ALERT_MANAGEMENT, Set.of("DETECTION_ADMIN"),
            CUSTOMER_CONSENT_MANAGEMENT, Set.of("PROTECTION_STAFF"),
            FINANCIAL_INTENT_REVIEW, Set.of("PROTECTION_STAFF"),
            PRIVACY_REQUEST_ASSISTANCE, Set.of("PROTECTION_STAFF"),
            PROTECTION_CASE_MANAGEMENT, Set.of("PROTECTION_STAFF"),
            PROTECTION_ENROLLMENT_REVIEW, Set.of("PROTECTION_STAFF"),
            TRUSTED_CONTACT_MANAGEMENT, Set.of("PROTECTION_STAFF"));

    public static final Set<String> ALLOWED_SCOPES = PURPOSE_SCOPES.values().stream()
            .flatMap(Set::stream).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private StaffAccessPolicy() {}

    public static boolean allows(String purposeCode, String scope) {
        return PURPOSE_SCOPES.getOrDefault(purposeCode, Set.of()).contains(scope);
    }

    public static boolean allowsAll(String purposeCode, java.util.Collection<String> scopes) {
        return PURPOSE_SCOPES.getOrDefault(purposeCode, Set.of()).containsAll(scopes);
    }

    public static Set<String> eligibleRoles(String purposeCode) {
        return PURPOSE_ELIGIBLE_ROLES.getOrDefault(purposeCode, Set.of());
    }
}
