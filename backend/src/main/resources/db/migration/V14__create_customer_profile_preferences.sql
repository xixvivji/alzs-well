create table customer_profile (
    customer_id varchar(80) primary key,
    display_name varchar(80) not null,
    organization varchar(120) not null,
    region varchar(40) not null,
    status varchar(30) not null,
    row_version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_customer_profile_status check (status in ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    constraint ck_customer_profile_version check (row_version >= 0),
    constraint ck_customer_profile_display_name check (btrim(display_name) <> '')
);

create table customer_preferences (
    customer_id varchar(80) primary key references customer_profile (customer_id) on delete cascade,
    sms_notification_enabled boolean not null,
    push_notification_enabled boolean not null,
    in_app_notification_enabled boolean not null,
    row_version bigint not null default 0,
    updated_at timestamptz not null,
    constraint ck_customer_preferences_version check (row_version >= 0)
);

create table customer_accessibility_settings (
    customer_id varchar(80) primary key references customer_profile (customer_id) on delete cascade,
    large_font boolean not null,
    high_contrast boolean not null,
    speech_guidance boolean not null,
    one_hand_mode boolean not null,
    row_version bigint not null default 0,
    updated_at timestamptz not null,
    constraint ck_customer_accessibility_version check (row_version >= 0)
);

create table customer_data_inventory (
    customer_id varchar(80) primary key references customer_profile (customer_id) on delete cascade,
    institution_count integer not null default 0,
    account_count integer not null default 0,
    transaction_count integer not null default 0,
    account_freshness varchar(30) not null,
    transaction_freshness varchar(30) not null,
    baseline_freshness varchar(30) not null,
    last_sync_at timestamptz,
    updated_at timestamptz not null,
    constraint ck_customer_inventory_counts check (
        institution_count >= 0 and account_count >= 0 and transaction_count >= 0
    )
);

insert into customer_profile (
    customer_id, display_name, organization, region, status, created_at, updated_at
) values (
    'SYN_CUSTOMER_FIN_MGMT_001', '이용자 001', '고령자보호센터', 'KR-11', 'ACTIVE', now(), now()
);

insert into customer_preferences (
    customer_id, sms_notification_enabled, push_notification_enabled,
    in_app_notification_enabled, updated_at
) values ('SYN_CUSTOMER_FIN_MGMT_001', false, false, true, now());

insert into customer_accessibility_settings (
    customer_id, large_font, high_contrast, speech_guidance, one_hand_mode, updated_at
) values ('SYN_CUSTOMER_FIN_MGMT_001', true, false, false, true, now());

insert into customer_data_inventory (
    customer_id, institution_count, account_count, transaction_count,
    account_freshness, transaction_freshness, baseline_freshness, last_sync_at, updated_at
) values (
    'SYN_CUSTOMER_FIN_MGMT_001', 2, 4, 42,
    'FIXED_SNAPSHOT', 'FIXED_SNAPSHOT', 'CURRENT', null, now()
);

comment on table customer_profile is '인증 주체와 연결되는 최소 비식별 고객 표시 프로필';
comment on table customer_preferences is '외부 발송 실행과 분리된 고객 서비스 환경설정';
comment on table customer_accessibility_settings is '고객별 큰글씨·고대비·음성안내 UI 설정';
comment on table customer_data_inventory is '서비스가 보유한 데이터 범위와 신선도 메타데이터';
