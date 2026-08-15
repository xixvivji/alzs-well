create table case_note (
    note_id uuid primary key,
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    case_id varchar(80) not null,
    case_version bigint not null,
    note_text varchar(500) not null,
    created_by varchar(80) not null,
    request_hash varchar(80) not null,
    idempotency_key_hash varchar(80) not null,
    created_at timestamptz not null,
    constraint fk_case_note_case
        foreign key (demo_session_id, demo_run_id, case_id)
        references protection_case (demo_session_id, demo_run_id, case_id) on delete cascade,
    constraint ck_case_note_version_positive check (case_version > 0),
    constraint ck_case_note_text_not_blank check (btrim(note_text) <> '')
);

create index idx_case_note_timeline
    on case_note (demo_session_id, demo_run_id, case_id, created_at, note_id);

create or replace function reject_case_note_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'case_note is append-only';
end;
$$;

create trigger trg_case_note_append_only
before update or delete on case_note
for each row execute function reject_case_note_mutation();

comment on table case_note is '외부 전송 없이 demoRun 내부에 보존되는 append-only 행원 메모';
