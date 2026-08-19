create table operational_case_activity (
    activity_id uuid primary key,
    case_id uuid not null references operational_protection_case (case_id) on delete cascade,
    activity_type varchar(40) not null,
    actor_subject varchar(80) not null,
    detail jsonb not null,
    occurred_at timestamptz not null,
    constraint ck_operational_case_activity_type check (
        activity_type in ('CASE_ASSIGNED')
    )
);

create index idx_operational_case_activity_timeline
    on operational_case_activity (case_id, occurred_at, activity_id);

create table operational_case_note (
    note_id uuid primary key,
    case_id uuid not null references operational_protection_case (case_id) on delete cascade,
    note_text varchar(500) not null,
    created_by varchar(80) not null,
    request_hash varchar(80) not null,
    idempotency_key_hash varchar(80) not null,
    integrity_hash varchar(80) not null,
    created_at timestamptz not null,
    constraint uq_operational_case_note_idempotency unique (case_id, idempotency_key_hash),
    constraint ck_operational_case_note_text check (btrim(note_text) <> '')
);

create index idx_operational_case_note_timeline
    on operational_case_note (case_id, created_at, note_id);

create or replace function reject_operational_case_history_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'operational case history is append-only';
end;
$$;

create trigger trg_operational_case_activity_append_only
before update or delete on operational_case_activity
for each row execute function reject_operational_case_history_mutation();

create trigger trg_operational_case_note_append_only
before update or delete on operational_case_note
for each row execute function reject_operational_case_history_mutation();

insert into auth_permission (permission_code, description)
values ('STAFF_CASE_NOTE', '운영형 사건 내부 메모 조회·등록');

insert into auth_role_permission (role_code, permission_code)
values ('PROTECTION_STAFF', 'STAFF_CASE_NOTE');

comment on table operational_case_activity is '운영형 사건 배정 변경의 추가 전용 타임라인';
comment on table operational_case_note is '외부 전송 없이 보존하는 운영형 사건 추가 전용 내부 메모';
