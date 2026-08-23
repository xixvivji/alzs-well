create table customer_beneficiary_snapshot (
    beneficiary_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    institution_id varchar(40) not null references financial_institution(institution_id),
    display_name varchar(80) not null,
    masked_account_reference varchar(40) not null,
    beneficiary_type varchar(20) not null,
    status varchar(20) not null,
    favorite boolean not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_beneficiary_name_mask check (
        display_name ~ '^합성(수취인|사업자|본인계좌) \*[0-9]{2}$'
    ),
    constraint ck_beneficiary_account_mask check (
        masked_account_reference ~ '^(안심은행|안심증권) [0-9]{3}-\*{3}-\*{2}[0-9]{2}$'
    ),
    constraint ck_beneficiary_type check (beneficiary_type in ('PERSON','MERCHANT','OWN_ACCOUNT')),
    constraint ck_beneficiary_status check (status in ('ACTIVE','INACTIVE')),
    constraint ck_beneficiary_provider check (provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_beneficiary_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_beneficiary_owner
    on customer_beneficiary_snapshot(customer_id,status,favorite desc,display_name,beneficiary_id);
create trigger trg_beneficiary_snapshot_append_only before update or delete on customer_beneficiary_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_transfer_limit_snapshot (
    limit_snapshot_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    currency char(3) not null,
    per_transfer_limit numeric(19,0) not null,
    daily_limit numeric(19,0) not null,
    daily_used_amount numeric(19,0) not null,
    daily_remaining_amount numeric(19,0) not null,
    data_as_of date not null,
    provider_mode varchar(30) not null,
    snapshot_hash char(64) not null,
    constraint uq_transfer_limit_snapshot unique(customer_id,currency,data_as_of),
    constraint ck_transfer_limit_currency check (currency='KRW'),
    constraint ck_transfer_limit_amounts check (
        per_transfer_limit >= 0 and per_transfer_limit <= daily_limit
        and daily_limit >= 0 and daily_used_amount >= 0
        and daily_remaining_amount >= 0 and daily_used_amount + daily_remaining_amount = daily_limit
    ),
    constraint ck_transfer_limit_provider check (provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_transfer_limit_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_transfer_limit_owner
    on customer_transfer_limit_snapshot(customer_id,data_as_of desc,limit_snapshot_id);
create trigger trg_transfer_limit_snapshot_append_only before update or delete on customer_transfer_limit_snapshot
for each row execute function reject_protected_event_mutation();

insert into customer_beneficiary_snapshot values
    ('95800000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK',
     '합성수취인 *01','안심은행 110-***-**11','PERSON','ACTIVE',true,'SYNTHETIC_PROVIDER','2026-08-14',repeat('6',64)),
    ('95800000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK',
     '합성사업자 *02','안심은행 110-***-**22','MERCHANT','ACTIVE',false,'SYNTHETIC_PROVIDER','2026-08-14',repeat('7',64)),
    ('95800000-0000-0000-0000-000000000003','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_SECURITIES',
     '합성본인계좌 *03','안심증권 301-***-**33','OWN_ACCOUNT','ACTIVE',false,'SYNTHETIC_PROVIDER','2026-08-14',repeat('8',64));

insert into customer_transfer_limit_snapshot values
    ('95900000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','KRW',
     5000000,10000000,1200000,8800000,'2026-08-14','SYNTHETIC_PROVIDER',repeat('9',64));

insert into auth_permission(permission_code,description) values
    ('TRANSFER_PREVIEW_READ','본인의 마스킹된 합성 수취인·이체한도 조회'),
    ('TRANSFER_PREVIEW_EVALUATE','실제 실행 없는 합성 이체 모의계산·사전검증');
insert into auth_role_permission(role_code,permission_code) values
    ('CUSTOMER','TRANSFER_PREVIEW_READ'),
    ('CUSTOMER','TRANSFER_PREVIEW_EVALUATE');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke insert,update,delete on customer_beneficiary_snapshot,customer_transfer_limit_snapshot from alzswell_app;
    end if;
end $$;

comment on table customer_beneficiary_snapshot is '실제 계좌번호 없이 제공하는 마스킹된 합성 수취인 snapshot';
comment on table customer_transfer_limit_snapshot is '실제 금융회사 호출 없이 제공하는 합성 이체한도 snapshot';
