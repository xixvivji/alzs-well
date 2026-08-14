alter table demo_session
    add column scenario_id varchar(40),
    add column snapshot_hash varchar(80),
    add column customer_id varchar(80),
    add column alert_id varchar(80),
    add column case_id varchar(80),
    add column ingested_at timestamptz,
    add column last_reset_at timestamptz;

create table demo_idempotency_record (
    record_id uuid primary key,
    operation_key varchar(180) not null,
    idempotency_key varchar(64) not null,
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    result_version integer,
    result_scenario_id varchar(40),
    result_snapshot_hash varchar(80),
    result_alert_id varchar(80),
    result_timestamp timestamptz not null,
    created_at timestamptz not null,
    constraint uq_demo_idempotency_operation_key unique (operation_key, idempotency_key),
    constraint ck_demo_idempotency_result_version_non_negative
        check (result_version is null or result_version >= 0)
);

create index idx_demo_idempotency_session
    on demo_idempotency_record (demo_session_id, created_at desc);

comment on table demo_idempotency_record is '데모 변경 API의 중복 실행 방지 기록';
