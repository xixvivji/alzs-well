create table account_display_setting (
    account_id uuid primary key references customer_account_snapshot(account_id),
    customer_id varchar(80) not null references customer_profile(customer_id),
    alias varchar(40),
    display_order integer not null,
    hidden boolean not null,
    row_version bigint not null,
    updated_at timestamptz not null,
    constraint ck_account_display_alias check (
        alias is null or (btrim(alias) <> '' and alias !~ '[0-9]{6,}')
    ),
    constraint ck_account_display_order check (display_order between 0 and 99),
    constraint ck_account_display_version check (row_version > 0),
    unique(customer_id, display_order)
);
create index idx_account_display_owner on account_display_setting(customer_id, hidden, display_order, account_id);

create table account_display_setting_event (
    event_id uuid primary key,
    account_id uuid not null references customer_account_snapshot(account_id),
    customer_id varchar(80) not null references customer_profile(customer_id),
    alias_snapshot varchar(40),
    display_order_snapshot integer not null,
    hidden_snapshot boolean not null,
    row_version bigint not null,
    actor_id varchar(80) not null,
    occurred_at timestamptz not null
);
create index idx_account_display_event on account_display_setting_event(account_id, occurred_at, event_id);
create trigger trg_account_display_event_append_only before update or delete on account_display_setting_event
for each row execute function reject_protected_event_mutation();

create table financial_counterparty_snapshot (
    counterparty_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    display_name varchar(80) not null,
    counterparty_type varchar(30) not null,
    first_seen_on date not null,
    last_seen_on date not null,
    transaction_count integer not null,
    new_counterparty boolean not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_counterparty_type check (counterparty_type in ('MERCHANT','INDIVIDUAL','PUBLIC_SERVICE','FINANCIAL')),
    constraint ck_counterparty_period check (last_seen_on >= first_seen_on and data_as_of >= last_seen_on),
    constraint ck_counterparty_count check (transaction_count > 0),
    constraint ck_counterparty_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create index idx_counterparty_owner on financial_counterparty_snapshot(customer_id, last_seen_on desc, counterparty_id);
create trigger trg_counterparty_snapshot_append_only before update or delete on financial_counterparty_snapshot
for each row execute function reject_protected_event_mutation();

create table account_recurring_counterparty_snapshot (
    account_id uuid not null references customer_account_snapshot(account_id),
    counterparty_id uuid not null references financial_counterparty_snapshot(counterparty_id),
    occurrence_count integer not null,
    average_amount numeric(19,0) not null,
    last_amount numeric(19,0) not null,
    currency char(3) not null,
    estimated_cycle_days integer not null,
    next_expected_on date,
    confidence numeric(5,4) not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    primary key(account_id, counterparty_id),
    constraint ck_recurring_counterparty_amount check (occurrence_count >= 2 and average_amount >= 0 and last_amount >= 0),
    constraint ck_recurring_counterparty_currency check (currency = 'KRW'),
    constraint ck_recurring_counterparty_cycle check (estimated_cycle_days between 1 and 366),
    constraint ck_recurring_counterparty_confidence check (confidence between 0 and 1),
    constraint ck_recurring_counterparty_hash check (snapshot_hash ~ '^[0-9a-f]{64}$')
);
create trigger trg_recurring_counterparty_append_only before update or delete on account_recurring_counterparty_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_account_group_snapshot (
    group_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    group_name varchar(40) not null,
    display_order integer not null,
    data_as_of date not null,
    snapshot_hash char(64) not null,
    constraint ck_account_group_order check (display_order between 0 and 99),
    constraint ck_account_group_hash check (snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(customer_id, group_name),
    unique(customer_id, display_order)
);
create index idx_account_group_owner on customer_account_group_snapshot(customer_id, display_order, group_id);
create trigger trg_account_group_append_only before update or delete on customer_account_group_snapshot
for each row execute function reject_protected_event_mutation();

create table customer_account_group_member_snapshot (
    group_id uuid not null references customer_account_group_snapshot(group_id),
    account_id uuid not null references customer_account_snapshot(account_id),
    display_order integer not null,
    snapshot_hash char(64) not null,
    primary key(group_id, account_id),
    constraint ck_account_group_member_order check (display_order between 0 and 99),
    constraint ck_account_group_member_hash check (snapshot_hash ~ '^[0-9a-f]{64}$'),
    unique(group_id, display_order)
);
create trigger trg_account_group_member_append_only before update or delete on customer_account_group_member_snapshot
for each row execute function reject_protected_event_mutation();

insert into account_display_setting values
    ('95000000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','주거래',1,false,1,'2026-08-14T00:00:00Z'),
    ('95000000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001',null,2,false,1,'2026-08-14T00:00:00Z'),
    ('95000000-0000-0000-0000-000000000003','SYN_CUSTOMER_FIN_MGMT_001',null,3,false,1,'2026-08-14T00:00:00Z'),
    ('95000000-0000-0000-0000-000000000004','SYN_CUSTOMER_FIN_MGMT_001','투자용',4,false,1,'2026-08-14T00:00:00Z');

insert into financial_counterparty_snapshot values
    ('95300000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','안심전기','PUBLIC_SERVICE','2026-02-25','2026-07-25',6,false,'2026-08-14',repeat('1',64)),
    ('95300000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','안심통신','MERCHANT','2026-01-12','2026-08-12',8,false,'2026-08-14',repeat('2',64));

insert into account_recurring_counterparty_snapshot values
    ('95000000-0000-0000-0000-000000000001','95300000-0000-0000-0000-000000000001',6,84600,85200,'KRW',30,'2026-08-25',0.9700,'2026-08-14',repeat('3',64)),
    ('95000000-0000-0000-0000-000000000001','95300000-0000-0000-0000-000000000002',8,69000,69000,'KRW',31,'2026-09-12',0.9900,'2026-08-14',repeat('4',64));

insert into customer_account_group_snapshot values
    ('95400000-0000-0000-0000-000000000001','SYN_CUSTOMER_FIN_MGMT_001','생활자금',1,'2026-08-14',repeat('5',64)),
    ('95400000-0000-0000-0000-000000000002','SYN_CUSTOMER_FIN_MGMT_001','저축·투자',2,'2026-08-14',repeat('6',64));
insert into customer_account_group_member_snapshot values
    ('95400000-0000-0000-0000-000000000001','95000000-0000-0000-0000-000000000001',1,repeat('7',64)),
    ('95400000-0000-0000-0000-000000000001','95000000-0000-0000-0000-000000000002',2,repeat('8',64)),
    ('95400000-0000-0000-0000-000000000002','95000000-0000-0000-0000-000000000003',1,repeat('9',64)),
    ('95400000-0000-0000-0000-000000000002','95000000-0000-0000-0000-000000000004',2,repeat('a',64));

insert into auth_permission(permission_code,description) values
    ('ACCOUNT_WRITE','본인의 계좌 표시 설정 변경');
insert into auth_role_permission(role_code,permission_code) values ('CUSTOMER','ACCOUNT_WRITE');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke insert, update, delete on financial_counterparty_snapshot,
            account_recurring_counterparty_snapshot, customer_account_group_snapshot,
            customer_account_group_member_snapshot, account_display_setting_event from alzswell_app;
        revoke insert, delete on account_display_setting from alzswell_app;
        grant update(alias,display_order,hidden,row_version,updated_at) on account_display_setting to alzswell_app;
        grant insert on account_display_setting_event to alzswell_app;
    end if;
end $$;

comment on table account_display_setting is '원천 계좌 snapshot과 분리한 고객 소유 표시 설정';
comment on table account_display_setting_event is '계좌 표시 설정 변경의 추가 전용 감사 이력';
comment on table financial_counterparty_snapshot is '마스킹·정규화된 합성 거래 상대 snapshot';
comment on table account_recurring_counterparty_snapshot is '계좌별 반복 거래 상대 분석 snapshot';
comment on table customer_account_group_snapshot is '고객 지정 계좌 그룹 합성 snapshot';
