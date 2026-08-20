create table detection_policy_version (
    policy_id uuid primary key,
    version_code varchar(60) not null unique,
    status varchar(20) not null,
    description varchar(300) not null,
    rules jsonb not null,
    rules_hash varchar(80) not null,
    based_on_policy_id uuid references detection_policy_version (policy_id),
    row_version bigint not null default 0,
    created_by varchar(80) not null,
    created_at timestamptz not null,
    published_by varchar(80),
    published_at timestamptz,
    constraint ck_detection_policy_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
    constraint ck_detection_policy_rules_array check (jsonb_typeof(rules) = 'array'),
    constraint ck_detection_policy_publish_state check (
        (status = 'DRAFT' and published_at is null and published_by is null)
        or (status in ('ACTIVE', 'RETIRED') and published_at is not null and published_by is not null)
    ),
    constraint ck_detection_policy_row_version check (row_version >= 0)
);

create unique index uq_detection_policy_single_active
    on detection_policy_version ((status)) where status = 'ACTIVE';

create table detection_policy_event (
    event_id uuid primary key,
    policy_id uuid not null references detection_policy_version (policy_id),
    event_type varchar(30) not null,
    actor_subject varchar(80) not null,
    from_status varchar(20),
    to_status varchar(20) not null,
    rules_hash varchar(80) not null,
    occurred_at timestamptz not null
);

create trigger trg_detection_policy_event_append_only
before update or delete on detection_policy_event
for each row execute function reject_protected_event_mutation();

alter table synthetic_detection_run
    add column policy_version varchar(60),
    add column policy_snapshot_hash varchar(80);

insert into auth_permission (permission_code, description) values
    ('DETECTION_POLICY_READ', '탐지 정책·알고리즘 버전 조회'),
    ('DETECTION_POLICY_WRITE', '탐지 정책 초안 생성·변경·게시·복귀');

insert into auth_role_permission (role_code, permission_code) values
    ('DETECTION_ADMIN', 'DETECTION_POLICY_READ'),
    ('DETECTION_ADMIN', 'DETECTION_POLICY_WRITE');

insert into detection_policy_version (
    policy_id, version_code, status, description, rules, rules_hash,
    row_version, created_by, created_at, published_by, published_at
) values (
    '34000000-0000-4000-8000-000000000001',
    'detection-policy-v1.0.0', 'ACTIVE', '초기 결정론적 합성 탐지 정책',
    '[{"featureCode":"MISSED_RECURRING_PAYMENT","triggerDelta":0,"highDelta":0,"reasonCode":"MISSED_RECURRING_PAYMENT"},{"featureCode":"DUPLICATE_TRANSFER","triggerDelta":0,"highDelta":0,"reasonCode":"DUPLICATE_TRANSFER"},{"featureCode":"REPEATED_CONFIRMATION","triggerDelta":0,"highDelta":4,"reasonCode":"REPEATED_CONFIRMATION"}]'::jsonb,
    'sha256:d6ffccbe66c00853b68a33ab0821386daf3b656c0ed9ded0dababf5a248a093c',
    0, 'SYSTEM_MIGRATION_V34', now(), 'SYSTEM_MIGRATION_V34', now()
);

insert into detection_policy_event (
    event_id, policy_id, event_type, actor_subject, to_status, rules_hash, occurred_at
) values (
    '34000000-0000-4000-8000-000000000002',
    '34000000-0000-4000-8000-000000000001', 'POLICY_SEEDED', 'SYSTEM_MIGRATION_V34',
    'ACTIVE', 'sha256:d6ffccbe66c00853b68a33ab0821386daf3b656c0ed9ded0dababf5a248a093c', now()
);

comment on table detection_policy_version is '외부 호출 없이 적용하는 버전별 결정론적 탐지 정책';
comment on table detection_policy_event is '탐지 정책 상태 변경 append-only 감사이력';
