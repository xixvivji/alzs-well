package com.alzswell.common.security;

import com.alzswell.demo.domain.DemoSession;
import com.alzswell.demo.domain.DemoSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 공개 데모의 익명 세션 소유권을 증명하는 불투명 capability를 발급하고 검증한다.
 * 원문 token은 한 번만 반환하고 DB에는 SHA-256 hash만 보관한다.
 */
@Service
public class DemoCapabilityService {

    public static final String REQUEST_HEADER = "X-Demo-Capability";
    public static final String CUSTOMER_RESPONSE_HEADER = "X-Demo-Customer-Capability";
    public static final String STAFF_RESPONSE_HEADER = "X-Demo-Staff-Capability";
    public static final String RUN_HEADER = "X-Demo-Run-Id";
    public static final String REQUEST_ROLE_ATTRIBUTE =
            "com.alzswell.common.security.DemoCapabilityService.role";
    public static final String REQUEST_HASH_ATTRIBUTE =
            "com.alzswell.common.security.DemoCapabilityService.hash";

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DemoSessionRepository sessionRepository;
    private final Clock clock;

    public DemoCapabilityService(DemoSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public IssuedCapability issue() {
        String capability = newOpaqueToken();
        return new IssuedCapability(capability, hash(capability));
    }

    public Validation validate(String rawToken, UUID sessionId, RequiredRole requiredRole) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            return Validation.notFound();
        }
        DemoSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isExpiredAt(OffsetDateTime.now(clock))) {
            return Validation.notFound();
        }

        String suppliedHash = hash(rawToken);
        boolean customerMatches = constantTimeEquals(suppliedHash, session.getCustomerCapabilityHash());
        boolean staffMatches = constantTimeEquals(suppliedHash, session.getStaffCapabilityHash());
        if (!customerMatches && !staffMatches) {
            return Validation.notFound();
        }

        CapabilityRole actualRole = customerMatches ? CapabilityRole.CUSTOMER_DEMO : CapabilityRole.DEMO_STAFF;
        if ((requiredRole == RequiredRole.CUSTOMER && actualRole != CapabilityRole.CUSTOMER_DEMO)
                || (requiredRole == RequiredRole.STAFF && actualRole != CapabilityRole.DEMO_STAFF)) {
            return Validation.scopeForbidden(actualRole, suppliedHash, session.getDemoRunId());
        }
        return Validation.allowed(actualRole, suppliedHash, session.getDemoRunId());
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String newOpaqueToken() {
        byte[] token = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private boolean constantTimeEquals(String supplied, String stored) {
        if (stored == null) {
            return false;
        }
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.US_ASCII),
                stored.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public enum RequiredRole {
        CUSTOMER,
        STAFF
    }

    public enum CapabilityRole {
        CUSTOMER_DEMO,
        DEMO_STAFF
    }

    public enum ValidationStatus {
        ALLOWED,
        NOT_FOUND,
        SCOPE_FORBIDDEN
    }

    public record Validation(
            ValidationStatus status,
            CapabilityRole role,
            String capabilityHash,
            UUID currentDemoRunId
    ) {
        static Validation allowed(CapabilityRole role, String hash, UUID currentDemoRunId) {
            return new Validation(ValidationStatus.ALLOWED, role, hash, currentDemoRunId);
        }

        static Validation notFound() {
            return new Validation(ValidationStatus.NOT_FOUND, null, null, null);
        }

        static Validation scopeForbidden(CapabilityRole role, String hash, UUID currentDemoRunId) {
            return new Validation(ValidationStatus.SCOPE_FORBIDDEN, role, hash, currentDemoRunId);
        }
    }

    public record IssuedCapability(
            String capability,
            String capabilityHash
    ) {
    }
}
