create table auth_session_event (
    event_id uuid primary key,
    principal_id uuid not null,
    actor_session_id uuid not null,
    target_session_id uuid not null,
    event_type varchar(40) not null,
    outcome varchar(20) not null,
    reason_code varchar(40) not null,
    occurred_at timestamptz not null,
    integrity_hash char(64) not null,
    constraint ck_auth_session_event_type check(event_type in ('SESSION_REVOKE_REQUESTED')),
    constraint ck_auth_session_event_outcome check(outcome in ('REVOKED','ALREADY_ENDED')),
    constraint ck_auth_session_event_reason check(reason_code in ('USER_SESSION_REVOKE')),
    constraint ck_auth_session_event_integrity check(integrity_hash ~ '^[0-9a-f]{64}$')
);
create index idx_auth_session_event_principal_time
on auth_session_event(principal_id, occurred_at desc, event_id desc);
create trigger trg_auth_session_event_append_only before update or delete on auth_session_event
for each row execute function reject_protected_event_mutation();

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  grant select,insert on auth_session_event to alzswell_app;
  revoke update,delete on auth_session_event from alzswell_app;
 end if;
end $$;

comment on table auth_session_event is '본인 선택 세션 폐기 요청의 추가 전용 보안 감사이력';
