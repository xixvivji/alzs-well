create table recurring_payment (
    recurring_payment_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    institution_id varchar(40) not null references financial_institution(institution_id),
    display_name varchar(80) not null,
    payment_type varchar(30) not null,
    category_code varchar(40) not null,
    cadence varchar(20) not null,
    expected_amount numeric(19, 0) not null,
    currency char(3) not null,
    next_expected_date date not null,
    grace_days integer not null,
    status varchar(20) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    reminder_enabled boolean not null default false,
    reminder_lead_days integer not null default 1,
    row_version bigint not null default 0,
    updated_at timestamptz not null,
    constraint ck_recurring_payment_type check (
        payment_type in ('UTILITY', 'SUBSCRIPTION', 'INSURANCE', 'LOAN', 'RENT')
    ),
    constraint ck_recurring_payment_cadence check (cadence in ('WEEKLY', 'MONTHLY', 'YEARLY')),
    constraint ck_recurring_payment_amount check (expected_amount >= 0),
    constraint ck_recurring_payment_currency check (currency = 'KRW'),
    constraint ck_recurring_payment_grace check (grace_days between 0 and 30),
    constraint ck_recurring_payment_status check (status in ('ACTIVE', 'PAUSED', 'ENDED')),
    constraint ck_recurring_payment_provider check (provider_mode = 'SYNTHETIC_PROVIDER'),
    constraint ck_recurring_payment_reminder check (reminder_lead_days between 0 and 30),
    constraint ck_recurring_payment_version check (row_version >= 0)
);
create index idx_recurring_payment_customer
    on recurring_payment(customer_id, status, next_expected_date, recurring_payment_id);

create table recurring_payment_occurrence (
    occurrence_id uuid primary key,
    recurring_payment_id uuid not null references recurring_payment(recurring_payment_id),
    expected_date date not null,
    observed_at timestamptz,
    amount numeric(19, 0) not null,
    occurrence_status varchar(30) not null,
    source_reference_hash char(64),
    recorded_at timestamptz not null,
    constraint ck_recurring_occurrence_amount check (amount >= 0),
    constraint ck_recurring_occurrence_status check (
        occurrence_status in ('EXPECTED', 'COMPLETED', 'MISSED', 'DUPLICATE_CANDIDATE')
    ),
    constraint ck_recurring_occurrence_observed check (
        (occurrence_status in ('EXPECTED', 'MISSED') and observed_at is null)
        or (occurrence_status in ('COMPLETED', 'DUPLICATE_CANDIDATE') and observed_at is not null)
    ),
    constraint ck_recurring_occurrence_source_hash check (
        source_reference_hash is null or source_reference_hash ~ '^[0-9a-f]{64}$'
    )
);
create index idx_recurring_occurrence_payment
    on recurring_payment_occurrence(recurring_payment_id, expected_date desc, occurrence_id);
create trigger trg_recurring_occurrence_append_only before update or delete on recurring_payment_occurrence
for each row execute function reject_protected_event_mutation();

create table recurring_payment_reminder_event (
    event_id uuid primary key,
    recurring_payment_id uuid not null references recurring_payment(recurring_payment_id),
    enabled_snapshot boolean not null,
    lead_days_snapshot integer not null,
    version_snapshot bigint not null,
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    occurred_at timestamptz not null,
    constraint ck_recurring_reminder_event_lead check (lead_days_snapshot between 0 and 30),
    constraint ck_recurring_reminder_event_version check (version_snapshot > 0)
);
create index idx_recurring_reminder_event_history
    on recurring_payment_reminder_event(recurring_payment_id, occurred_at, event_id);
create trigger trg_recurring_reminder_event_append_only before update or delete on recurring_payment_reminder_event
for each row execute function reject_protected_event_mutation();

insert into recurring_payment values
    ('94000000-0000-0000-0000-000000000001', 'SYN_CUSTOMER_FIN_MGMT_001', 'SYNTHETIC_BANK',
     '생활전기요금', 'UTILITY', 'ESSENTIAL_UTILITY', 'MONTHLY', 85000, 'KRW', '2026-09-10', 5,
     'ACTIVE', 'SYNTHETIC_PROVIDER', '2026-08-14', true, 3, 0, '2026-08-14T00:00:00Z'),
    ('94000000-0000-0000-0000-000000000002', 'SYN_CUSTOMER_FIN_MGMT_001', 'SYNTHETIC_BANK',
     '영상구독 A', 'SUBSCRIPTION', 'DIGITAL_SUBSCRIPTION', 'MONTHLY', 14900, 'KRW', '2026-09-05', 2,
     'ACTIVE', 'SYNTHETIC_PROVIDER', '2026-08-14', false, 1, 0, '2026-08-14T00:00:00Z'),
    ('94000000-0000-0000-0000-000000000003', 'SYN_CUSTOMER_FIN_MGMT_001', 'SYNTHETIC_BANK',
     '생활보장보험', 'INSURANCE', 'INSURANCE_PREMIUM', 'MONTHLY', 120000, 'KRW', '2026-08-28', 3,
     'ACTIVE', 'SYNTHETIC_PROVIDER', '2026-08-14', true, 5, 0, '2026-08-14T00:00:00Z');

insert into recurring_payment_occurrence values
    ('94100000-0000-0000-0000-000000000001', '94000000-0000-0000-0000-000000000001', '2026-05-10', '2026-05-10T01:00:00Z', 85000, 'COMPLETED', repeat('1',64), '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000002', '94000000-0000-0000-0000-000000000001', '2026-06-10', null, 85000, 'MISSED', null, '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000003', '94000000-0000-0000-0000-000000000001', '2026-07-10', null, 85000, 'MISSED', null, '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000004', '94000000-0000-0000-0000-000000000001', '2026-08-10', null, 85000, 'MISSED', null, '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000005', '94000000-0000-0000-0000-000000000001', '2026-09-10', null, 85000, 'EXPECTED', null, '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000006', '94000000-0000-0000-0000-000000000002', '2026-07-05', '2026-07-05T02:00:00Z', 14900, 'COMPLETED', repeat('2',64), '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000007', '94000000-0000-0000-0000-000000000002', '2026-08-05', '2026-08-05T02:00:00Z', 14900, 'COMPLETED', repeat('3',64), '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000008', '94000000-0000-0000-0000-000000000002', '2026-08-05', '2026-08-05T02:04:00Z', 14900, 'DUPLICATE_CANDIDATE', repeat('4',64), '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000009', '94000000-0000-0000-0000-000000000002', '2026-09-05', null, 14900, 'EXPECTED', null, '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000010', '94000000-0000-0000-0000-000000000003', '2026-07-28', '2026-07-28T03:00:00Z', 120000, 'COMPLETED', repeat('5',64), '2026-08-14T00:00:00Z'),
    ('94100000-0000-0000-0000-000000000011', '94000000-0000-0000-0000-000000000003', '2026-08-28', null, 120000, 'EXPECTED', null, '2026-08-14T00:00:00Z');

insert into auth_permission(permission_code, description) values
    ('RECURRING_PAYMENT_READ', '본인의 합성 정기납부·구독 조회'),
    ('RECURRING_PAYMENT_WRITE', '본인의 인앱 정기납부 확인 알림 설정');
insert into auth_role_permission(role_code, permission_code) values
    ('CUSTOMER', 'RECURRING_PAYMENT_READ'),
    ('CUSTOMER', 'RECURRING_PAYMENT_WRITE');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke insert, update, delete on recurring_payment from alzswell_app;
        grant update(reminder_enabled, reminder_lead_days, row_version, updated_at)
            on recurring_payment to alzswell_app;
        revoke insert, update, delete on recurring_payment_occurrence from alzswell_app;
        revoke update, delete on recurring_payment_reminder_event from alzswell_app;
    end if;
end $$;

comment on table recurring_payment is '실제 결제 실행 없이 제공하는 고객별 합성 정기납부·구독 read model과 인앱 알림 설정';
comment on table recurring_payment_occurrence is '예상·완료·미발생·중복 후보를 보존하는 추가 전용 합성 발생 이력';
comment on table recurring_payment_reminder_event is '정기납부 인앱 확인 알림 설정 변경의 추가 전용 감사이력';
