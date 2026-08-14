create table demo_session (
    session_id uuid primary key,
    scenario_seed bigint not null,
    expires_at timestamptz not null,
    reset_version integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_demo_session_reset_version_non_negative check (reset_version >= 0),
    constraint ck_demo_session_expiry_after_creation check (expires_at > created_at)
);

create index idx_demo_session_expires_at
    on demo_session (expires_at);

create table decision_audit (
    audit_id uuid primary key,
    demo_session_id uuid references demo_session (session_id) on delete cascade,
    trace_id varchar(64) not null,
    event_type varchar(80) not null,
    actor_type varchar(40) not null,
    policy_version varchar(40),
    algorithm_version varchar(40),
    schema_version varchar(40),
    event_payload jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null
);

create index idx_decision_audit_session_occurred_at
    on decision_audit (demo_session_id, occurred_at desc);

create index idx_decision_audit_trace_id
    on decision_audit (trace_id);

comment on table demo_session is '완전 합성데이터 기반 익명 데모 세션';
comment on table decision_audit is '상태전이, 정책 판단, 직원행위의 추적 가능한 감사 이벤트';
