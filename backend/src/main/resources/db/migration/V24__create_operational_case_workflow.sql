create table operational_protection_case (
    case_id uuid primary key,
    alert_id uuid not null unique references operational_alert (alert_id) on delete cascade,
    signal_id uuid not null references customer_detection_signal (signal_id),
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    review_priority varchar(20) not null,
    task_status varchar(30) not null,
    case_version bigint not null default 1,
    assigned_team varchar(80),
    assigned_to varchar(80),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_operational_case_priority check (review_priority in ('HIGH', 'MEDIUM', 'LOW')),
    constraint ck_operational_case_status check (
        task_status in ('PENDING', 'IN_REVIEW', 'GUIDANCE_APPROVED', 'COMPLETED')
    ),
    constraint ck_operational_case_version check (case_version > 0),
    constraint ck_operational_case_assignment check (
        (assigned_to is null and assigned_team is null) or
        (assigned_to is not null and assigned_team is not null)
    )
);

create index idx_operational_case_queue
    on operational_protection_case (review_priority, created_at, case_id);

create table operational_case_review_event (
    review_event_id uuid primary key,
    case_id uuid not null references operational_protection_case (case_id) on delete cascade,
    action_code varchar(30) not null,
    previous_status varchar(30) not null,
    resulting_status varchar(30) not null,
    reviewer_subject varchar(80) not null,
    note varchar(500) not null,
    request_hash varchar(80) not null,
    idempotency_key_hash varchar(80) not null,
    created_at timestamptz not null,
    constraint uq_operational_case_review_idempotency unique (case_id, idempotency_key_hash),
    constraint ck_operational_case_review_action check (
        action_code in ('START_REVIEW', 'COMPLETE_REVIEW', 'REOPEN_REVIEW')
    )
);

create table operational_guidance_plan (
    guidance_plan_id uuid primary key,
    case_id uuid not null unique references operational_protection_case (case_id) on delete cascade,
    selected_action_codes jsonb not null,
    approved_by varchar(80) not null,
    approved_at timestamptz not null,
    delivered boolean not null default false,
    external_execution_created boolean not null default false,
    constraint ck_operational_guidance_not_executed check (
        delivered = false and external_execution_created = false
    )
);

insert into auth_role (role_code, description)
values ('PROTECTION_STAFF', '운영형 보호업무 사건을 검토하는 사설 검증 행원');

insert into auth_permission (permission_code, description) values
    ('STAFF_CASE_READ', '운영형 행원 사건큐와 상세 조회'),
    ('STAFF_CASE_ASSIGN', '운영형 사건 담당 팀·행원 배정'),
    ('STAFF_CASE_REVIEW', '운영형 사건 검토 상태전이'),
    ('STAFF_GUIDANCE_APPROVE', '외부 실행 없는 안내계획 승인');

insert into auth_role_permission (role_code, permission_code) values
    ('PROTECTION_STAFF', 'STAFF_CASE_READ'),
    ('PROTECTION_STAFF', 'STAFF_CASE_ASSIGN'),
    ('PROTECTION_STAFF', 'STAFF_CASE_REVIEW'),
    ('PROTECTION_STAFF', 'STAFF_GUIDANCE_APPROVE');

insert into operational_protection_case (
    case_id, alert_id, signal_id, customer_id, review_priority, task_status,
    case_version, created_at, updated_at
)
select gen_random_uuid(), alert_id, signal_id, customer_id, severity, 'PENDING', 1, updated_at, updated_at
  from operational_alert
 where state = 'BANK_REVIEW'
on conflict (alert_id) do nothing;

comment on table operational_protection_case is 'BANK_REVIEW 경보에서 파생된 운영형 행원 검토 사건';
comment on table operational_case_review_event is '멱등 처리되는 운영형 사건 검토 상태전이';
comment on table operational_guidance_plan is '실제 전달·금융조치 없이 승인된 안내계획';
