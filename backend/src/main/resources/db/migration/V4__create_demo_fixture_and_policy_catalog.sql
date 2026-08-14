create table demo_fixture_catalog (
    scenario_id varchar(40) primary key,
    fixture_version varchar(60) not null,
    enabled boolean not null,
    expected_connection_count integer not null check (expected_connection_count > 0),
    expected_account_count integer not null check (expected_account_count > 0),
    expected_transaction_count integer not null check (expected_transaction_count > 0),
    expected_baseline_count integer not null check (expected_baseline_count > 0),
    expected_trend_count integer not null check (expected_trend_count > 0)
);

insert into demo_fixture_catalog values
    ('MOVE_AB_001', 'move-ab-v1.0.0', true, 4, 4, 19, 3, 12);

create table protection_action_catalog (
    action_code varchar(60) primary key,
    title varchar(160) not null,
    action_status varchar(30) not null,
    execution_type varchar(30) not null check (execution_type = 'GUIDANCE_ONLY'),
    eligibility_summary varchar(400) not null,
    issuer varchar(160) not null,
    source_url varchar(500),
    effective_from date,
    checked_at date not null,
    display_order integer not null
);

insert into protection_action_catalog values
    ('SAFE_BLOCK_INFO', '금융거래 안심차단 안내', 'EXTERNAL_ONLY', 'GUIDANCE_ONLY',
     '신청 가능 여부와 세부 범위는 금융회사 확인이 필요합니다.', '금융위원회',
     'https://www.fsc.go.kr/no010101/85644', null, '2026-08-14', 1),
    ('BANK_CONSULTATION', '은행 상담 연결 안내', 'AVAILABLE', 'GUIDANCE_ONLY',
     '공식 고객센터 또는 영업점 상담 경로를 확인합니다.', '참여 금융회사 공식 고객지원',
     null, null, '2026-08-14', 2);

comment on table demo_fixture_catalog is '공개 데모에서 허용된 합성 fixture 정의와 완전성 기준';
comment on table protection_action_catalog is '실행 기능이 없는 공식 보호수단 안내 카탈로그';
