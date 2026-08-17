create table financial_institution (
    institution_id varchar(40) primary key,
    display_name varchar(80) not null,
    institution_type varchar(20) not null,
    provider_mode varchar(30) not null,
    connection_available boolean not null,
    data_as_of date not null,
    constraint ck_financial_institution_type check (institution_type in ('BANK', 'SECURITIES')),
    constraint ck_financial_institution_provider check (provider_mode = 'SYNTHETIC_PROVIDER')
);

create table financial_institution_scope (
    institution_id varchar(40) not null references financial_institution (institution_id) on delete cascade,
    scope_code varchar(40) not null,
    display_name varchar(80) not null,
    read_only boolean not null,
    primary key (institution_id, scope_code),
    constraint ck_financial_scope_read_only check (read_only)
);

create table customer_connection (
    connection_id uuid primary key,
    customer_id varchar(80) not null references customer_profile (customer_id) on delete cascade,
    institution_id varchar(40) not null references financial_institution (institution_id),
    connection_status varchar(20) not null,
    consented_at timestamptz not null,
    consent_expires_at timestamptz not null,
    last_synced_at timestamptz,
    provider_mode varchar(30) not null,
    row_version bigint not null default 0,
    unique (customer_id, institution_id),
    constraint ck_customer_connection_status check (connection_status in ('ACTIVE', 'DEGRADED', 'EXPIRED')),
    constraint ck_customer_connection_period check (consented_at < consent_expires_at),
    constraint ck_customer_connection_provider check (provider_mode = 'SYNTHETIC_PROVIDER'),
    constraint ck_customer_connection_version check (row_version >= 0)
);
create index idx_customer_connection_customer on customer_connection (customer_id, connection_status);

create table customer_connection_scope (
    connection_id uuid not null references customer_connection (connection_id) on delete cascade,
    scope_code varchar(40) not null,
    consent_status varchar(20) not null,
    primary key (connection_id, scope_code),
    constraint ck_customer_connection_scope_status check (consent_status in ('CONSENTED', 'WITHDRAWN'))
);

insert into financial_institution values
    ('SYNTHETIC_BANK', '안심은행', 'BANK', 'SYNTHETIC_PROVIDER', true, '2026-08-14'),
    ('SYNTHETIC_SECURITIES', '안심증권', 'SECURITIES', 'SYNTHETIC_PROVIDER', true, '2026-08-14');

insert into financial_institution_scope values
    ('SYNTHETIC_BANK', 'ACCOUNTS', '계좌', true),
    ('SYNTHETIC_BANK', 'TRANSACTIONS', '거래내역', true),
    ('SYNTHETIC_SECURITIES', 'INVESTMENT_ACCOUNTS', '증권계좌', true),
    ('SYNTHETIC_SECURITIES', 'POSITIONS', '보유자산', true);

insert into customer_connection values
    ('92000000-0000-0000-0000-000000000001', 'SYN_CUSTOMER_FIN_MGMT_001', 'SYNTHETIC_BANK', 'ACTIVE',
     '2026-08-01T00:00:00Z', '2027-08-01T00:00:00Z', '2026-08-14T00:00:00Z', 'SYNTHETIC_PROVIDER', 0),
    ('92000000-0000-0000-0000-000000000002', 'SYN_CUSTOMER_FIN_MGMT_001', 'SYNTHETIC_SECURITIES', 'ACTIVE',
     '2026-08-01T00:00:00Z', '2027-08-01T00:00:00Z', '2026-08-14T00:00:00Z', 'SYNTHETIC_PROVIDER', 0);

insert into customer_connection_scope
select connection_id, scope_code, 'CONSENTED'
from customer_connection join financial_institution_scope using (institution_id);

insert into auth_permission values ('FINANCIAL_CONNECTION_READ', '자신의 금융기관 연결 상태 조회');
insert into auth_role_permission values ('CUSTOMER', 'FINANCIAL_CONNECTION_READ');

update demo_fixture_catalog
set expected_connection_count = 2
where scenario_id = 'FIN_MGMT_AB_001';

comment on table financial_institution is '공식 공개 기능을 바탕으로 한 합성 금융기관 카탈로그';
comment on table customer_connection is '외부 호출 없이 고정 snapshot으로 제공하는 합성 연결 상태';
