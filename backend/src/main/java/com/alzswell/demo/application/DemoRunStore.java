package com.alzswell.demo.application;

import com.alzswell.demo.domain.DemoSession;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoRunStore {

    private final JdbcTemplate jdbcTemplate;

    public DemoRunStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(DemoSession session, String fixtureVersion, OffsetDateTime startedAt) {
        jdbcTemplate.update(
                """
                insert into demo_run (
                    demo_session_id, demo_run_id, reset_version, scenario_id, snapshot_hash,
                    context_package_hash, fixture_version, started_at, ingested_at
                ) values (?, ?, ?, ?, ?, null, ?, ?, ?)
                """,
                session.getSessionId(),
                session.getDemoRunId(),
                session.getResetVersion(),
                session.getScenarioId(),
                session.getSnapshotHash(),
                fixtureVersion,
                startedAt,
                session.getScenarioId() == null ? null : startedAt
        );
    }

    public void markIngested(
            UUID sessionId,
            UUID demoRunId,
            String scenarioId,
            String snapshotHash,
            OffsetDateTime ingestedAt
    ) {
        int updated = jdbcTemplate.update(
                """
                update demo_run
                   set scenario_id = ?, snapshot_hash = ?, ingested_at = ?
                 where demo_session_id = ? and demo_run_id = ? and scenario_id is null
                """,
                scenarioId, snapshotHash, ingestedAt, sessionId, demoRunId
        );
        if (updated != 1) {
            throw new IllegalStateException("데모 실행 메타데이터를 적재 상태로 전환할 수 없습니다.");
        }
    }
}
