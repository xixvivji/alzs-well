alter table demo_session
    add column demo_run_id uuid,
    add column row_version bigint not null default 0,
    add column customer_capability_hash varchar(80),
    add column staff_capability_hash varchar(80);

update demo_session
   set demo_run_id = gen_random_uuid()
 where demo_run_id is null;

alter table demo_session
    alter column demo_run_id set not null;

create table demo_run (
    demo_session_id uuid not null references demo_session (session_id) on delete cascade,
    demo_run_id uuid not null,
    reset_version integer not null check (reset_version >= 0),
    scenario_id varchar(40),
    snapshot_hash varchar(80),
    context_package_hash varchar(80),
    fixture_version varchar(60) not null,
    started_at timestamptz not null,
    ingested_at timestamptz,
    primary key (demo_session_id, demo_run_id),
    constraint uq_demo_run_reset_version unique (demo_session_id, reset_version)
);

insert into demo_run (
    demo_session_id, demo_run_id, reset_version, scenario_id, snapshot_hash,
    fixture_version, started_at, ingested_at
)
select session_id, demo_run_id, reset_version, scenario_id, snapshot_hash,
       'fin-mgmt-ab-v2.0.0', coalesce(last_reset_at, created_at), ingested_at
  from demo_session;

alter table demo_idempotency_record
    add column result_demo_run_id uuid,
    add column request_hash varchar(80);

update demo_idempotency_record r
   set result_demo_run_id = s.demo_run_id,
       request_hash = 'sha256:legacy'
  from demo_session s
 where s.session_id = r.demo_session_id;

alter table demo_idempotency_record
    alter column result_demo_run_id set not null,
    alter column request_hash set not null;

alter table decision_audit add column demo_run_id uuid;

update decision_audit a
   set demo_run_id = s.demo_run_id
  from demo_session s
 where s.session_id = a.demo_session_id;

alter table synthetic_consent add column demo_run_id uuid;
alter table synthetic_connection add column demo_run_id uuid;
alter table synthetic_connection_scope add column demo_run_id uuid;
alter table synthetic_account add column demo_run_id uuid;
alter table synthetic_transaction add column demo_run_id uuid;
alter table synthetic_baseline add column demo_run_id uuid;
alter table synthetic_baseline_reason add column demo_run_id uuid;
alter table synthetic_financial_profile add column demo_run_id uuid;
alter table synthetic_asset_trend add column demo_run_id uuid;

update synthetic_consent t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_connection t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_connection_scope t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_account t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_transaction t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_baseline t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_baseline_reason t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_financial_profile t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;
update synthetic_asset_trend t set demo_run_id = s.demo_run_id
  from demo_session s where s.session_id = t.demo_session_id;

alter table synthetic_consent alter column demo_run_id set not null;
alter table synthetic_connection alter column demo_run_id set not null;
alter table synthetic_connection_scope alter column demo_run_id set not null;
alter table synthetic_account alter column demo_run_id set not null;
alter table synthetic_transaction alter column demo_run_id set not null;
alter table synthetic_baseline alter column demo_run_id set not null;
alter table synthetic_baseline_reason alter column demo_run_id set not null;
alter table synthetic_financial_profile alter column demo_run_id set not null;
alter table synthetic_asset_trend alter column demo_run_id set not null;

alter table synthetic_connection drop constraint fk_synthetic_connection_consent;
alter table synthetic_connection_scope drop constraint fk_synthetic_connection_scope_connection;
alter table synthetic_account drop constraint fk_synthetic_account_connection;
alter table synthetic_transaction drop constraint fk_synthetic_transaction_account;
alter table synthetic_baseline_reason drop constraint fk_synthetic_baseline_reason_baseline;

alter table synthetic_baseline_reason drop constraint synthetic_baseline_reason_pkey;
alter table synthetic_transaction drop constraint synthetic_transaction_pkey;
alter table synthetic_connection_scope drop constraint synthetic_connection_scope_pkey;
alter table synthetic_account drop constraint synthetic_account_pkey;
alter table synthetic_connection drop constraint synthetic_connection_pkey;
alter table synthetic_consent drop constraint synthetic_consent_pkey;
alter table synthetic_baseline drop constraint synthetic_baseline_pkey;
alter table synthetic_financial_profile drop constraint synthetic_financial_profile_pkey;
alter table synthetic_asset_trend drop constraint synthetic_asset_trend_pkey;

alter table synthetic_consent
    add primary key (demo_session_id, demo_run_id, consent_id);
alter table synthetic_connection
    add primary key (demo_session_id, demo_run_id, connection_id);
alter table synthetic_connection_scope
    add primary key (demo_session_id, demo_run_id, connection_id, scope_code);
alter table synthetic_account
    add primary key (demo_session_id, demo_run_id, account_id);
alter table synthetic_transaction
    add primary key (demo_session_id, demo_run_id, transaction_id);
alter table synthetic_baseline
    add primary key (demo_session_id, demo_run_id, baseline_id);
alter table synthetic_baseline_reason
    add primary key (demo_session_id, demo_run_id, baseline_id, reason_code);
alter table synthetic_financial_profile
    add primary key (demo_session_id, demo_run_id, customer_id);
alter table synthetic_asset_trend
    add primary key (demo_session_id, demo_run_id, customer_id, trend_month);

alter table synthetic_consent
    add constraint fk_synthetic_consent_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade;
alter table synthetic_connection
    add constraint fk_synthetic_connection_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade,
    add constraint fk_synthetic_connection_consent_v2
        foreign key (demo_session_id, demo_run_id, consent_id)
        references synthetic_consent (demo_session_id, demo_run_id, consent_id) on delete cascade;
alter table synthetic_connection_scope
    add constraint fk_synthetic_connection_scope_connection_v2
        foreign key (demo_session_id, demo_run_id, connection_id)
        references synthetic_connection (demo_session_id, demo_run_id, connection_id) on delete cascade;
alter table synthetic_account
    add constraint fk_synthetic_account_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade,
    add constraint fk_synthetic_account_connection_v2
        foreign key (demo_session_id, demo_run_id, connection_id)
        references synthetic_connection (demo_session_id, demo_run_id, connection_id) on delete cascade;
alter table synthetic_transaction
    add constraint fk_synthetic_transaction_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade,
    add constraint fk_synthetic_transaction_account_v2
        foreign key (demo_session_id, demo_run_id, account_id)
        references synthetic_account (demo_session_id, demo_run_id, account_id) on delete cascade;
alter table synthetic_baseline
    add constraint fk_synthetic_baseline_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade;
alter table synthetic_baseline_reason
    add constraint fk_synthetic_baseline_reason_baseline_v2
        foreign key (demo_session_id, demo_run_id, baseline_id)
        references synthetic_baseline (demo_session_id, demo_run_id, baseline_id) on delete cascade;
alter table synthetic_financial_profile
    add constraint fk_synthetic_financial_profile_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade;
alter table synthetic_asset_trend
    add constraint fk_synthetic_asset_trend_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade;

create table synthetic_interaction_event (
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    interaction_id varchar(80) not null,
    customer_id varchar(80) not null,
    occurred_at timestamptz not null,
    event_type varchar(60) not null,
    subject_reference varchar(80) not null,
    source_provider varchar(40) not null check (source_provider = 'SYNTHETIC_PROVIDER'),
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, demo_run_id, interaction_id),
    constraint fk_synthetic_interaction_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade
);

create index idx_synthetic_interaction_customer_time
    on synthetic_interaction_event (demo_session_id, demo_run_id, customer_id, occurred_at);

create table synthetic_signal (
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    alert_id varchar(80) not null,
    reason_code varchar(60) not null,
    observed_count integer not null check (observed_count >= 0),
    window_seconds integer not null check (window_seconds > 0),
    algorithm_version varchar(60) not null,
    detected_at timestamptz not null,
    snapshot_hash varchar(80) not null,
    primary key (demo_session_id, demo_run_id, alert_id, reason_code),
    constraint fk_synthetic_signal_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade
);

alter table demo_fixture_catalog
    add column expected_interaction_count integer not null default 0
        check (expected_interaction_count >= 0),
    add column expected_signal_count integer not null default 0
        check (expected_signal_count >= 0);

update demo_fixture_catalog
   set scenario_id = 'FIN_MGMT_AB_001',
       fixture_version = 'fin-mgmt-ab-v2.0.0',
       expected_transaction_count = 42,
       expected_baseline_count = 3,
       expected_interaction_count = 8,
       expected_signal_count = 3
 where scenario_id = 'MOVE_AB_001';

comment on table demo_run is 'Reset으로 덮어쓰지 않는 1회 불변 데모 실행 메타데이터';
comment on column demo_run.snapshot_hash is 'T0 정규화 fixture 및 원시 이벤트 해시';
comment on column demo_run.context_package_hash is 'T1 A/B 맥락 패키지 해시';
comment on table synthetic_interaction_event is '반복 확인 등 완전 합성 고객 행동 원시 이벤트';
comment on table synthetic_signal is '원시 이벤트에서 결정론적으로 산출한 사유코드별 신호';
