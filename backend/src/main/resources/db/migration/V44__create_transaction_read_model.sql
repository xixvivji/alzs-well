create table financial_transaction_snapshot (
    transaction_id uuid primary key,
    account_id uuid not null references customer_account_snapshot(account_id),
    customer_id varchar(80) not null references customer_profile(customer_id),
    counterparty_id uuid references financial_counterparty_snapshot(counterparty_id),
    occurred_at timestamptz not null,
    posted_on date not null,
    direction varchar(10) not null,
    transaction_type varchar(30) not null,
    status varchar(20) not null,
    amount numeric(19,0) not null,
    currency char(3) not null,
    balance_after numeric(19,0) not null,
    display_description varchar(120) not null,
    provider_mode varchar(30) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_transaction_direction check (direction in ('CREDIT','DEBIT')),
    constraint ck_transaction_status check (status in ('POSTED','PENDING','REVERSED')),
    constraint ck_transaction_amount check (amount > 0 and balance_after >= 0),
    constraint ck_transaction_currency check (currency = 'KRW'),
    constraint ck_transaction_provider check (provider_mode = 'SYNTHETIC_PROVIDER'),
    constraint ck_transaction_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_transaction_account_cursor on financial_transaction_snapshot(account_id,occurred_at desc,transaction_id desc);
create index idx_transaction_customer_cursor on financial_transaction_snapshot(customer_id,occurred_at desc,transaction_id desc);
create index idx_transaction_counterparty on financial_transaction_snapshot(counterparty_id,occurred_at desc,transaction_id desc);
create trigger trg_transaction_snapshot_append_only before update or delete on financial_transaction_snapshot
for each row execute function reject_protected_event_mutation();

create table transaction_enrichment_snapshot (
    transaction_id uuid primary key references financial_transaction_snapshot(transaction_id),
    normalized_description varchar(120) not null,
    inferred_category varchar(30) not null,
    recurring_candidate boolean not null,
    new_counterparty boolean not null,
    confidence numeric(5,4) not null,
    enrichment_version varchar(40) not null,
    reason_codes varchar(60)[] not null,
    snapshot_hash char(64) not null,
    constraint ck_transaction_category check (inferred_category in (
        'INCOME','HOUSING','UTILITIES','COMMUNICATION','FOOD','TRANSPORT','HEALTH','FINANCE','SHOPPING','OTHER'
    )),
    constraint ck_transaction_enrichment_confidence check (confidence between 0 and 1),
    constraint ck_transaction_enrichment_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create trigger trg_transaction_enrichment_append_only before update or delete on transaction_enrichment_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_transaction_preference (
    transaction_id uuid primary key references financial_transaction_snapshot(transaction_id),
    customer_id varchar(80) not null references customer_profile(customer_id),
    category_code varchar(30),
    note_text varchar(120),
    row_version bigint not null,
    updated_at timestamptz not null,
    constraint ck_transaction_preference_category check (category_code is null or category_code in (
        'INCOME','HOUSING','UTILITIES','COMMUNICATION','FOOD','TRANSPORT','HEALTH','FINANCE','SHOPPING','OTHER'
    )),
    constraint ck_transaction_preference_note check (note_text is null or (btrim(note_text)<>'' and note_text !~ '[0-9]{6,}')),
    constraint ck_transaction_preference_version check (row_version > 0)
);

create table customer_transaction_preference_event (
    event_id uuid primary key,
    transaction_id uuid not null references financial_transaction_snapshot(transaction_id),
    customer_id varchar(80) not null references customer_profile(customer_id),
    event_type varchar(30) not null,
    category_snapshot varchar(30),
    note_snapshot varchar(120),
    row_version bigint not null,
    actor_id varchar(80) not null,
    occurred_at timestamptz not null,
    constraint ck_transaction_preference_event_type check (event_type in ('CATEGORY_UPDATED','NOTE_UPDATED'))
);
create index idx_transaction_preference_event on customer_transaction_preference_event(transaction_id,occurred_at,event_id);
create trigger trg_transaction_preference_event_append_only before update or delete on customer_transaction_preference_event
for each row execute function reject_protected_event_mutation();

insert into financial_counterparty_snapshot values
    ('95300000-0000-0000-0000-000000000003','SYN_CUSTOMER_FIN_MGMT_001','안심급여','FINANCIAL','2026-02-10','2026-08-10',7,false,'2026-08-14',repeat('b',64)),
    ('95300000-0000-0000-0000-000000000004','SYN_CUSTOMER_FIN_MGMT_001','안심마켓','MERCHANT','2026-07-02','2026-08-13',4,false,'2026-08-14',repeat('c',64));

insert into financial_transaction_snapshot values
    ('95500000-0000-0000-0000-000000000001','95000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','95300000-0000-0000-0000-000000000003','2026-08-10T09:00:00Z','2026-08-10','CREDIT','TRANSFER_IN','POSTED',3200000,'KRW',20200000,'안심급여 입금','SYNTHETIC_PROVIDER','2026-08-14',repeat('1',64)),
    ('95500000-0000-0000-0000-000000000002','95000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','95300000-0000-0000-0000-000000000002','2026-08-12T03:00:00Z','2026-08-12','DEBIT','AUTOPAY','POSTED',69000,'KRW',20131000,'안심통신 정기납부','SYNTHETIC_PROVIDER','2026-08-14',repeat('2',64)),
    ('95500000-0000-0000-0000-000000000003','95000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','95300000-0000-0000-0000-000000000004','2026-08-13T10:30:00Z','2026-08-13','DEBIT','CARD_SETTLEMENT','POSTED',81400,'KRW',20049600,'안심마켓 생활비','SYNTHETIC_PROVIDER','2026-08-14',repeat('3',64)),
    ('95500000-0000-0000-0000-000000000004','95000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001',null,'2026-08-14T02:00:00Z','2026-08-14','DEBIT','TRANSFER_OUT','POSTED',1599600,'KRW',18450000,'생활비 계좌 이동','SYNTHETIC_PROVIDER','2026-08-14',repeat('4',64)),
    ('95500000-0000-0000-0000-000000000005','95000000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001',null,'2026-08-14T02:00:01Z','2026-08-14','CREDIT','TRANSFER_IN','POSTED',1599600,'KRW',3200000,'생활비 계좌 입금','SYNTHETIC_PROVIDER','2026-08-14',repeat('5',64)),
    ('95500000-0000-0000-0000-000000000006','95000000-0000-0000-0000-000000000003','SYN_CUSTOMER_FIN_MGMT_001',null,'2026-08-01T00:00:00Z','2026-08-01','CREDIT','INTEREST','POSTED',320000,'KRW',20000000,'정기예금 이자예정','SYNTHETIC_PROVIDER','2026-08-14',repeat('6',64)),
    ('95500000-0000-0000-0000-000000000007','95000000-0000-0000-0000-000000000004','SYN_CUSTOMER_FIN_MGMT_001',null,'2026-08-08T06:00:00Z','2026-08-08','CREDIT','SECURITIES_CASH','POSTED',500000,'KRW',8000000,'증권 예수금 입금','SYNTHETIC_PROVIDER','2026-08-14',repeat('7',64)),
    ('95500000-0000-0000-0000-000000000008','95000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','95300000-0000-0000-0000-000000000001','2026-07-25T01:00:00Z','2026-07-25','DEBIT','AUTOPAY','POSTED',85200,'KRW',17900000,'안심전기 정기납부','SYNTHETIC_PROVIDER','2026-08-14',repeat('8',64));

insert into transaction_enrichment_snapshot values
    ('95500000-0000-0000-0000-000000000001','안심급여','INCOME',true,false,0.9900,'rules-v1',array['SALARY_PATTERN'],repeat('9',64)),
    ('95500000-0000-0000-0000-000000000002','안심통신','COMMUNICATION',true,false,0.9900,'rules-v1',array['RECURRING_COUNTERPARTY'],repeat('a',64)),
    ('95500000-0000-0000-0000-000000000003','안심마켓','FOOD',false,false,0.8800,'rules-v1',array['MERCHANT_CATEGORY'],repeat('b',64)),
    ('95500000-0000-0000-0000-000000000004','내부 계좌 이동','FINANCE',false,false,0.9500,'rules-v1',array['INTERNAL_TRANSFER'],repeat('c',64)),
    ('95500000-0000-0000-0000-000000000005','내부 계좌 이동','FINANCE',false,false,0.9500,'rules-v1',array['INTERNAL_TRANSFER'],repeat('d',64)),
    ('95500000-0000-0000-0000-000000000006','예금 이자','INCOME',false,false,0.9800,'rules-v1',array['INTEREST_TYPE'],repeat('e',64)),
    ('95500000-0000-0000-0000-000000000007','증권 예수금','FINANCE',false,false,0.9200,'rules-v1',array['SECURITIES_CASH'],repeat('f',64)),
    ('95500000-0000-0000-0000-000000000008','안심전기','UTILITIES',true,false,0.9700,'rules-v1',array['RECURRING_COUNTERPARTY'],repeat('0',64));

insert into customer_transaction_preference(transaction_id,customer_id,category_code,note_text,row_version,updated_at)
select transaction_id,customer_id,null,null,1,'2026-08-14T00:00:00Z' from financial_transaction_snapshot;

insert into auth_permission(permission_code,description) values
    ('TRANSACTION_READ','본인의 마스킹된 합성 거래·상대방·분석 조회'),
    ('TRANSACTION_WRITE','본인의 거래 범주와 금융 기억노트 변경');
insert into auth_role_permission(role_code,permission_code) values
    ('CUSTOMER','TRANSACTION_READ'),('CUSTOMER','TRANSACTION_WRITE');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke insert,update,delete on financial_transaction_snapshot,transaction_enrichment_snapshot from alzswell_app;
        revoke insert,update,delete on customer_transaction_preference from alzswell_app;
        grant update(category_code,note_text,row_version,updated_at) on customer_transaction_preference to alzswell_app;
        revoke update,delete on customer_transaction_preference_event from alzswell_app;
        grant insert on customer_transaction_preference_event to alzswell_app;
    end if;
end $$;

comment on table financial_transaction_snapshot is '외부 실행 없이 제공하는 마스킹된 합성 거래 원장 snapshot';
comment on table transaction_enrichment_snapshot is '결정론적 거래 정규화·범주·반복성 분석 snapshot';
comment on table customer_transaction_preference is '원천 거래와 분리한 고객 범주·기억노트 설정';
comment on table customer_transaction_preference_event is '고객 거래 설정의 추가 전용 변경 이력';
