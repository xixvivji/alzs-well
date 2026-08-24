-- P1 금융보유 조회: 기존 계좌·부채 원장을 재사용하고 상품별 읽기 projection만 추가한다.
create unique index uq_customer_account_owner_ref
    on customer_account_snapshot(customer_id, account_id);
create unique index uq_customer_liability_owner_ref
    on customer_liability_snapshot(customer_id, liability_id);

create table customer_deposit_holding_snapshot (
    holding_id uuid primary key,
    customer_id varchar(80) not null,
    account_id uuid not null,
    opened_on date not null,
    maturity_date date not null,
    principal_amount numeric(19,0) not null,
    expected_maturity_amount numeric(19,0) not null,
    product_type varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    foreign key(customer_id,account_id) references customer_account_snapshot(customer_id,account_id),
    constraint ck_deposit_period check(maturity_date >= opened_on),
    constraint ck_deposit_amount check(principal_amount >= 0 and expected_maturity_amount >= principal_amount),
    constraint ck_deposit_product check(product_type in ('TERM_DEPOSIT','INSTALLMENT_SAVINGS')),
    constraint ck_deposit_hash check(snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(customer_id,account_id)
);
create index idx_deposit_holding_owner on customer_deposit_holding_snapshot(customer_id,maturity_date,holding_id);
create trigger trg_deposit_holding_append_only before update or delete on customer_deposit_holding_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_loan_holding_detail_snapshot (
    loan_id uuid primary key,
    customer_id varchar(80) not null,
    original_principal numeric(19,0) not null,
    started_on date not null,
    maturity_date date not null,
    repayment_method varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    foreign key(customer_id,loan_id) references customer_liability_snapshot(customer_id,liability_id),
    constraint ck_loan_detail_amount check(original_principal > 0),
    constraint ck_loan_detail_period check(maturity_date >= started_on),
    constraint ck_loan_repayment_method check(repayment_method in ('EQUAL_PRINCIPAL_INTEREST','EQUAL_PRINCIPAL','BULLET')),
    constraint ck_loan_detail_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create trigger trg_loan_holding_detail_append_only before update or delete on customer_loan_holding_detail_snapshot
for each row execute function reject_protected_event_mutation();

create table loan_repayment_schedule_snapshot (
    installment_id uuid primary key,
    loan_id uuid not null references customer_loan_holding_detail_snapshot(loan_id),
    installment_number integer not null,
    due_date date not null,
    principal_amount numeric(19,0) not null,
    interest_amount numeric(19,0) not null,
    status varchar(20) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_loan_installment_number check(installment_number > 0),
    constraint ck_loan_schedule_amount check(principal_amount >= 0 and interest_amount >= 0),
    constraint ck_loan_schedule_status check(status in ('PAID','SCHEDULED')),
    constraint ck_loan_schedule_hash check(snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(loan_id,installment_number)
);
create index idx_loan_schedule on loan_repayment_schedule_snapshot(loan_id,due_date,installment_id);
create trigger trg_loan_schedule_append_only before update or delete on loan_repayment_schedule_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_investment_account_snapshot (
    investment_account_id uuid primary key,
    customer_id varchar(80) not null,
    cash_account_id uuid not null,
    institution_id varchar(40) not null references financial_institution(institution_id),
    display_name varchar(80) not null,
    masked_account_number varchar(40) not null,
    account_type varchar(30) not null,
    status varchar(20) not null,
    cash_balance numeric(19,0) not null,
    total_market_value numeric(19,0) not null,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    foreign key(customer_id,cash_account_id) references customer_account_snapshot(customer_id,account_id),
    constraint ck_investment_mask check(masked_account_number like '%*%' and masked_account_number !~ '[0-9]{6,}'),
    constraint ck_investment_type check(account_type in ('BROKERAGE','PENSION')),
    constraint ck_investment_status check(status in ('ACTIVE','CLOSED')),
    constraint ck_investment_amount check(cash_balance >= 0 and total_market_value >= cash_balance),
    constraint ck_investment_currency check(currency='KRW'),
    constraint ck_investment_provider check(provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_investment_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_investment_owner on customer_investment_account_snapshot(customer_id,status,investment_account_id);
create trigger trg_investment_account_append_only before update or delete on customer_investment_account_snapshot
for each row execute function reject_protected_event_mutation();

create table investment_position_snapshot (
    position_id uuid primary key,
    investment_account_id uuid not null references customer_investment_account_snapshot(investment_account_id),
    asset_class varchar(30) not null,
    instrument_name varchar(100) not null,
    masked_instrument_code varchar(40) not null,
    quantity numeric(19,4) not null,
    average_purchase_price numeric(19,0) not null,
    current_price numeric(19,0) not null,
    market_value numeric(19,0) not null,
    unrealized_profit_loss numeric(19,0) not null,
    currency char(3) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_position_class check(asset_class in ('DOMESTIC_EQUITY','BOND','FUND')),
    constraint ck_position_mask check(masked_instrument_code like '%*%' and masked_instrument_code !~ '[0-9]{6,}'),
    constraint ck_position_amount check(quantity >= 0 and average_purchase_price >= 0 and current_price >= 0 and market_value >= 0),
    constraint ck_position_currency check(currency='KRW'),
    constraint ck_position_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_position_account on investment_position_snapshot(investment_account_id,asset_class,position_id);
create trigger trg_investment_position_append_only before update or delete on investment_position_snapshot
for each row execute function reject_protected_event_mutation();

insert into customer_deposit_holding_snapshot values
('97000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','95000000-0000-0000-0000-000000000003','2025-12-31','2026-12-31',20000000,20640000,'TERM_DEPOSIT','2026-08-14',repeat('a',64));
insert into customer_loan_holding_detail_snapshot values
('95600000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001',20000000,'2024-01-20','2029-01-20','EQUAL_PRINCIPAL_INTEREST','2026-08-14',repeat('b',64));
insert into loan_repayment_schedule_snapshot values
('97100000-0000-0000-0000-000000000001','95600000-0000-0000-0000-000000000001',32,'2026-08-20',378000,42000,'PAID','2026-08-14',repeat('c',64)),
('97100000-0000-0000-0000-000000000002','95600000-0000-0000-0000-000000000001',33,'2026-09-20',378000,42000,'SCHEDULED','2026-08-14',repeat('d',64)),
('97100000-0000-0000-0000-000000000003','95600000-0000-0000-0000-000000000001',34,'2026-10-20',379300,40700,'SCHEDULED','2026-08-14',repeat('e',64));
insert into customer_investment_account_snapshot values
('97200000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','95000000-0000-0000-0000-000000000004','SYNTHETIC_SECURITIES','안심 투자계좌','301-***-**04','BROKERAGE','ACTIVE',8000000,18000000,'KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('f',64));
insert into investment_position_snapshot values
('97300000-0000-0000-0000-000000000001','97200000-0000-0000-0000-000000000001','DOMESTIC_EQUITY','안심 대표기업','A***01',10,500000,550000,5500000,500000,'KRW','2026-08-14',repeat('1',64)),
('97300000-0000-0000-0000-000000000002','97200000-0000-0000-0000-000000000001','BOND','안심 국채형 채권','B***02',5,600000,620000,3100000,100000,'KRW','2026-08-14',repeat('2',64)),
('97300000-0000-0000-0000-000000000003','97200000-0000-0000-0000-000000000001','FUND','안심 균형형 펀드','F***03',10,130000,140000,1400000,100000,'KRW','2026-08-14',repeat('3',64));

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  revoke insert,update,delete on customer_deposit_holding_snapshot,customer_loan_holding_detail_snapshot,
   loan_repayment_schedule_snapshot,customer_investment_account_snapshot,investment_position_snapshot from alzswell_app;
 end if;
end $$;

comment on table customer_deposit_holding_snapshot is '기존 합성 예금계좌에 연결된 추가 전용 만기 projection';
comment on table customer_loan_holding_detail_snapshot is '기존 합성 대출부채에 연결된 추가 전용 계약 projection';
comment on table loan_repayment_schedule_snapshot is '상환 실행 없이 표시만 하는 합성 대출 일정 snapshot';
comment on table customer_investment_account_snapshot is '안심증권 합성 투자계좌의 추가 전용 snapshot';
comment on table investment_position_snapshot is '주문 실행 없이 표시만 하는 합성 투자 포지션 snapshot';
