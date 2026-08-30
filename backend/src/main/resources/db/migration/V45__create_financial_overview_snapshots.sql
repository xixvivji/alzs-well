create table customer_liability_snapshot (
    liability_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    institution_id varchar(40) not null references financial_institution(institution_id),
    liability_type varchar(30) not null,
    display_name varchar(80) not null,
    masked_reference varchar(40) not null,
    outstanding_amount numeric(19,0) not null,
    scheduled_amount numeric(19,0) not null,
    annual_interest_rate numeric(7,4) not null,
    next_due_date date not null,
    status varchar(20) not null,
    currency char(3) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_liability_type check (liability_type in ('LOAN','CARD_BILL')),
    constraint ck_liability_mask check (masked_reference like '%*%' and masked_reference !~ '[0-9]{6,}'),
    constraint ck_liability_amount check (outstanding_amount >= 0 and scheduled_amount >= 0),
    constraint ck_liability_rate check (annual_interest_rate >= 0),
    constraint ck_liability_status check (status in ('ACTIVE','PAID','CLOSED')),
    constraint ck_liability_currency check (currency='KRW'),
    constraint ck_liability_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_liability_owner on customer_liability_snapshot(customer_id,status,next_due_date,liability_id);
create trigger trg_liability_snapshot_append_only before update or delete on customer_liability_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_asset_calendar_snapshot (
    event_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    institution_id varchar(40) references financial_institution(institution_id),
    account_id uuid references customer_account_snapshot(account_id),
    event_type varchar(30) not null,
    title varchar(100) not null,
    scheduled_date date not null,
    direction varchar(10) not null,
    expected_amount numeric(19,0) not null,
    currency char(3) not null,
    certainty varchar(20) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_asset_calendar_type check (event_type in ('SALARY','INTEREST','MATURITY')),
    constraint ck_asset_calendar_direction check (direction in ('INFLOW','OUTFLOW','NEUTRAL')),
    constraint ck_asset_calendar_amount check (expected_amount >= 0),
    constraint ck_asset_calendar_currency check (currency='KRW'),
    constraint ck_asset_calendar_certainty check (certainty in ('EXPECTED','CONFIRMED')),
    constraint ck_asset_calendar_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_asset_calendar_owner on customer_asset_calendar_snapshot(customer_id,scheduled_date,event_id);
create trigger trg_asset_calendar_append_only before update or delete on customer_asset_calendar_snapshot
for each row execute function reject_protected_event_mutation();

insert into customer_liability_snapshot values
    ('95600000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK','LOAN',
     '안심 생활대출','LN-***-**01',12000000,420000,4.2000,'2026-09-20','ACTIVE','KRW','2026-08-14',repeat('1',64)),
    ('95600000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK','CARD_BILL',
     '생활카드 예정대금','CD-***-**02',800000,800000,0.0000,'2026-09-15','ACTIVE','KRW','2026-08-14',repeat('2',64));

insert into customer_asset_calendar_snapshot values
    ('95700000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK',
     '95000000-0000-0000-0000-000000000001','SALARY','예상 급여 입금','2026-09-10','INFLOW',3200000,'KRW','EXPECTED','2026-08-14',repeat('3',64)),
    ('95700000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK',
     '95000000-0000-0000-0000-000000000003','INTEREST','정기예금 이자 반영','2026-09-30','INFLOW',320000,'KRW','EXPECTED','2026-08-14',repeat('4',64)),
    ('95700000-0000-0000-0000-000000000003','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK',
     '95000000-0000-0000-0000-000000000003','MATURITY','정기예금 만기','2026-12-31','NEUTRAL',20000000,'KRW','EXPECTED','2026-08-14',repeat('5',64));

insert into auth_permission(permission_code,description) values
    ('FINANCIAL_OVERVIEW_READ','본인의 합성 통합자산·부채·현금흐름 조회');
insert into auth_role_permission(role_code,permission_code) values
    ('CUSTOMER','FINANCIAL_OVERVIEW_READ');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke insert,update,delete on customer_liability_snapshot,customer_asset_calendar_snapshot from alzswell_app;
    end if;
end $$;

comment on table customer_liability_snapshot is '심사·상환 실행 없이 제공하는 마스킹된 합성 부채 snapshot';
comment on table customer_asset_calendar_snapshot is '급여·이자·만기 읽기 일정을 위한 추가 전용 합성 snapshot';
