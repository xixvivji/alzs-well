create table customer_account_snapshot (
    account_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    connection_id uuid not null references customer_connection(connection_id),
    institution_id varchar(40) not null references financial_institution(institution_id),
    account_type varchar(30) not null,
    display_name varchar(80) not null,
    masked_account_number varchar(40) not null,
    account_status varchar(20) not null,
    currency char(3) not null,
    current_balance numeric(19, 0) not null,
    available_balance numeric(19, 0) not null,
    balance_as_of timestamptz not null,
    interest_type varchar(20) not null,
    annual_interest_rate numeric(7, 4) not null,
    accrued_interest numeric(19, 0) not null,
    interest_as_of date not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_customer_account_type check (
        account_type in ('CHECKING', 'SAVINGS', 'DEPOSIT', 'INVESTMENT_CASH')
    ),
    constraint ck_customer_account_mask check (
        masked_account_number like '%*%' and masked_account_number !~ '[0-9]{6,}'
    ),
    constraint ck_customer_account_status check (account_status in ('ACTIVE', 'DORMANT', 'CLOSED')),
    constraint ck_customer_account_currency check (currency = 'KRW'),
    constraint ck_customer_account_balance check (
        current_balance >= 0 and available_balance >= 0 and available_balance <= current_balance
    ),
    constraint ck_customer_account_interest check (
        interest_type in ('NONE', 'FIXED', 'VARIABLE')
        and annual_interest_rate >= 0 and accrued_interest >= 0
    ),
    constraint ck_customer_account_provider check (provider_mode = 'SYNTHETIC_PROVIDER'),
    constraint ck_customer_account_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_customer_account_owner
    on customer_account_snapshot(customer_id, account_status, institution_id, account_id);
create trigger trg_customer_account_snapshot_append_only before update or delete on customer_account_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_account_balance_snapshot (
    account_id uuid not null references customer_account_snapshot(account_id),
    balance_date date not null,
    current_balance numeric(19, 0) not null,
    available_balance numeric(19, 0) not null,
    snapshot_hash char(64) not null,
    primary key(account_id, balance_date),
    constraint ck_account_balance_history_amount check (
        current_balance >= 0 and available_balance >= 0 and available_balance <= current_balance
    ),
    constraint ck_account_balance_history_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create trigger trg_customer_account_balance_append_only before update or delete on customer_account_balance_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_account_restriction_snapshot (
    restriction_id uuid primary key,
    account_id uuid not null references customer_account_snapshot(account_id),
    restriction_code varchar(60) not null,
    title varchar(120) not null,
    description varchar(300) not null,
    status varchar(20) not null,
    effective_from date not null,
    effective_to date,
    snapshot_hash char(64) not null,
    constraint ck_account_restriction_status check (status in ('ACTIVE', 'RESOLVED')),
    constraint ck_account_restriction_period check (effective_to is null or effective_to >= effective_from),
    constraint ck_account_restriction_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_customer_account_restriction
    on customer_account_restriction_snapshot(account_id, status, effective_from, restriction_id);
create trigger trg_customer_account_restriction_append_only before update or delete on customer_account_restriction_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_account_statement_snapshot (
    statement_id uuid primary key,
    account_id uuid not null references customer_account_snapshot(account_id),
    period_from date not null,
    period_to date not null,
    opening_balance numeric(19, 0) not null,
    closing_balance numeric(19, 0) not null,
    total_inflow numeric(19, 0) not null,
    total_outflow numeric(19, 0) not null,
    transaction_count integer not null,
    generated_at timestamptz not null,
    snapshot_hash char(64) not null,
    constraint ck_account_statement_period check (period_to >= period_from),
    constraint ck_account_statement_amount check (
        opening_balance >= 0 and closing_balance >= 0 and total_inflow >= 0 and total_outflow >= 0
    ),
    constraint ck_account_statement_count check (transaction_count >= 0),
    constraint ck_account_statement_hash check (snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(account_id, period_from, period_to)
);
create index idx_customer_account_statement
    on customer_account_statement_snapshot(account_id, period_to desc, statement_id);
create trigger trg_customer_account_statement_append_only before update or delete on customer_account_statement_snapshot
for each row execute function reject_protected_event_mutation();

insert into customer_account_snapshot values
    ('95000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','92000000-0000-0000-0000-000000000001','SYNTHETIC_BANK','CHECKING','생활 입출금','110-***-**01','ACTIVE','KRW',18450000,18000000,'2026-08-14T00:00:00Z','VARIABLE',0.1000,1200,'2026-08-14','SYNTHETIC_PROVIDER','2026-08-14',repeat('1',64)),
    ('95000000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','92000000-0000-0000-0000-000000000001','SYNTHETIC_BANK','SAVINGS','생활비 통장','110-***-**02','ACTIVE','KRW',3200000,3200000,'2026-08-14T00:00:00Z','VARIABLE',0.2000,900,'2026-08-14','SYNTHETIC_PROVIDER','2026-08-14',repeat('2',64)),
    ('95000000-0000-0000-0000-000000000003','SYN_CUSTOMER_FIN_MGMT_001','92000000-0000-0000-0000-000000000001','SYNTHETIC_BANK','DEPOSIT','안심 정기예금','110-***-**03','ACTIVE','KRW',20000000,0,'2026-08-14T00:00:00Z','FIXED',3.2000,320000,'2026-08-14','SYNTHETIC_PROVIDER','2026-08-14',repeat('3',64)),
    ('95000000-0000-0000-0000-000000000004','SYN_CUSTOMER_FIN_MGMT_001','92000000-0000-0000-0000-000000000002','SYNTHETIC_SECURITIES','INVESTMENT_CASH','투자 예수금','301-***-**04','ACTIVE','KRW',8000000,7500000,'2026-08-14T00:00:00Z','NONE',0.0000,0,'2026-08-14','SYNTHETIC_PROVIDER','2026-08-14',repeat('4',64));

insert into customer_account_balance_snapshot values
    ('95000000-0000-0000-0000-000000000001','2026-06-30',17100000,16800000,repeat('5',64)),
    ('95000000-0000-0000-0000-000000000001','2026-07-31',17900000,17500000,repeat('6',64)),
    ('95000000-0000-0000-0000-000000000001','2026-08-14',18450000,18000000,repeat('7',64)),
    ('95000000-0000-0000-0000-000000000002','2026-06-30',2900000,2900000,repeat('8',64)),
    ('95000000-0000-0000-0000-000000000002','2026-07-31',3100000,3100000,repeat('9',64)),
    ('95000000-0000-0000-0000-000000000002','2026-08-14',3200000,3200000,repeat('a',64)),
    ('95000000-0000-0000-0000-000000000003','2026-06-30',20000000,0,repeat('b',64)),
    ('95000000-0000-0000-0000-000000000003','2026-07-31',20000000,0,repeat('c',64)),
    ('95000000-0000-0000-0000-000000000003','2026-08-14',20000000,0,repeat('d',64)),
    ('95000000-0000-0000-0000-000000000004','2026-06-30',7200000,7000000,repeat('e',64)),
    ('95000000-0000-0000-0000-000000000004','2026-07-31',7700000,7300000,repeat('f',64)),
    ('95000000-0000-0000-0000-000000000004','2026-08-14',8000000,7500000,repeat('0',64));

insert into customer_account_restriction_snapshot values
    ('95100000-0000-0000-0000-000000000001','95000000-0000-0000-0000-000000000003',
     'MATURITY_WITHDRAWAL_ONLY','만기 전 출금 제한','합성 정기예금은 만기 전 가용잔액이 0원인 조건으로 표시됩니다.',
     'ACTIVE','2026-08-01',null,repeat('1',64));

insert into customer_account_statement_snapshot values
    ('95200000-0000-0000-0000-000000000001','95000000-0000-0000-0000-000000000001','2026-07-01','2026-07-31',17100000,17900000,4200000,3400000,18,'2026-08-01T00:00:00Z',repeat('2',64)),
    ('95200000-0000-0000-0000-000000000002','95000000-0000-0000-0000-000000000001','2026-08-01','2026-08-14',17900000,18450000,2100000,1550000,9,'2026-08-14T00:00:00Z',repeat('3',64)),
    ('95200000-0000-0000-0000-000000000003','95000000-0000-0000-0000-000000000002','2026-08-01','2026-08-14',3100000,3200000,400000,300000,6,'2026-08-14T00:00:00Z',repeat('4',64)),
    ('95200000-0000-0000-0000-000000000004','95000000-0000-0000-0000-000000000003','2026-08-01','2026-08-14',20000000,20000000,0,0,0,'2026-08-14T00:00:00Z',repeat('5',64)),
    ('95200000-0000-0000-0000-000000000005','95000000-0000-0000-0000-000000000004','2026-08-01','2026-08-14',7700000,8000000,500000,200000,4,'2026-08-14T00:00:00Z',repeat('6',64));

insert into auth_permission(permission_code,description) values
    ('ACCOUNT_READ','본인의 마스킹된 합성 계좌·잔액·명세 조회');
insert into auth_role_permission(role_code,permission_code) values ('CUSTOMER','ACCOUNT_READ');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke insert, update, delete on customer_account_snapshot,
            customer_account_balance_snapshot, customer_account_restriction_snapshot,
            customer_account_statement_snapshot from alzswell_app;
    end if;
end $$;

comment on table customer_account_snapshot is '안심은행·안심증권 고정 기준일의 마스킹된 고객 계좌 합성 snapshot';
comment on table customer_account_balance_snapshot is '계좌별 기간 잔액 추세를 위한 추가 전용 합성 snapshot';
comment on table customer_account_restriction_snapshot is '실행 없이 표시만 하는 계좌 조건·제약 합성 snapshot';
comment on table customer_account_statement_snapshot is '파일 다운로드 없이 요약만 제공하는 추가 전용 합성 거래명세 snapshot';
