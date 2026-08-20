create table customer_protection_enrollment (
    enrollment_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    action_code varchar(60) not null references protection_action_catalog (action_code),
    institution_id varchar(40) not null references financial_institution (institution_id),
    enrollment_status varchar(24) not null,
    observed_as_of date not null,
    provider_mode varchar(30) not null,
    unique (customer_id, action_code, institution_id),
    constraint ck_protection_enrollment_status check (enrollment_status in ('ENROLLED', 'NOT_ENROLLED', 'UNKNOWN')),
    constraint ck_protection_enrollment_provider check (provider_mode = 'SYNTHETIC_PROVIDER')
);

insert into customer_protection_enrollment values
    ('96000000-0000-0000-0000-000000000001', 'SYN_CUSTOMER_FIN_MGMT_001',
     'SAFE_BLOCK_INFO', 'SYNTHETIC_BANK', 'NOT_ENROLLED', '2026-08-14', 'SYNTHETIC_PROVIDER'),
    ('96000000-0000-0000-0000-000000000002', 'SYN_CUSTOMER_FIN_MGMT_001',
     'BANK_CONSULTATION', 'SYNTHETIC_BANK', 'UNKNOWN', '2026-08-14', 'SYNTHETIC_PROVIDER');

insert into auth_permission (permission_code, description) values
    ('PROTECTION_ACTION_READ', '공식 보호수단 안내 카탈로그 조회'),
    ('PROTECTION_ACTION_EVALUATE', '실행 없는 보호수단 안내 가능성 평가'),
    ('PROTECTION_ENROLLMENT_READ', '자신의 합성 보호수단 가입상태 조회'),
    ('PROTECTION_ENROLLMENT_READ_ALL', '보호업무 목적의 합성 가입상태 조회');

insert into auth_role_permission (role_code, permission_code) values
    ('CUSTOMER', 'PROTECTION_ACTION_READ'), ('CUSTOMER', 'PROTECTION_ACTION_EVALUATE'),
    ('CUSTOMER', 'PROTECTION_ENROLLMENT_READ'), ('PROTECTION_STAFF', 'PROTECTION_ACTION_READ'),
    ('PROTECTION_STAFF', 'PROTECTION_ACTION_EVALUATE'),
    ('PROTECTION_STAFF', 'PROTECTION_ENROLLMENT_READ_ALL'),
    ('DETECTION_ADMIN', 'PROTECTION_ACTION_READ'), ('DETECTION_ADMIN', 'PROTECTION_ACTION_EVALUATE');

comment on table customer_protection_enrollment is
    '외부 금융회사 호출 없이 고정 snapshot으로 제공하는 합성 보호수단 가입상태';
