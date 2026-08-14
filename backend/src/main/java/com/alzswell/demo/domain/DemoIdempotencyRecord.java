package com.alzswell.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "demo_idempotency_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_demo_idempotency_operation_key",
                columnNames = {"operation_key", "idempotency_key"}
        )
)
public class DemoIdempotencyRecord {

    @Id
    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "operation_key", nullable = false, length = 180)
    private String operationKey;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "demo_session_id", nullable = false)
    private UUID demoSessionId;

    @Column(name = "result_version")
    private Integer resultVersion;

    @Column(name = "result_demo_run_id", nullable = false)
    private UUID resultDemoRunId;

    @Column(name = "request_hash", nullable = false, length = 80)
    private String requestHash;

    @Column(name = "result_scenario_id", length = 40)
    private String resultScenarioId;

    @Column(name = "result_snapshot_hash", length = 80)
    private String resultSnapshotHash;

    @Column(name = "result_alert_id", length = 80)
    private String resultAlertId;

    @Column(name = "result_timestamp", nullable = false)
    private OffsetDateTime resultTimestamp;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DemoIdempotencyRecord() {
    }

    public DemoIdempotencyRecord(
            UUID recordId,
            String operationKey,
            String idempotencyKey,
            UUID demoSessionId,
            Integer resultVersion,
            UUID resultDemoRunId,
            String requestHash,
            String resultScenarioId,
            String resultSnapshotHash,
            String resultAlertId,
            OffsetDateTime resultTimestamp
    ) {
        this.recordId = recordId;
        this.operationKey = operationKey;
        this.idempotencyKey = idempotencyKey;
        this.demoSessionId = demoSessionId;
        this.resultVersion = resultVersion;
        this.resultDemoRunId = resultDemoRunId;
        this.requestHash = requestHash;
        this.resultScenarioId = resultScenarioId;
        this.resultSnapshotHash = resultSnapshotHash;
        this.resultAlertId = resultAlertId;
        this.resultTimestamp = resultTimestamp;
        this.createdAt = resultTimestamp;
    }

    public UUID getDemoSessionId() {
        return demoSessionId;
    }

    public Integer getResultVersion() {
        return resultVersion;
    }

    public UUID getResultDemoRunId() {
        return resultDemoRunId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResultScenarioId() {
        return resultScenarioId;
    }

    public String getResultSnapshotHash() {
        return resultSnapshotHash;
    }

    public String getResultAlertId() {
        return resultAlertId;
    }

    public OffsetDateTime getResultTimestamp() {
        return resultTimestamp;
    }
}
