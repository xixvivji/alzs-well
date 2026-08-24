-- P2 금융상품 조회·모의계산: 외부 상품 API와 실제 신청 없이 승인된 합성 snapshot만 제공한다.
create table deposit_product_snapshot (
    product_id uuid primary key,
    institution_id varchar(40) not null references financial_institution(institution_id),
    product_name varchar(100) not null,
    product_type varchar(30) not null,
    min_principal numeric(19,0) not null,
    max_principal numeric(19,0) not null,
    min_term_months integer not null,
    max_term_months integer not null,
    interest_payment_type varchar(30) not null,
    summary varchar(300) not null,
    caution_text varchar(500) not null,
    status varchar(20) not null,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_deposit_product_type check(product_type in ('TERM_DEPOSIT','INSTALLMENT_SAVINGS')),
    constraint ck_deposit_product_amount check(min_principal > 0 and max_principal >= min_principal),
    constraint ck_deposit_product_term check(min_term_months > 0 and max_term_months >= min_term_months),
    constraint ck_deposit_interest_payment check(interest_payment_type in ('AT_MATURITY','MONTHLY')),
    constraint ck_deposit_product_status check(status='AVAILABLE'),
    constraint ck_deposit_product_currency check(currency='KRW'),
    constraint ck_deposit_product_provider check(provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_deposit_product_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_deposit_product_catalog on deposit_product_snapshot(status,product_type,product_name,product_id);
create trigger trg_deposit_product_append_only before update or delete on deposit_product_snapshot
for each row execute function reject_protected_event_mutation();

create table deposit_product_rate_snapshot (
    rate_id uuid primary key,
    product_id uuid not null references deposit_product_snapshot(product_id),
    tier_code varchar(30) not null,
    min_term_months integer not null,
    max_term_months integer not null,
    annual_interest_rate numeric(7,4) not null,
    rate_type varchar(20) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_deposit_rate_term check(min_term_months > 0 and max_term_months >= min_term_months),
    constraint ck_deposit_rate_value check(annual_interest_rate >= 0),
    constraint ck_deposit_rate_type check(rate_type='FIXED'),
    constraint ck_deposit_rate_hash check(snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(product_id,tier_code,min_term_months,max_term_months)
);
create index idx_deposit_rate_product on deposit_product_rate_snapshot(product_id,min_term_months,rate_id);
create trigger trg_deposit_product_rate_append_only before update or delete on deposit_product_rate_snapshot
for each row execute function reject_protected_event_mutation();

create table deposit_maturity_option_snapshot (
    option_id uuid primary key,
    holding_id uuid not null references customer_deposit_holding_snapshot(holding_id),
    option_code varchar(30) not null,
    title varchar(100) not null,
    description varchar(300) not null,
    display_order integer not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_maturity_option_code check(option_code in ('RENEW_PRINCIPAL','RENEW_ALL','CLOSE_AT_MATURITY')),
    constraint ck_maturity_option_order check(display_order > 0),
    constraint ck_maturity_option_hash check(snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(holding_id,option_code)
);
create trigger trg_deposit_maturity_option_append_only before update or delete on deposit_maturity_option_snapshot
for each row execute function reject_protected_event_mutation();

create table loan_product_snapshot (
    product_id uuid primary key,
    institution_id varchar(40) not null references financial_institution(institution_id),
    product_name varchar(100) not null,
    product_type varchar(30) not null,
    min_principal numeric(19,0) not null,
    max_principal numeric(19,0) not null,
    min_term_months integer not null,
    max_term_months integer not null,
    min_annual_interest_rate numeric(7,4) not null,
    max_annual_interest_rate numeric(7,4) not null,
    repayment_method varchar(30) not null,
    summary varchar(300) not null,
    caution_text varchar(500) not null,
    status varchar(20) not null,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_loan_product_type check(product_type in ('UNSECURED','SECURED')),
    constraint ck_loan_product_amount check(min_principal > 0 and max_principal >= min_principal),
    constraint ck_loan_product_term check(min_term_months > 0 and max_term_months >= min_term_months),
    constraint ck_loan_product_rate check(min_annual_interest_rate >= 0 and max_annual_interest_rate >= min_annual_interest_rate),
    constraint ck_loan_product_method check(repayment_method='EQUAL_PRINCIPAL'),
    constraint ck_loan_product_status check(status='AVAILABLE'),
    constraint ck_loan_product_currency check(currency='KRW'),
    constraint ck_loan_product_provider check(provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_loan_product_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_loan_product_catalog on loan_product_snapshot(status,product_type,product_name,product_id);
create trigger trg_loan_product_append_only before update or delete on loan_product_snapshot
for each row execute function reject_protected_event_mutation();

insert into deposit_product_snapshot values
('97400000-0000-0000-0000-000000000001','SYNTHETIC_BANK','안심 정기예금','TERM_DEPOSIT',100000,100000000,6,36,'AT_MATURITY','목돈을 정해진 기간 예치하는 합성 정기예금입니다.','표시 금리와 계산 결과는 합성 예시이며 실제 가입·수익을 보장하지 않습니다.','AVAILABLE','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('4',64)),
('97400000-0000-0000-0000-000000000002','SYNTHETIC_BANK','안심 생활적금','INSTALLMENT_SAVINGS',10000,3000000,6,24,'AT_MATURITY','매월 같은 금액을 적립하는 합성 적금 상품입니다.','본 데모에서는 정기예금 방식의 단순 계산만 제공하며 실제 가입은 지원하지 않습니다.','AVAILABLE','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('5',64));
insert into deposit_product_rate_snapshot values
('97500000-0000-0000-0000-000000000001','97400000-0000-0000-0000-000000000001','BASE',6,11,2.8000,'FIXED','2026-08-14',repeat('6',64)),
('97500000-0000-0000-0000-000000000002','97400000-0000-0000-0000-000000000001','BASE',12,23,3.2000,'FIXED','2026-08-14',repeat('7',64)),
('97500000-0000-0000-0000-000000000003','97400000-0000-0000-0000-000000000001','BASE',24,36,3.4000,'FIXED','2026-08-14',repeat('8',64)),
('97500000-0000-0000-0000-000000000004','97400000-0000-0000-0000-000000000002','BASE',6,11,2.6000,'FIXED','2026-08-14',repeat('9',64)),
('97500000-0000-0000-0000-000000000005','97400000-0000-0000-0000-000000000002','BASE',12,24,3.0000,'FIXED','2026-08-14',repeat('a',64));
insert into deposit_maturity_option_snapshot values
('97600000-0000-0000-0000-000000000001','97000000-0000-0000-0000-000000000001','RENEW_PRINCIPAL','원금 재예치','만기 시점의 원금만 같은 기간으로 재예치하는 안내입니다.',1,'2026-08-14',repeat('b',64)),
('97600000-0000-0000-0000-000000000002','97000000-0000-0000-0000-000000000001','RENEW_ALL','원리금 재예치','만기 원금과 세후 이자를 함께 재예치하는 안내입니다.',2,'2026-08-14',repeat('c',64)),
('97600000-0000-0000-0000-000000000003','97000000-0000-0000-0000-000000000001','CLOSE_AT_MATURITY','만기 해지','만기 후 연결 계좌로 지급받는 절차 안내입니다.',3,'2026-08-14',repeat('d',64));
insert into loan_product_snapshot values
('97700000-0000-0000-0000-000000000001','SYNTHETIC_BANK','안심 생활대출','UNSECURED',1000000,50000000,12,60,4.2000,8.5000,'EQUAL_PRINCIPAL','생활자금 목적의 합성 신용대출 예시입니다.','신호·경보 정보는 심사나 금리 산정에 사용하지 않으며 실제 신청을 지원하지 않습니다.','AVAILABLE','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('e',64)),
('97700000-0000-0000-0000-000000000002','SYNTHETIC_BANK','안심 담보대출','SECURED',10000000,300000000,12,120,3.8000,7.2000,'EQUAL_PRINCIPAL','담보 조건을 단순화한 합성 대출 예시입니다.','실제 담보평가·신용조회·심사를 수행하지 않으며 계산 결과는 예시입니다.','AVAILABLE','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('f',64));

insert into auth_permission(permission_code,description) values
('FINANCIAL_PRODUCT_READ','안심은행 합성 예금·대출 상품과 만기 선택지 조회'),
('FINANCIAL_PRODUCT_SIMULATE','실제 신청 없는 합성 이자·상환 모의계산');
insert into auth_role_permission(role_code,permission_code) values
('CUSTOMER','FINANCIAL_PRODUCT_READ'),('CUSTOMER','FINANCIAL_PRODUCT_SIMULATE');

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  revoke insert,update,delete on deposit_product_snapshot,deposit_product_rate_snapshot,
   deposit_maturity_option_snapshot,loan_product_snapshot from alzswell_app;
 end if;
end $$;

comment on table deposit_product_snapshot is '외부 상품 API를 호출하지 않는 안심은행 합성 예금·적금 상품 snapshot';
comment on table deposit_product_rate_snapshot is '결정론적 이자 모의계산에 사용하는 합성 고정금리 snapshot';
comment on table deposit_maturity_option_snapshot is '실제 만기 처리를 실행하지 않는 합성 선택지 snapshot';
comment on table loan_product_snapshot is '심사·신용조회·신청을 실행하지 않는 안심은행 합성 대출상품 snapshot';
