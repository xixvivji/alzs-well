package com.alzswell.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "demo_session")
public class DemoSession {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "scenario_seed", nullable = false)
    private long scenarioSeed;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "reset_version", nullable = false)
    private int resetVersion;

    @Column(name = "demo_run_id", nullable = false)
    private UUID demoRunId;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "customer_capability_hash", length = 80)
    private String customerCapabilityHash;

    @Column(name = "staff_capability_hash", length = 80)
    private String staffCapabilityHash;

    @Column(name = "scenario_id", length = 40)
    private String scenarioId;

    @Column(name = "snapshot_hash", length = 80)
    private String snapshotHash;

    @Column(name = "customer_id", length = 80)
    private String customerId;

    @Column(name = "alert_id", length = 80)
    private String alertId;

    @Column(name = "case_id", length = 80)
    private String caseId;

    @Column(name = "ingested_at")
    private OffsetDateTime ingestedAt;

    @Column(name = "last_reset_at")
    private OffsetDateTime lastResetAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DemoSession() {
    }

    public DemoSession(
            UUID sessionId,
            UUID demoRunId,
            long scenarioSeed,
            String customerCapabilityHash,
            String staffCapabilityHash,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt
    ) {
        this.sessionId = sessionId;
        this.demoRunId = demoRunId;
        this.scenarioSeed = scenarioSeed;
        this.customerCapabilityHash = Objects.requireNonNull(customerCapabilityHash);
        this.staffCapabilityHash = Objects.requireNonNull(staffCapabilityHash);
        if (customerCapabilityHash.equals(staffCapabilityHash)) {
            throw new IllegalArgumentException("고객과 행원 capability hash는 달라야 합니다.");
        }
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
        this.resetVersion = 0;
    }

    public void ingest(
            String scenarioId,
            String snapshotHash,
            String customerId,
            String alertId,
            String caseId,
            OffsetDateTime ingestedAt
    ) {
        this.scenarioId = scenarioId;
        this.snapshotHash = snapshotHash;
        this.customerId = customerId;
        this.alertId = alertId;
        this.caseId = caseId;
        this.ingestedAt = ingestedAt;
        this.updatedAt = ingestedAt;
    }

    public void reset(UUID nextDemoRunId, OffsetDateTime resetAt) {
        this.resetVersion += 1;
        this.demoRunId = nextDemoRunId;
        this.lastResetAt = resetAt;
        this.updatedAt = resetAt;
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public long getScenarioSeed() {
        return scenarioSeed;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getResetVersion() {
        return resetVersion;
    }

    public UUID getDemoRunId() {
        return demoRunId;
    }

    public String getCustomerCapabilityHash() {
        return customerCapabilityHash;
    }

    public String getStaffCapabilityHash() {
        return staffCapabilityHash;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getCaseId() {
        return caseId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
