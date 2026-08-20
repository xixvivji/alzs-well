create table customer_consent (
    consent_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    purpose_code varchar(50) not null,
    status varchar(20) not null,
    granted_at timestamptz not null,
    expires_at timestamptz not null,
    withdrawn_at timestamptz,
    withdrawal_reason varchar(300),
    row_version bigint not null default 1,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_customer_consent_purpose check (purpose_code in (
        'FINANCIAL_ANALYSIS', 'PROTECTION_GUIDANCE', 'TRUSTED_CONTACT_DISCLOSURE'
    )),
    constraint ck_customer_consent_status check (status in ('GRANTED', 'WITHDRAWN')),
    constraint ck_customer_consent_period check (granted_at < expires_at),
    constraint ck_customer_consent_withdrawal check (
        (status = 'GRANTED' and withdrawn_at is null and withdrawal_reason is null) or
        (status = 'WITHDRAWN' and withdrawn_at is not null and withdrawal_reason is not null)
    ),
    constraint ck_customer_consent_version check (row_version > 0)
);
create index idx_customer_consent_active on customer_consent (customer_id, expires_at)
    where status = 'GRANTED';

create table customer_consent_scope (
    consent_id uuid not null references customer_consent (consent_id) on delete cascade,
    scope_code varchar(50) not null,
    primary key (consent_id, scope_code),
    constraint ck_customer_consent_scope check (scope_code in (
        'ACCOUNT_SUMMARY', 'TRANSACTION_SUMMARY', 'BASELINE_SIGNAL',
        'PROTECTION_CASE', 'CONTACT_MINIMUM'
    ))
);

create table customer_consent_event (
    event_id uuid primary key,
    consent_id uuid not null references customer_consent (consent_id),
    event_type varchar(20) not null,
    status_snapshot varchar(20) not null,
    scope_snapshot varchar(50)[] not null,
    reason varchar(300),
    actor_id varchar(80) not null,
    occurred_at timestamptz not null,
    row_version bigint not null,
    constraint ck_customer_consent_event_type check (event_type in ('GRANTED', 'WITHDRAWN'))
);
create index idx_customer_consent_event_history on customer_consent_event (consent_id, occurred_at, event_id);

insert into auth_permission (permission_code, description) values
    ('CONSENT_READ', '자신의 목적별 동의 조회'), ('CONSENT_WRITE', '자신의 목적별 동의 등록과 철회'),
    ('CONSENT_READ_ALL', '보호업무 목적의 고객 동의 조회'),
    ('CONSENT_WRITE_ALL', '보호업무 목적의 고객 동의 등록과 철회'),
    ('DISCLOSURE_EVALUATE', '외부 전송 없는 최소정보 제공 가능성 평가');
insert into auth_role_permission (role_code, permission_code) values
    ('CUSTOMER', 'CONSENT_READ'), ('CUSTOMER', 'CONSENT_WRITE'), ('CUSTOMER', 'DISCLOSURE_EVALUATE'),
    ('PROTECTION_STAFF', 'CONSENT_READ_ALL'), ('PROTECTION_STAFF', 'CONSENT_WRITE_ALL'),
    ('PROTECTION_STAFF', 'DISCLOSURE_EVALUATE');

comment on table customer_consent is '목적·범위·유효기간·철회를 분리한 운영형 동의';
comment on table customer_consent_event is '동의 등록·철회의 추가 전용 불변 이력';
