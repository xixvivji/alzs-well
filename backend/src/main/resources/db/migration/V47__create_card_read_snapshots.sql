alter table customer_account_snapshot
    add constraint uq_account_owner_institution_pair unique(account_id,customer_id,institution_id);

create table customer_card_snapshot (
    card_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    institution_id varchar(40) not null references financial_institution(institution_id),
    linked_account_id uuid not null,
    display_name varchar(80) not null,
    masked_card_number varchar(40) not null,
    card_type varchar(20) not null,
    brand_code varchar(20) not null,
    status varchar(20) not null,
    payment_day integer not null,
    next_payment_due_date date not null,
    current_usage_amount numeric(19,0) not null,
    current_due_amount numeric(19,0) not null,
    total_limit_amount numeric(19,0) not null,
    available_limit_amount numeric(19,0) not null,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint fk_card_owned_linked_account foreign key(linked_account_id,customer_id,institution_id)
        references customer_account_snapshot(account_id,customer_id,institution_id),
    constraint ck_card_number_mask check (
        masked_card_number ~ '^안심카드 \*{4}-\*{4}-\*{4}-[0-9]{4}$'
    ),
    constraint ck_card_type check (card_type in ('CREDIT','DEBIT')),
    constraint ck_card_brand check (brand_code in ('LOCAL','GLOBAL')),
    constraint ck_card_status check (status in ('ACTIVE','BLOCKED','EXPIRED')),
    constraint ck_card_payment_day check (payment_day between 1 and 31),
    constraint ck_card_amounts check (
        current_usage_amount >= 0 and current_due_amount >= 0
        and current_due_amount <= current_usage_amount and total_limit_amount >= 0
        and available_limit_amount >= 0 and available_limit_amount <= total_limit_amount
        and current_usage_amount + available_limit_amount <= total_limit_amount
    ),
    constraint ck_card_currency check (currency='KRW'),
    constraint ck_card_provider check (provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_card_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_card_owner on customer_card_snapshot(customer_id,status,card_id);
create trigger trg_card_snapshot_append_only before update or delete on customer_card_snapshot
for each row execute function reject_protected_event_mutation();

create table card_transaction_snapshot (
    card_transaction_id uuid primary key,
    card_id uuid not null references customer_card_snapshot(card_id),
    occurred_at timestamptz not null,
    merchant_display_name varchar(100) not null,
    category_code varchar(30) not null,
    amount numeric(19,0) not null,
    status varchar(20) not null,
    installment_months integer not null,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_card_transaction_merchant check (
        merchant_display_name ~ '^합성(마트|교통|통신|병원|서점|식당|온라인) [0-9]{2}$'
    ),
    constraint ck_card_transaction_category check (category_code in (
        'UTILITIES','COMMUNICATION','FOOD','TRANSPORT','HEALTH','SHOPPING','OTHER'
    )),
    constraint ck_card_transaction_amount check (amount > 0),
    constraint ck_card_transaction_status check (status in ('APPROVED','PENDING','CANCELLED')),
    constraint ck_card_transaction_installment check (installment_months between 1 and 36),
    constraint ck_card_transaction_currency check (currency='KRW'),
    constraint ck_card_transaction_provider check (provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_card_transaction_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_card_transaction_cursor
    on card_transaction_snapshot(card_id,occurred_at desc,card_transaction_id desc);
create trigger trg_card_transaction_append_only before update or delete on card_transaction_snapshot
for each row execute function reject_protected_event_mutation();

create table card_statement_snapshot (
    statement_id uuid primary key,
    card_id uuid not null references customer_card_snapshot(card_id),
    period_from date not null,
    period_to date not null,
    statement_date date not null,
    due_date date not null,
    total_amount numeric(19,0) not null,
    paid_amount numeric(19,0) not null,
    remaining_due_amount numeric(19,0) not null,
    status varchar(20) not null,
    currency char(3) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint uq_card_statement_period unique(card_id,period_from,period_to),
    constraint ck_card_statement_period check (
        period_to >= period_from and statement_date >= period_to and due_date >= statement_date
    ),
    constraint ck_card_statement_amount check (
        total_amount >= 0 and paid_amount >= 0 and remaining_due_amount >= 0
        and paid_amount + remaining_due_amount = total_amount
    ),
    constraint ck_card_statement_status check (status in ('ISSUED','PARTIALLY_PAID','PAID')),
    constraint ck_card_statement_currency check (currency='KRW'),
    constraint ck_card_statement_provider check (provider_mode='SYNTHETIC_PROVIDER'),
    constraint ck_card_statement_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_card_statement_owner on card_statement_snapshot(card_id,period_to desc,statement_id desc);
create trigger trg_card_statement_append_only before update or delete on card_statement_snapshot
for each row execute function reject_protected_event_mutation();

insert into customer_card_snapshot values
    ('96000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK',
     '95000000-0000-0000-0000-000000000001','안심 생활신용카드','안심카드 ****-****-****-1001',
     'CREDIT','LOCAL','ACTIVE',15,'2026-08-15',1200000,800000,5000000,3800000,'KRW',
     'SYNTHETIC_PROVIDER','2026-08-14',repeat('a',64)),
    ('96000000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','SYNTHETIC_BANK',
     '95000000-0000-0000-0000-000000000002','안심 생활체크카드','안심카드 ****-****-****-2002',
     'DEBIT','GLOBAL','ACTIVE',20,'2026-08-20',300000,0,1000000,700000,'KRW',
     'SYNTHETIC_PROVIDER','2026-08-14',repeat('b',64));

insert into card_transaction_snapshot values
    ('96100000-0000-0000-0000-000000000001','96000000-0000-0000-0000-000000000001','2026-08-13T10:30:00Z','합성마트 01','FOOD',81400,'APPROVED',1,'KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('c',64)),
    ('96100000-0000-0000-0000-000000000002','96000000-0000-0000-0000-000000000001','2026-08-12T03:00:00Z','합성통신 02','COMMUNICATION',69000,'APPROVED',1,'KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('d',64)),
    ('96100000-0000-0000-0000-000000000003','96000000-0000-0000-0000-000000000001','2026-08-09T09:10:00Z','합성서점 03','SHOPPING',42000,'APPROVED',1,'KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('e',64)),
    ('96100000-0000-0000-0000-000000000004','96000000-0000-0000-0000-000000000002','2026-08-11T08:20:00Z','합성교통 04','TRANSPORT',27500,'APPROVED',1,'KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('f',64));

insert into card_statement_snapshot values
    ('96200000-0000-0000-0000-000000000001','96000000-0000-0000-0000-000000000001',
     '2026-07-01','2026-07-31','2026-08-01','2026-08-15',800000,0,800000,'ISSUED','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('1',64)),
    ('96200000-0000-0000-0000-000000000002','96000000-0000-0000-0000-000000000002',
     '2026-07-01','2026-07-31','2026-08-01','2026-08-20',300000,300000,0,'PAID','KRW','SYNTHETIC_PROVIDER','2026-08-14',repeat('2',64));

insert into auth_permission(permission_code,description) values
    ('CARD_READ','본인의 마스킹된 합성 카드·이용내역·청구·한도 조회');
insert into auth_role_permission(role_code,permission_code) values ('CUSTOMER','CARD_READ');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke insert,update,delete on customer_card_snapshot,card_transaction_snapshot,card_statement_snapshot from alzswell_app;
    end if;
end $$;

comment on table customer_card_snapshot is '잠금·해제·재발급 실행 없이 제공하는 마스킹된 합성 카드 snapshot';
comment on table card_transaction_snapshot is '원문 가맹점 정보 없이 제공하는 추가 전용 합성 카드 이용 snapshot';
comment on table card_statement_snapshot is '결제 실행·파일 다운로드 없이 제공하는 추가 전용 합성 청구 snapshot';
