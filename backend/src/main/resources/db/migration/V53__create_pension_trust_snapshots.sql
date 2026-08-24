-- 연금·신탁 조회는 안심은행 합성 snapshot만 제공하며 가입·변경·해지를 실행하지 않는다.
create table customer_pension_holding_snapshot (
    holding_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    institution_id varchar(40) not null references financial_institution(institution_id),
    display_name varchar(80) not null,
    masked_contract_reference varchar(40) not null,
    pension_type varchar(30) not null,
    status varchar(20) not null,
    contributed_amount numeric(19,0) not null,
    current_value numeric(19,0) not null,
    expected_benefit_start_date date not null,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_pension_mask check(masked_contract_reference like '%*%' and masked_contract_reference !~ '[0-9]{6,}'),
    constraint ck_pension_type check(pension_type in ('IRP','PENSION_SAVINGS','RETIREMENT_PENSION')),
    constraint ck_pension_status check(status in ('ACTIVE','BENEFIT_STARTED','CLOSED')),
    constraint ck_pension_amount check(contributed_amount >= 0 and current_value >= 0),
    constraint ck_pension_currency check(currency='KRW'),
    constraint ck_pension_provider check(provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_pension_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_pension_holding_owner on customer_pension_holding_snapshot(customer_id,status,holding_id);
create trigger trg_pension_holding_append_only before update or delete on customer_pension_holding_snapshot
for each row execute function reject_protected_event_mutation();

create table pension_projection_snapshot (
    projection_id uuid primary key,
    holding_id uuid not null references customer_pension_holding_snapshot(holding_id),
    scenario_code varchar(30) not null,
    assumed_annual_return numeric(7,4) not null,
    projected_value numeric(19,0) not null,
    projected_monthly_benefit numeric(19,0) not null,
    benefit_start_date date not null,
    calculated_on date not null,
    provider_mode varchar(30) not null,
    snapshot_hash char(64) not null,
    constraint ck_pension_projection_scenario check(scenario_code in ('CONSERVATIVE','BASELINE')),
    constraint ck_pension_projection_return check(assumed_annual_return between 0 and 100),
    constraint ck_pension_projection_amount check(projected_value >= 0 and projected_monthly_benefit >= 0),
    constraint ck_pension_projection_provider check(provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_pension_projection_hash check(snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(holding_id,scenario_code)
);
create index idx_pension_projection_holding on pension_projection_snapshot(holding_id,scenario_code,projection_id);
create trigger trg_pension_projection_append_only before update or delete on pension_projection_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_trust_holding_snapshot (
    trust_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    institution_id varchar(40) not null references financial_institution(institution_id),
    display_name varchar(80) not null,
    masked_contract_reference varchar(40) not null,
    trust_type varchar(30) not null,
    purpose_code varchar(40) not null,
    status varchar(20) not null,
    entrusted_principal numeric(19,0) not null,
    current_value numeric(19,0) not null,
    beneficiary_count integer not null,
    started_on date not null,
    maturity_date date,
    next_review_date date,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_trust_mask check(masked_contract_reference like '%*%' and masked_contract_reference !~ '[0-9]{6,}'),
    constraint ck_trust_type check(trust_type in ('LIVING_TRUST','ASSET_MANAGEMENT_TRUST')),
    constraint ck_trust_purpose check(purpose_code in ('LIVING_SUPPORT','ASSET_MANAGEMENT')),
    constraint ck_trust_status check(status in ('ACTIVE','MATURED','CLOSED')),
    constraint ck_trust_amount check(entrusted_principal >= 0 and current_value >= 0),
    constraint ck_trust_beneficiary_count check(beneficiary_count >= 0),
    constraint ck_trust_period check(maturity_date is null or maturity_date >= started_on),
    constraint ck_trust_currency check(currency='KRW'),
    constraint ck_trust_provider check(provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_trust_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_trust_holding_owner on customer_trust_holding_snapshot(customer_id,status,trust_id);
create trigger trg_trust_holding_append_only before update or delete on customer_trust_holding_snapshot
for each row execute function reject_protected_event_mutation();

insert into customer_pension_holding_snapshot values
('97400000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK','안심 개인형퇴직연금','PEN-***-**01','IRP','ACTIVE',18000000,20500000,'2036-03-01','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('4',64));
insert into pension_projection_snapshot values
('97500000-0000-0000-0000-000000000001','97400000-0000-0000-0000-000000000001','CONSERVATIVE',2.0000,25000000,135000,'2036-03-01','2026-08-14','SYNTHETIC_PROVIDER',repeat('5',64)),
('97500000-0000-0000-0000-000000000002','97400000-0000-0000-0000-000000000001','BASELINE',3.5000,29500000,160000,'2036-03-01','2026-08-14','SYNTHETIC_PROVIDER',repeat('6',64));
insert into customer_trust_holding_snapshot values
('97600000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK','안심 생활지원신탁','TRU-***-**01','LIVING_TRUST','LIVING_SUPPORT','ACTIVE',30000000,30700000,1,'2025-06-01',null,'2026-12-01','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('7',64));

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  revoke insert,update,delete on customer_pension_holding_snapshot,pension_projection_snapshot,
   customer_trust_holding_snapshot from alzswell_app;
 end if;
end $$;

comment on table customer_pension_holding_snapshot is '금융사 원문을 대신하는 안심은행 합성 연금 보유 snapshot';
comment on table pension_projection_snapshot is '추천이나 보장 없이 표시만 하는 합성 연금 전망 snapshot';
comment on table customer_trust_holding_snapshot is '수익자 식별정보와 실행 기능을 포함하지 않는 합성 신탁 snapshot';
