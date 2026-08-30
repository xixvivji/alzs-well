create table operational_case_follow_up (
    follow_up_id uuid primary key,
    case_id uuid not null references operational_protection_case (case_id) on delete cascade,
    follow_up_type varchar(40) not null,
    status varchar(20) not null,
    scheduled_at timestamptz not null,
    purpose varchar(300) not null,
    outcome varchar(500),
    follow_up_version bigint not null default 1,
    idempotency_key_hash varchar(80) not null,
    request_hash varchar(80) not null,
    created_by varchar(80) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_operational_follow_up_idempotency unique (case_id, idempotency_key_hash),
    constraint ck_operational_follow_up_type check (
        follow_up_type in ('CUSTOMER_RECHECK', 'BRANCH_CONSULTATION', 'INTERNAL_REVIEW')
    ),
    constraint ck_operational_follow_up_status check (status in ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    constraint ck_operational_follow_up_version check (follow_up_version > 0),
    constraint ck_operational_follow_up_outcome check (
        (status = 'SCHEDULED' and outcome is null) or
        (status in ('COMPLETED', 'CANCELLED') and outcome is not null and btrim(outcome) <> '')
    )
);

create index idx_operational_follow_up_case_time
    on operational_case_follow_up (case_id, scheduled_at, follow_up_id);

create table operational_case_follow_up_event (
    follow_up_event_id uuid primary key,
    follow_up_id uuid not null references operational_case_follow_up (follow_up_id) on delete cascade,
    case_id uuid not null references operational_protection_case (case_id) on delete cascade,
    event_type varchar(30) not null,
    previous_status varchar(20),
    resulting_status varchar(20) not null,
    actor_subject varchar(80) not null,
    detail jsonb not null,
    integrity_hash varchar(80) not null,
    created_at timestamptz not null,
    constraint ck_operational_follow_up_event_type check (
        event_type in ('FOLLOW_UP_CREATED', 'FOLLOW_UP_RESCHEDULED', 'FOLLOW_UP_COMPLETED', 'FOLLOW_UP_CANCELLED')
    )
);

create index idx_operational_follow_up_event_timeline
    on operational_case_follow_up_event (case_id, created_at, follow_up_event_id);

create trigger trg_operational_follow_up_event_append_only
before update or delete on operational_case_follow_up_event
for each row execute function reject_operational_case_history_mutation();

insert into auth_permission (permission_code, description)
values ('STAFF_FOLLOW_UP', '운영형 사건 후속 일정 조회·등록·상태 변경');

insert into auth_role_permission (role_code, permission_code)
values ('PROTECTION_STAFF', 'STAFF_FOLLOW_UP');

comment on table operational_case_follow_up is '실제 연락 실행 없이 관리하는 운영형 사건 내부 후속 일정';
comment on table operational_case_follow_up_event is '후속 일정 변경의 추가 전용 감사이력';
