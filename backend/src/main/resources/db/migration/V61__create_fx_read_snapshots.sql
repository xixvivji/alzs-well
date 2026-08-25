-- 외환·해외송금 합성 read model. 실제 환전·송금·외부 금융사 호출은 생성하지 않는다.
create table fx_rate_snapshot (
 rate_id uuid primary key, currency char(3) not null unique,
 currency_name varchar(40) not null, unit_amount numeric(10,2) not null,
 base_rate numeric(15,4) not null, remittance_send_rate numeric(15,4) not null,
 remittance_receive_rate numeric(15,4) not null, cash_buy_rate numeric(15,4) not null,
 cash_sell_rate numeric(15,4) not null, quoted_at timestamptz not null,
 provider_mode varchar(30) not null, data_as_of date not null, snapshot_hash char(64) not null,
 constraint ck_fx_currency check(currency in ('USD','JPY','EUR')),
 constraint ck_fx_values check(unit_amount > 0 and base_rate > 0 and remittance_send_rate > 0 and remittance_receive_rate > 0 and cash_buy_rate > 0 and cash_sell_rate > 0),
 constraint ck_fx_provider check(provider_mode='SYNTHETIC_PROVIDER'),
 constraint ck_fx_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create trigger trg_fx_rate_append_only before update or delete on fx_rate_snapshot for each row execute function reject_protected_event_mutation();

create table customer_foreign_currency_account_snapshot (
 account_id uuid primary key, customer_id varchar(80) not null references customer_profile(customer_id),
 institution_id varchar(40) not null references financial_institution(institution_id),
 masked_account_number varchar(40) not null, account_name varchar(80) not null,
 currency char(3) not null, balance numeric(19,2) not null, available_balance numeric(19,2) not null,
 status varchar(20) not null, provider_mode varchar(30) not null, data_as_of date not null, snapshot_hash char(64) not null,
 constraint uq_fx_account_owner unique(customer_id,account_id),
 constraint ck_fx_account_masked check(masked_account_number like '%*%' and masked_account_number !~ '^[0-9-]+$'),
 constraint ck_fx_account_currency check(currency in ('USD','JPY','EUR')),
 constraint ck_fx_account_balance check(balance >= 0 and available_balance >= 0 and available_balance <= balance),
 constraint ck_fx_account_status check(status='ACTIVE'), constraint ck_fx_account_provider check(provider_mode='SYNTHETIC_PROVIDER'),
 constraint ck_fx_account_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_fx_account_customer on customer_foreign_currency_account_snapshot(customer_id,currency,account_id);
create trigger trg_fx_account_append_only before update or delete on customer_foreign_currency_account_snapshot for each row execute function reject_protected_event_mutation();

create table overseas_remittance_snapshot (
 remittance_id uuid primary key, customer_id varchar(80) not null,
 source_account_id uuid not null, destination_country_code char(2) not null,
 beneficiary_alias varchar(60) not null, currency char(3) not null,
 foreign_amount numeric(19,2) not null, applied_rate numeric(15,4) not null,
 krw_amount numeric(19,0) not null, fee_amount numeric(19,0) not null,
 status varchar(20) not null, requested_at timestamptz not null, completed_at timestamptz,
 provider_mode varchar(30) not null, data_as_of date not null, snapshot_hash char(64) not null,
 constraint fk_remittance_fx_owner foreign key(customer_id,source_account_id) references customer_foreign_currency_account_snapshot(customer_id,account_id),
 constraint ck_remittance_country check(destination_country_code ~ '^[A-Z]{2}$'),
 constraint ck_remittance_currency check(currency in ('USD','JPY','EUR')),
 constraint ck_remittance_amount check(foreign_amount > 0 and applied_rate > 0 and krw_amount > 0 and fee_amount >= 0),
 constraint ck_remittance_status check(status in ('COMPLETED','CANCELLED')),
 constraint ck_remittance_period check(completed_at is null or completed_at >= requested_at),
 constraint ck_remittance_provider check(provider_mode='SYNTHETIC_PROVIDER'),
 constraint ck_remittance_hash check(snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_remittance_customer on overseas_remittance_snapshot(customer_id,requested_at desc,remittance_id desc);
create trigger trg_remittance_append_only before update or delete on overseas_remittance_snapshot for each row execute function reject_protected_event_mutation();

insert into fx_rate_snapshot values
('98400000-0000-0000-0000-000000000001','USD','미국 달러',1,1392.5000,1406.4000,1378.6000,1416.8000,1368.2000,'2026-08-25T09:00:00+09:00','SYNTHETIC_PROVIDER','2026-08-25',repeat('1',64)),
('98400000-0000-0000-0000-000000000002','JPY','일본 엔',100,946.2000,955.6000,936.8000,965.1000,927.3000,'2026-08-25T09:00:00+09:00','SYNTHETIC_PROVIDER','2026-08-25',repeat('2',64)),
('98400000-0000-0000-0000-000000000003','EUR','유로',1,1628.3000,1644.5000,1612.1000,1661.0000,1595.6000,'2026-08-25T09:00:00+09:00','SYNTHETIC_PROVIDER','2026-08-25',repeat('3',64));
insert into customer_foreign_currency_account_snapshot values
('98500000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK','***-***-8101','안심 달러통장','USD',2500.00,2400.00,'ACTIVE','SYNTHETIC_PROVIDER','2026-08-25',repeat('4',64)),
('98500000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK','***-***-8102','안심 엔화통장','JPY',180000.00,180000.00,'ACTIVE','SYNTHETIC_PROVIDER','2026-08-25',repeat('5',64));
insert into overseas_remittance_snapshot values
('98600000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','98500000-0000-0000-0000-000000000001','US','합성수취인 A','USD',500.00,1406.4000,703200,5000,'COMPLETED','2026-08-10T10:00:00+09:00','2026-08-10T10:30:00+09:00','SYNTHETIC_PROVIDER','2026-08-25',repeat('6',64)),
('98600000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','98500000-0000-0000-0000-000000000002','JP','합성수취인 B','JPY',30000.00,955.6000,286680,3000,'COMPLETED','2026-07-15T11:00:00+09:00','2026-07-15T11:20:00+09:00','SYNTHETIC_PROVIDER','2026-08-25',repeat('7',64));

insert into auth_permission values ('FX_READ','합성 환율·외화계좌·해외송금 이력 조회'),('FX_SIMULATE','실행 없는 합성 환전 모의계산');
insert into auth_role_permission values ('CUSTOMER','FX_READ'),('CUSTOMER','FX_SIMULATE');
do $$ begin if exists(select 1 from pg_roles where rolname='alzswell_app') then revoke insert,update,delete on fx_rate_snapshot,customer_foreign_currency_account_snapshot,overseas_remittance_snapshot from alzswell_app; end if; end $$;
