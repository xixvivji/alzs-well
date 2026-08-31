-- Runtime credentials must not be able to manufacture principals, roles, policy catalog
-- entries, or completed detection/AI evidence.  Keep only the mutations exercised by
-- the application services and add database guards for terminal history.

alter table alert_incident add column deferred_until timestamptz;
alter table alert_incident drop constraint ck_alert_incident_state;
alter table alert_incident add constraint ck_alert_incident_state check (state in (
    'AWAITING_CONTEXT', 'DEFERRED', 'CLOSED_NORMAL', 'PENDING_BANK_REVIEW',
    'IN_BANK_REVIEW', 'FOLLOW_UP_REQUIRED', 'GUIDANCE_PLAN_APPROVED',
    'CLOSED_FALSE_POSITIVE'
));
alter table alert_incident add constraint ck_alert_incident_deferral check (
    (state = 'DEFERRED' and deferred_until is not null)
    or (state <> 'DEFERRED' and deferred_until is null)
);

create table alert_deferral_event (
    deferral_event_id uuid primary key,
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    alert_id varchar(80) not null,
    previous_state varchar(40) not null,
    resulting_state varchar(40) not null,
    previous_incident_version bigint not null,
    resulting_incident_version bigint not null,
    deferred_until timestamptz not null,
    request_hash varchar(80) not null,
    idempotency_key_hash varchar(80) not null,
    created_at timestamptz not null,
    constraint fk_alert_deferral_event_alert
        foreign key (demo_session_id, demo_run_id, alert_id)
        references alert_incident (demo_session_id, demo_run_id, alert_id) on delete cascade,
    constraint uq_alert_deferral_event_command unique (
        demo_session_id, demo_run_id, alert_id, idempotency_key_hash
    ),
    constraint ck_alert_deferral_event_states check (
        previous_state = 'AWAITING_CONTEXT' and resulting_state = 'DEFERRED'
    ),
    constraint ck_alert_deferral_event_versions check (
        previous_incident_version > 0
        and resulting_incident_version = previous_incident_version + 1
    ),
    constraint ck_alert_deferral_event_time check (deferred_until > created_at)
);
create index idx_alert_deferral_event_history
    on alert_deferral_event(demo_session_id, demo_run_id, alert_id, created_at, deferral_event_id);
create or replace function protect_alert_deferral_event()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and pg_trigger_depth() > 1 then
        return old;
    end if;
    raise exception 'alert deferral events are append-only';
end;
$$;
create trigger trg_alert_deferral_event_append_only
before update or delete on alert_deferral_event
for each row execute function protect_alert_deferral_event();

create table detection_promotion_integrity_event (
    integrity_event_id uuid primary key,
    detection_run_id uuid not null,
    customer_id varchar(80),
    outcome varchar(20) not null,
    reason_code varchar(60) not null,
    stored_hash varchar(80),
    recomputed_hash varchar(80),
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    occurred_at timestamptz not null,
    integrity_hash varchar(80) not null,
    constraint ck_detection_promotion_integrity_outcome check (outcome = 'REJECTED'),
    constraint ck_detection_promotion_integrity_reason check (reason_code in (
        'INPUT_HASH_MISMATCH', 'DATASET_HASH_MISMATCH',
        'RESULT_HASH_MISMATCH', 'SOURCE_JSON_INVALID'
    ))
);
create index idx_detection_promotion_integrity_run
    on detection_promotion_integrity_event(detection_run_id, occurred_at, integrity_event_id);
create trigger trg_detection_promotion_integrity_append_only
before update or delete on detection_promotion_integrity_event
for each row execute function reject_protected_event_mutation();

-- SQL and Java serializers produce different byte layouts for the same jsonb value.
-- Hash the database-canonical jsonb text at the storage boundary so both ingestion
-- paths use one representation before the immutable source guard is installed.
update synthetic_detection_dataset
   set payload_hash = 'sha256:' || encode(
       digest(convert_to(payload::text, 'UTF8'), 'sha256'), 'hex'
   );
update synthetic_detection_run
   set input_payload_hash = dataset.payload_hash
  from synthetic_detection_dataset dataset
 where dataset.dataset_id = synthetic_detection_run.dataset_id;

create or replace function normalize_detection_dataset_hash()
returns trigger
language plpgsql
as $$
begin
    new.payload_hash := 'sha256:' || encode(
        digest(convert_to(new.payload::text, 'UTF8'), 'sha256'), 'hex'
    );
    return new;
end;
$$;
create trigger trg_normalize_detection_dataset_hash
before insert on synthetic_detection_dataset
for each row execute function normalize_detection_dataset_hash();

create or replace function protect_detection_dataset_transition()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'detection datasets cannot be deleted';
    end if;
    if old.dataset_id is distinct from new.dataset_id
       or old.customer_id is distinct from new.customer_id
       or old.dataset_name is distinct from new.dataset_name
       or old.payload is distinct from new.payload
       or old.payload_hash is distinct from new.payload_hash
       or old.observation_count is distinct from new.observation_count
       or old.evidence_count is distinct from new.evidence_count
       or old.created_at is distinct from new.created_at then
        raise exception 'detection dataset source identity is immutable';
    end if;
    if old.status = 'INGESTED' then
        raise exception 'ingested detection datasets are immutable';
    end if;
    if not (
        (old.status in ('DRAFT', 'INVALID') and new.status in ('VALIDATED', 'INVALID'))
        or (old.status = 'VALIDATED' and new.status = 'INGESTED')
    ) then
        raise exception 'invalid detection dataset transition';
    end if;
    if new.row_version <> old.row_version + 1 then
        raise exception 'detection dataset version must advance exactly once';
    end if;
    return new;
end;
$$;
create trigger trg_protect_detection_dataset_transition
before update or delete on synthetic_detection_dataset
for each row execute function protect_detection_dataset_transition();

create trigger trg_synthetic_detection_run_append_only
before update or delete on synthetic_detection_run
for each row execute function reject_protected_event_mutation();
create trigger trg_detection_run_promotion_append_only
before update or delete on detection_run_promotion
for each row execute function reject_protected_event_mutation();
create trigger trg_customer_baseline_snapshot_append_only
before update or delete on customer_baseline_snapshot
for each row execute function reject_protected_event_mutation();
create trigger trg_customer_baseline_feature_snapshot_append_only
before update or delete on customer_baseline_feature_snapshot
for each row execute function reject_protected_event_mutation();
create trigger trg_customer_detection_signal_append_only
before update or delete on customer_detection_signal
for each row execute function reject_protected_event_mutation();
create trigger trg_customer_signal_evidence_snapshot_append_only
before update or delete on customer_signal_evidence_snapshot
for each row execute function reject_protected_event_mutation();
create trigger trg_baseline_calculation_job_append_only
before update or delete on baseline_calculation_job
for each row execute function reject_protected_event_mutation();

create or replace function protect_ai_retrieval_terminal_run()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'AI retrieval runs cannot be deleted';
    end if;
    if old.status <> 'RUNNING' then
        raise exception 'terminal AI retrieval runs are immutable';
    end if;
    if new.status not in ('SUCCEEDED', 'FAILED') then
        raise exception 'AI retrieval runs may only enter a terminal state';
    end if;
    if (to_jsonb(new) - array['status', 'result_count', 'failure_code', 'finished_at'])
       is distinct from
       (to_jsonb(old) - array['status', 'result_count', 'failure_code', 'finished_at']) then
        raise exception 'AI retrieval run identity is immutable';
    end if;
    return new;
end;
$$;
create trigger trg_protect_ai_retrieval_terminal_run
before update or delete on ai_knowledge.retrieval_run
for each row execute function protect_ai_retrieval_terminal_run();

create index idx_ai_knowledge_chunk_content_fts_simple
    on ai_knowledge.chunk using gin (to_tsvector('simple', content));

do $$
begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_app') then
        revoke insert, update, delete on
            auth_principal, auth_role, auth_permission, auth_principal_role,
            auth_role_permission, protection_action_catalog
        from alzswell_app;

        revoke update, delete on auth_session, auth_refresh_token, auth_login_event from alzswell_app;
        grant update(
            access_token_hash, refresh_token_hash, access_expires_at, refresh_expires_at,
            last_rotated_at, revoked_at, revoke_reason, compromised_at
        ) on auth_session to alzswell_app;
        grant delete on auth_session to alzswell_app;
        grant update(used_at, revoked_at) on auth_refresh_token to alzswell_app;
        grant update(outcome) on auth_login_event to alzswell_app;
        grant delete on auth_login_event to alzswell_app;

        revoke update, delete on
            synthetic_detection_dataset, synthetic_detection_run, detection_run_promotion,
            customer_baseline_snapshot, customer_baseline_feature_snapshot,
            customer_detection_signal, customer_signal_evidence_snapshot,
            baseline_calculation_job, alert_deferral_event,
            detection_promotion_integrity_event
        from alzswell_app;
        grant update(status, validation_errors, row_version, validated_at, ingested_at)
            on synthetic_detection_dataset to alzswell_app;
        grant select, insert on alert_deferral_event to alzswell_app;
        grant select, insert on detection_promotion_integrity_event to alzswell_app;
    end if;
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_runtime') then
        revoke delete on ai_knowledge.retrieval_run from alzswell_ai_runtime;
    end if;
end $$;

comment on column alert_incident.deferred_until is
    '고객이 나중에 다시 확인하기로 선택한 합성 데모 재확인 시각';
comment on table alert_deferral_event is
    '고객의 나중에 확인 선택을 원문 없이 보존하는 추가 전용 감사이력';
comment on function protect_detection_dataset_transition() is
    '합성 데이터셋 원문과 적재 완료 상태의 변경·삭제를 차단한다';
comment on function protect_ai_retrieval_terminal_run() is
    'AI 검색 실행은 RUNNING에서 한 번만 성공 또는 실패로 종료되고 이후 변경·삭제할 수 없다';
comment on table detection_promotion_integrity_event is
    '탐지 실행 승격 전 원본·결과 hash 불일치로 거부된 시도를 추가 전용으로 보존한다';
comment on function normalize_detection_dataset_hash() is
    'SQL·Java 입력을 DB canonical jsonb SHA-256 단일 형식으로 정규화한다';
comment on index ai_knowledge.idx_ai_knowledge_chunk_content_fts_simple is
    'simple 사전 keyword 후보 검색의 full scan을 방지하는 표현식 GIN 인덱스';
