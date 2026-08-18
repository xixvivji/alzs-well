alter table auth_session
    add column token_family_id uuid,
    add column absolute_expires_at timestamptz,
    add column compromised_at timestamptz;

update auth_session
set token_family_id = gen_random_uuid(),
    absolute_expires_at = greatest(refresh_expires_at, created_at + interval '24 hours');

alter table auth_session
    alter column token_family_id set not null,
    alter column absolute_expires_at set not null,
    add constraint ck_auth_session_absolute_expiry
        check (refresh_expires_at <= absolute_expires_at),
    add constraint ck_auth_session_compromised
        check (compromised_at is null or compromised_at >= created_at);

create index idx_auth_session_family on auth_session (token_family_id);

create table auth_refresh_token (
    token_hash char(64) primary key,
    session_id uuid not null references auth_session (session_id) on delete cascade,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    revoked_at timestamptz,
    constraint ck_auth_refresh_token_expiry check (expires_at > issued_at),
    constraint ck_auth_refresh_token_used check (used_at is null or used_at >= issued_at),
    constraint ck_auth_refresh_token_revoked check (revoked_at is null or revoked_at >= issued_at)
);

insert into auth_refresh_token (token_hash, session_id, issued_at, expires_at)
select refresh_token_hash, session_id, last_rotated_at, refresh_expires_at
from auth_session;

create index idx_auth_refresh_token_session on auth_refresh_token (session_id, issued_at desc);

create table auth_login_event (
    event_id bigserial primary key,
    login_id_hash char(64) not null,
    outcome varchar(20) not null,
    occurred_at timestamptz not null,
    constraint ck_auth_login_event_outcome check (outcome in ('SUCCEEDED', 'FAILED', 'RATE_LIMITED'))
);

create index idx_auth_login_event_rate_limit
    on auth_login_event (login_id_hash, occurred_at desc)
    where outcome in ('FAILED', 'RATE_LIMITED');

comment on table auth_refresh_token is '회전·재사용 탐지를 위한 refresh token 계열 이력';
comment on table auth_login_event is '원문 로그인 ID를 저장하지 않는 인증 시도 감사·율 제한 기록';
