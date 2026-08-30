alter table synthetic_fixture_generation_run
    drop constraint ck_fixture_generation_profile;

alter table synthetic_fixture_generation_run
    add constraint ck_fixture_generation_profile
        check(profile in ('SMOKE','DEMO','LOAD','DEV'));

create table synthetic_fixture_quality_report (
    run_id uuid not null references synthetic_fixture_generation_run(run_id),
    policy_version varchar(80) not null,
    algorithm_version varchar(80) not null,
    policy_snapshot_hash varchar(80) not null,
    status varchar(20) not null,
    policy_stable boolean not null,
    evaluated_customer_count integer not null,
    expected_signal_count integer not null,
    actual_signal_count integer not null,
    true_positive_count integer not null,
    true_negative_count integer not null,
    false_positive_count integer not null,
    false_negative_count integer not null,
    precision_score numeric(8,6) not null,
    recall_score numeric(8,6) not null,
    report_hash char(64) not null,
    evaluated_at timestamptz not null,
    primary key(run_id, policy_version, algorithm_version),
    constraint ck_fixture_quality_status check(status in ('PASSED','FAILED')),
    constraint ck_fixture_quality_passed check(
        status <> 'PASSED' or (
            policy_stable and false_positive_count = 0 and false_negative_count = 0
            and expected_signal_count = actual_signal_count
        )
    ),
    constraint ck_fixture_quality_counts check(
        evaluated_customer_count > 0
        and expected_signal_count >= 0 and actual_signal_count >= 0
        and true_positive_count >= 0 and true_negative_count >= 0
        and false_positive_count >= 0 and false_negative_count >= 0
        and true_positive_count + true_negative_count
            + false_positive_count + false_negative_count = evaluated_customer_count
    ),
    constraint ck_fixture_quality_scores check(
        precision_score between 0 and 1 and recall_score between 0 and 1
    ),
    constraint ck_fixture_quality_hash check(report_hash ~ '^[0-9a-f]{64}$')
);

create index idx_fixture_quality_status_time
    on synthetic_fixture_quality_report(status, evaluated_at desc, run_id);

create trigger trg_synthetic_fixture_quality_append_only
before update or delete on synthetic_fixture_quality_report
for each row execute function reject_protected_event_mutation();

do $$
begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_app') then
        revoke insert, update, delete on synthetic_fixture_quality_report from alzswell_app;
        grant select on synthetic_fixture_quality_report to alzswell_app;
    end if;
end $$;

comment on table synthetic_fixture_quality_report is
    '합성 fixture별 활성 탐지정책 실행 결과와 오탐·미탐을 고정한 추가 전용 품질 증적';

comment on table synthetic_fixture_generation_run is
    'SMOKE·DEMO·LOAD·DEV 결정론적 합성 데이터 생성 실행과 검증 건수';
