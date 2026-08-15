create table follow_up_task (
    follow_up_id uuid primary key,
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    case_id varchar(80) not null,
    status varchar(30) not null,
    scheduled_at timestamptz not null,
    reason varchar(500) not null,
    created_by varchar(80) not null,
    external_delivery_created boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_follow_up_task_case
        foreign key (demo_session_id, demo_run_id, case_id)
        references protection_case (demo_session_id, demo_run_id, case_id) on delete cascade,
    constraint ck_follow_up_status check (status in ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    constraint ck_follow_up_reason_not_blank check (btrim(reason) <> ''),
    constraint ck_follow_up_no_external_delivery check (external_delivery_created = false)
);

create index idx_follow_up_task_schedule
    on follow_up_task (demo_session_id, demo_run_id, status, scheduled_at, follow_up_id);

comment on table follow_up_task is '전화·문자·푸시 발송 없이 내부 재확인 일정만 관리';
