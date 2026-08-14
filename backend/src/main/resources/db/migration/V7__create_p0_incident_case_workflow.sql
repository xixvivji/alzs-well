create table alert_incident (
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    alert_id varchar(80) not null,
    customer_id varchar(80) not null,
    state varchar(40) not null,
    incident_version bigint not null default 1,
    pre_decision varchar(60) not null default 'NEEDS_CONTEXT',
    post_decision varchar(60),
    response_code varchar(60),
    demo_branch_code varchar(80),
    t1_context_evidence jsonb,
    trusted_contact_gate jsonb not null default '{"gateEvaluated":false,"consentSnapshotId":"CONSENT_TRUSTED_CONTACT_001","consentStatus":"NOT_GRANTED","recipientAccepted":false,"triggerMatched":false,"fieldScopeMatched":false,"validityMatched":false,"deliveryEnabled":false,"resultCode":null,"dispatchAttempted":false,"externalDeliveryRequested":false,"externalDeliveryCreated":false}'::jsonb,
    alert_snapshot_at timestamptz not null,
    context_observed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (demo_session_id, demo_run_id, alert_id),
    constraint fk_alert_incident_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade,
    constraint ck_alert_incident_version_positive check (incident_version > 0),
    constraint ck_alert_incident_state check (state in (
        'AWAITING_CONTEXT', 'CLOSED_NORMAL', 'PENDING_BANK_REVIEW',
        'IN_BANK_REVIEW', 'FOLLOW_UP_REQUIRED', 'GUIDANCE_PLAN_APPROVED',
        'CLOSED_FALSE_POSITIVE'
    ))
);

create index idx_alert_incident_customer_state
    on alert_incident (demo_session_id, demo_run_id, customer_id, state, alert_snapshot_at desc);

create table context_event (
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    context_event_id varchar(80) not null,
    alert_id varchar(80) not null,
    response_code varchar(60) not null,
    demo_branch_code varchar(80) not null,
    structural_evidence_matched boolean not null,
    context_types jsonb not null default '[]'::jsonb,
    context_evidence_ids jsonb not null default '[]'::jsonb,
    context_evidence_refs jsonb not null default '[]'::jsonb,
    request_hash varchar(80) not null,
    idempotency_key_hash varchar(80) not null,
    observed_at timestamptz not null,
    created_at timestamptz not null,
    primary key (demo_session_id, demo_run_id, context_event_id),
    constraint uq_context_event_alert unique (demo_session_id, demo_run_id, alert_id),
    constraint fk_context_event_alert
        foreign key (demo_session_id, demo_run_id, alert_id)
        references alert_incident (demo_session_id, demo_run_id, alert_id) on delete cascade
);

create table protection_case (
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    case_id varchar(80) not null,
    alert_id varchar(80) not null,
    customer_id varchar(80) not null,
    review_priority varchar(20) not null,
    review_task_status varchar(40) not null,
    case_version bigint not null default 1,
    customer_response_code varchar(60) not null,
    assigned_to varchar(80),
    latest_note varchar(500),
    follow_up_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (demo_session_id, demo_run_id, case_id),
    constraint uq_protection_case_alert unique (demo_session_id, demo_run_id, alert_id),
    constraint fk_protection_case_alert
        foreign key (demo_session_id, demo_run_id, alert_id)
        references alert_incident (demo_session_id, demo_run_id, alert_id) on delete cascade,
    constraint ck_protection_case_version_positive check (case_version > 0),
    constraint ck_protection_case_priority check (review_priority in ('HIGH', 'MEDIUM', 'LOW')),
    constraint ck_protection_case_task_status check (review_task_status in (
        'PENDING', 'IN_REVIEW', 'FOLLOW_UP', 'GUIDANCE_APPROVED', 'COMPLETED'
    ))
);

create index idx_protection_case_queue
    on protection_case (demo_session_id, demo_run_id, review_priority, created_at, case_id);

create table guidance_plan (
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    case_id varchar(80) not null,
    plan_version bigint not null,
    status varchar(30) not null,
    selected_action_codes jsonb not null,
    approved_by varchar(80) not null,
    approved_at timestamptz not null,
    delivered boolean not null default false,
    delivered_at timestamptz,
    external_execution_created boolean not null default false,
    primary key (demo_session_id, demo_run_id, case_id),
    constraint fk_guidance_plan_case
        foreign key (demo_session_id, demo_run_id, case_id)
        references protection_case (demo_session_id, demo_run_id, case_id) on delete cascade,
    constraint ck_guidance_plan_version_positive check (plan_version > 0),
    constraint ck_guidance_plan_status check (status = 'APPROVED'),
    constraint ck_guidance_plan_not_delivered
        check (delivered = false and delivered_at is null and external_execution_created = false)
);

create table workflow_command_result (
    command_record_id uuid primary key,
    demo_session_id uuid not null,
    demo_run_id uuid not null,
    capability_hash varchar(80) not null,
    capability_role varchar(40) not null,
    http_method varchar(10) not null,
    operation_path varchar(300) not null,
    idempotency_key_hash varchar(80) not null,
    request_hash varchar(80) not null,
    response_code varchar(80) not null,
    response_message varchar(300) not null,
    response_payload jsonb not null,
    created_at timestamptz not null,
    constraint fk_workflow_command_run
        foreign key (demo_session_id, demo_run_id)
        references demo_run (demo_session_id, demo_run_id) on delete cascade,
    constraint uq_workflow_command_scope unique (
        demo_session_id, demo_run_id, capability_hash, capability_role,
        http_method, operation_path, idempotency_key_hash
    )
);

create or replace function initialize_p0_alert_incident()
returns trigger
language plpgsql
as $$
begin
    insert into alert_incident (
        demo_session_id, demo_run_id, alert_id, customer_id, state,
        incident_version, pre_decision, alert_snapshot_at, created_at, updated_at
    ) values (
        new.demo_session_id, new.demo_run_id, new.alert_id,
        'SYN_CUSTOMER_FIN_MGMT_001', 'AWAITING_CONTEXT',
        1, 'NEEDS_CONTEXT', new.detected_at, new.detected_at, new.detected_at
    ) on conflict (demo_session_id, demo_run_id, alert_id) do nothing;
    return new;
end;
$$;

create trigger trg_initialize_p0_alert_incident
after insert on synthetic_signal
for each row execute function initialize_p0_alert_incident();

insert into alert_incident (
    demo_session_id, demo_run_id, alert_id, customer_id, state,
    incident_version, pre_decision, alert_snapshot_at, created_at, updated_at
)
select demo_session_id, demo_run_id, alert_id, 'SYN_CUSTOMER_FIN_MGMT_001',
       'AWAITING_CONTEXT', 1, 'NEEDS_CONTEXT', min(detected_at), min(detected_at), min(detected_at)
  from synthetic_signal
 group by demo_session_id, demo_run_id, alert_id
on conflict (demo_session_id, demo_run_id, alert_id) do nothing;

comment on table alert_incident is 'demoRun 범위에서 사건 생명주기의 단일 상태 기준';
comment on table context_event is '고객 응답과 서버 검증 T1 근거를 T0와 분리해 보존';
comment on table protection_case is '행원 배정·검토 작업상태와 낙관적 caseVersion';
comment on table guidance_plan is '실제 금융 실행이나 고객 전달이 없는 상담 안내계획 승인';
comment on table workflow_command_result is 'capability/run/requestHash 범위의 변경 명령 재현 결과';
