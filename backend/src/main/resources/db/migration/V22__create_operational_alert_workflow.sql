create table operational_alert (
    alert_id uuid primary key,
    signal_id uuid not null unique references customer_detection_signal (signal_id),
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    state varchar(30) not null,
    severity varchar(20) not null,
    reason_code varchar(60) not null,
    alert_version bigint not null default 1,
    deferred_until timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_operational_alert_state check (
        state in ('AWAITING_CONTEXT', 'DEFERRED', 'CLOSED_NORMAL', 'BANK_REVIEW')
    ),
    constraint ck_operational_alert_severity check (severity in ('LOW', 'MEDIUM', 'HIGH')),
    constraint ck_operational_alert_version check (alert_version > 0),
    constraint ck_operational_alert_defer check (
        (state = 'DEFERRED' and deferred_until is not null) or
        (state <> 'DEFERRED' and deferred_until is null)
    )
);

create index idx_operational_alert_customer_state
    on operational_alert (customer_id, state, created_at desc, alert_id desc);

create table operational_alert_context_event (
    context_event_id uuid primary key,
    alert_id uuid not null references operational_alert (alert_id) on delete cascade,
    response_code varchar(30) not null,
    previous_state varchar(30) not null,
    resulting_state varchar(30) not null,
    request_hash varchar(80) not null,
    idempotency_key_hash varchar(80) not null,
    created_at timestamptz not null,
    constraint uq_operational_alert_context_idempotency unique (alert_id, idempotency_key_hash),
    constraint ck_operational_alert_response check (
        response_code in ('EXPECTED_CHANGE', 'UNRECOGNIZED', 'NOT_SURE')
    )
);

create table operational_alert_audit_event (
    audit_event_id uuid primary key,
    alert_id uuid not null references operational_alert (alert_id) on delete cascade,
    event_type varchar(40) not null,
    previous_state varchar(30),
    resulting_state varchar(30) not null,
    detail jsonb not null,
    integrity_hash varchar(80) not null,
    created_at timestamptz not null
);

create index idx_operational_alert_audit
    on operational_alert_audit_event (alert_id, created_at, audit_event_id);

insert into auth_permission (permission_code, description) values
    ('ALERT_READ', '자신의 운영형 경보와 감사이력 조회'),
    ('ALERT_RESPOND', '자신의 운영형 경보에 맥락 응답 또는 확인 연기'),
    ('ALERT_READ_ALL', '모든 고객의 운영형 경보와 감사이력 조회'),
    ('ALERT_RESPOND_ALL', '모든 고객의 운영형 경보에 맥락 응답 또는 확인 연기');

insert into auth_role_permission (role_code, permission_code) values
    ('CUSTOMER', 'ALERT_READ'),
    ('CUSTOMER', 'ALERT_RESPOND'),
    ('DETECTION_ADMIN', 'ALERT_READ_ALL'),
    ('DETECTION_ADMIN', 'ALERT_RESPOND_ALL');

insert into operational_alert (
    alert_id, signal_id, customer_id, state, severity, reason_code, created_at, updated_at
)
select gen_random_uuid(), signal_id, customer_id, 'AWAITING_CONTEXT', severity, reason_code,
       detected_at, detected_at
  from customer_detection_signal;

insert into operational_alert_audit_event (
    audit_event_id, alert_id, event_type, previous_state, resulting_state,
    detail, integrity_hash, created_at
)
select gen_random_uuid(), alert_id, 'ALERT_CREATED', null, state,
       jsonb_build_object('signalId', signal_id, 'syntheticData', true),
       'seed:' || md5(alert_id::text || '|ALERT_CREATED|' || state), created_at
  from operational_alert;

comment on table operational_alert is '변화신호에서 파생된 운영형 고객 경보 상태의 단일 기준';
comment on table operational_alert_context_event is '원문 멱등키를 저장하지 않는 고객 생활맥락 응답';
comment on table operational_alert_audit_event is '운영형 경보 상태변경의 추가 전용 감사이력';
