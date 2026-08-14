package com.alzswell.demo.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import com.alzswell.demo.api.DemoErrorCode;
import com.alzswell.demo.api.DemoScenarioIngestedResponse;
import com.alzswell.demo.api.DemoScenarioListResponse;
import com.alzswell.demo.api.DemoSessionCreatedResponse;
import com.alzswell.demo.api.DemoSessionResetResponse;
import com.alzswell.demo.api.DemoSessionResponse;
import com.alzswell.demo.domain.DemoIdempotencyRecord;
import com.alzswell.demo.domain.DemoIdempotencyRecordRepository;
import com.alzswell.demo.domain.DemoSession;
import com.alzswell.demo.domain.DemoSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoSessionService {

    public static final String SUPPORTED_SCENARIO_ID = "FIN_MGMT_AB_001";
    public static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";
    public static final String ALERT_ID = "ALERT_FIN_MGMT_001";
    public static final String CASE_ID = "CASE_FIN_MGMT_001";

    private static final String DATA_MODE = "SYNTHETIC_ONLY";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DemoSessionRepository sessionRepository;
    private final DemoIdempotencyRecordRepository idempotencyRepository;
    private final DemoAuditWriter auditWriter;
    private final DemoRunStore demoRunStore;
    private final SyntheticFinanceFixtureService fixtureService;
    private final Clock clock;
    private final long sessionTtlSeconds;
    private final long maxActiveSessions;
    private final String fixtureVersion;
    private final String algorithmVersion;
    private final String policyVersion;

    public DemoSessionService(
            DemoSessionRepository sessionRepository,
            DemoIdempotencyRecordRepository idempotencyRepository,
            DemoAuditWriter auditWriter,
            DemoRunStore demoRunStore,
            SyntheticFinanceFixtureService fixtureService,
            Clock clock,
            @Value("${app.demo.session-ttl-seconds:7200}") long sessionTtlSeconds,
            @Value("${app.demo.max-active-sessions:1000}") long maxActiveSessions,
            @Value("${app.versions.fixture:fin-mgmt-ab-v2.0.0}") String fixtureVersion,
            @Value("${app.versions.algorithm:baseline-rules-v2.0.0}") String algorithmVersion,
            @Value("${app.versions.policy:context-policy-v1.0.0}") String policyVersion
    ) {
        this.sessionRepository = sessionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditWriter = auditWriter;
        this.demoRunStore = demoRunStore;
        this.fixtureService = fixtureService;
        this.clock = clock;
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.maxActiveSessions = maxActiveSessions;
        this.fixtureVersion = fixtureVersion;
        this.algorithmVersion = algorithmVersion;
        this.policyVersion = policyVersion;
    }

    @Transactional
    public synchronized DemoSessionCreatedResponse createSession(
            String customerCapabilityHash,
            String staffCapabilityHash
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (sessionRepository.countByExpiresAtAfter(now) >= maxActiveSessions) {
            throw new BusinessException(DemoErrorCode.SESSION_RATE_LIMITED);
        }
        UUID demoRunId = UUID.randomUUID();
        DemoSession session = new DemoSession(
                UUID.randomUUID(),
                demoRunId,
                nextPositiveSeed(),
                customerCapabilityHash,
                staffCapabilityHash,
                now,
                now.plusSeconds(sessionTtlSeconds)
        );
        sessionRepository.saveAndFlush(session);
        demoRunStore.create(session, fixtureVersion, now);

        auditWriter.write(
                session.getSessionId(),
                session.getDemoRunId(),
                "DEMO_SESSION_CREATED",
                Map.of(
                        "dataMode", DATA_MODE,
                        "resetVersion", session.getResetVersion()
                ),
                now
        );
        return toCreatedResponse(session, session.getResetVersion());
    }

    @Transactional(readOnly = true)
    public DemoSessionResponse getSession(UUID sessionId) {
        DemoSession session = requireActiveSession(sessionId);
        return new DemoSessionResponse(
                session.getSessionId(),
                session.getScenarioId() == null ? null : session.getDemoRunId(),
                Long.toUnsignedString(session.getScenarioSeed()),
                session.getScenarioId(),
                "ACTIVE",
                session.getResetVersion(),
                session.getSnapshotHash(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                DATA_MODE
        );
    }

    public DemoScenarioListResponse getScenarios() {
        return new DemoScenarioListResponse(List.of(new DemoScenarioListResponse.DemoScenarioItem(
                SUPPORTED_SCENARIO_ID,
                "금융관리 행동변화 3·2·7 A/B 비교",
                9,
                3,
                List.of("FIN_MGMT_A_NORMAL_CONTEXT", "FIN_MGMT_B_NO_CONTEXT"),
                true
        )));
    }

    @Transactional(readOnly = true)
    public DemoSession requireFinancialFixture(UUID sessionId, String customerId) {
        DemoSession session = requireActiveSession(sessionId);
        if (session.getCustomerId() != null && !session.getCustomerId().equals(customerId)) {
            throw new BusinessException(DemoErrorCode.SESSION_NOT_FOUND);
        }
        requireFixtureMetadata(session);
        return session;
    }

    @Transactional(readOnly = true)
    public DemoSession requireFinancialFixture(UUID sessionId) {
        DemoSession session = requireActiveSession(sessionId);
        requireFixtureMetadata(session);
        return session;
    }

    @Transactional(readOnly = true)
    public DemoSession requireActive(UUID sessionId) {
        return requireActiveSession(sessionId);
    }

    private void requireFixtureMetadata(DemoSession session) {
        if (session.getScenarioId() == null || session.getSnapshotHash() == null || session.getCustomerId() == null
                || !fixtureService.isComplete(session)) {
            throw new BusinessException(DemoErrorCode.SYNTHETIC_FIXTURE_NOT_READY);
        }
    }

    @Transactional
    public synchronized DemoScenarioIngestedResponse ingest(
            UUID sessionId,
            String scenarioId,
            String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateScenarioId(scenarioId);
        DemoSession session = requireActiveSession(sessionId);
        String operationKey = "INGEST:" + sessionId;
        String requestHash = hashRequest("scenarioId=" + scenarioId);
        String idempotencyKeyHash = hashRequest(idempotencyKey);
        DemoIdempotencyRecord existing = idempotencyRepository
                .findByOperationKeyAndIdempotencyKey(operationKey, idempotencyKeyHash)
                .orElse(null);
        if (existing != null) {
            requireSameRequest(existing, requestHash);
            return toIngestedResponse(
                    session,
                    existing.getResultDemoRunId(),
                    existing.getResultScenarioId(),
                    existing.getResultSnapshotHash(),
                    requestHash,
                    true
            );
        }

        if (session.getScenarioId() != null) {
            idempotencyRepository.save(new DemoIdempotencyRecord(
                    UUID.randomUUID(), operationKey, idempotencyKeyHash, sessionId,
                    session.getResetVersion(), session.getDemoRunId(), requestHash,
                    session.getScenarioId(), session.getSnapshotHash(), session.getAlertId(),
                    OffsetDateTime.now(clock)
            ));
            return toIngestedResponse(session, session.getDemoRunId(),
                    session.getScenarioId(), session.getSnapshotHash(), requestHash, false);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        String snapshotHash = fixtureService.createSnapshotHash(session, scenarioId, fixtureVersion);
        session.ingest(scenarioId, snapshotHash, CUSTOMER_ID, ALERT_ID, null, now);
        sessionRepository.saveAndFlush(session);
        demoRunStore.markIngested(sessionId, session.getDemoRunId(), scenarioId, snapshotHash, now);
        fixtureService.restore(session);
        idempotencyRepository.save(new DemoIdempotencyRecord(
                UUID.randomUUID(),
                operationKey,
                idempotencyKeyHash,
                sessionId,
                session.getResetVersion(),
                session.getDemoRunId(),
                requestHash,
                scenarioId,
                snapshotHash,
                ALERT_ID,
                now
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioId", scenarioId);
        payload.put("snapshotHash", snapshotHash);
        payload.put("customerId", CUSTOMER_ID);
        payload.put("alertId", ALERT_ID);
        payload.put("requestHash", requestHash);
        payload.put("idempotencyKeyHash", idempotencyKeyHash);
        auditWriter.write(sessionId, session.getDemoRunId(), "DEMO_SCENARIO_INGESTED", payload, now);
        return toIngestedResponse(
                session, session.getDemoRunId(), scenarioId, snapshotHash, requestHash, false
        );
    }

    @Transactional
    public synchronized DemoSessionResetResponse reset(
            UUID sessionId,
            UUID requestedDemoRunId,
            String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
        DemoSession session = requireActiveSession(sessionId);
        String requestedRunScope = requestedDemoRunId == null ? "DRAFT" : requestedDemoRunId.toString();
        String operationKey = "RESET:" + sessionId + ":" + requestedRunScope;
        String requestHash = hashRequest("RESET:" + requestedRunScope);
        String idempotencyKeyHash = hashRequest(idempotencyKey);
        DemoIdempotencyRecord existing = idempotencyRepository
                .findByOperationKeyAndIdempotencyKey(operationKey, idempotencyKeyHash)
                .orElse(null);
        if (existing != null) {
            requireSameRequest(existing, requestHash);
            return toResetResponse(
                    session,
                    requestedDemoRunId,
                    existing.getResultDemoRunId(),
                    existing.getResultScenarioId(),
                    existing.getResultSnapshotHash(),
                    existing.getResultAlertId(),
                    existing.getResultVersion() == null ? session.getResetVersion() : existing.getResultVersion(),
                    existing.getResultTimestamp(),
                    requestHash,
                    true
            );
        }

        if (session.getScenarioId() != null
                && !session.getDemoRunId().equals(requestedDemoRunId)) {
            throw new BusinessException(DemoErrorCode.RUN_STALE);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID previousDemoRunId = session.getDemoRunId();
        session.reset(UUID.randomUUID(), now);
        sessionRepository.saveAndFlush(session);
        demoRunStore.create(session, fixtureVersion, now);
        if (SUPPORTED_SCENARIO_ID.equals(session.getScenarioId())) {
            fixtureService.restore(session);
        }
        idempotencyRepository.save(new DemoIdempotencyRecord(
                UUID.randomUUID(),
                operationKey,
                idempotencyKeyHash,
                sessionId,
                session.getResetVersion(),
                session.getDemoRunId(),
                requestHash,
                session.getScenarioId(),
                session.getSnapshotHash(),
                session.getAlertId(),
                now
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioId", session.getScenarioId());
        payload.put("snapshotHash", session.getSnapshotHash());
        payload.put("resetVersion", session.getResetVersion());
        payload.put("previousDemoRunId", previousDemoRunId);
        payload.put("demoRunId", session.getDemoRunId());
        payload.put("requestHash", requestHash);
        payload.put("idempotencyKeyHash", idempotencyKeyHash);
        auditWriter.write(sessionId, session.getDemoRunId(), "DEMO_SESSION_RESET", payload, now);
        return toResetResponse(
                session,
                previousDemoRunId,
                session.getDemoRunId(),
                session.getScenarioId(),
                session.getSnapshotHash(),
                session.getAlertId(),
                session.getResetVersion(),
                now,
                requestHash,
                false
        );
    }

    private DemoSession requireActiveSession(UUID sessionId) {
        DemoSession session = loadSession(sessionId);
        if (session.isExpiredAt(OffsetDateTime.now(clock))) {
            // 만료 여부 자체도 세션 존재 정보이므로 공개 API에서는 찾을 수 없음으로 통일한다.
            throw new BusinessException(DemoErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private DemoSession loadSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(DemoErrorCode.SESSION_NOT_FOUND));
    }

    private void validateScenarioId(String scenarioId) {
        if (!SUPPORTED_SCENARIO_ID.equals(scenarioId)) {
            throw new BusinessException(DemoErrorCode.SCENARIO_NOT_SUPPORTED);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT,
                    "Idempotency-Key는 8~64자의 영문, 숫자, '.', '_', ':', '-'만 사용할 수 있습니다."
            );
        }
    }

    private DemoSessionCreatedResponse toCreatedResponse(DemoSession session, int resetVersion) {
        return new DemoSessionCreatedResponse(
                session.getSessionId(),
                Long.toUnsignedString(session.getScenarioSeed()),
                null,
                session.getExpiresAt(),
                resetVersion,
                DATA_MODE
        );
    }

    private DemoScenarioIngestedResponse toIngestedResponse(
            DemoSession session,
            UUID demoRunId,
            String scenarioId,
            String snapshotHash,
            String requestHash,
            boolean idempotencyReplayed
    ) {
        return new DemoScenarioIngestedResponse(
                scenarioId,
                demoRunId,
                CUSTOMER_ID,
                ALERT_ID,
                null,
                Long.toUnsignedString(session.getScenarioSeed()),
                snapshotHash,
                new DemoScenarioIngestedResponse.DatePeriod(
                        LocalDate.of(2025, 8, 1),
                        LocalDate.of(2026, 4, 30)
                ),
                new DemoScenarioIngestedResponse.DatePeriod(
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 7, 31)
                ),
                List.of("MISSED_RECURRING", "DUPLICATE_TRANSFER", "REPEATED_CONFIRMATION"),
                "NEEDS_CONTEXT",
                "AWAITING_CONTEXT",
                algorithmVersion,
                policyVersion,
                new com.alzswell.demo.api.CommandMetadata(requestHash, idempotencyReplayed)
        );
    }

    private DemoSessionResetResponse toResetResponse(
            DemoSession session,
            UUID previousDemoRunId,
            UUID demoRunId,
            String scenarioId,
            String snapshotHash,
            String alertId,
            int resetVersion,
            OffsetDateTime restoredAt,
            String requestHash,
            boolean idempotencyReplayed
    ) {
        return new DemoSessionResetResponse(
                session.getSessionId(),
                scenarioId == null ? null : previousDemoRunId,
                scenarioId == null ? null : demoRunId,
                Long.toUnsignedString(session.getScenarioSeed()),
                scenarioId,
                snapshotHash,
                alertId,
                resetVersion,
                restoredAt,
                new com.alzswell.demo.api.CommandMetadata(requestHash, idempotencyReplayed)
        );
    }

    private long nextPositiveSeed() {
        long seed;
        do {
            seed = SECURE_RANDOM.nextLong() & Long.MAX_VALUE;
        } while (seed == 0L);
        return seed;
    }

    private String hashRequest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void requireSameRequest(DemoIdempotencyRecord existing, String requestHash) {
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new BusinessException(DemoErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }
}
