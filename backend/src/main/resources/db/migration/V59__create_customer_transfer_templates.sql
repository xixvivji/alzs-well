alter table customer_beneficiary_snapshot
    add constraint uq_beneficiary_owner_pair unique(beneficiary_id, customer_id);

create table customer_transfer_template (
    template_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    source_account_id uuid not null,
    beneficiary_id uuid not null,
    template_name varchar(50) not null,
    amount numeric(19,0),
    currency char(3) not null,
    purpose_code varchar(30) not null,
    status varchar(20) not null,
    row_version bigint not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    integrity_hash char(64) not null,
    constraint fk_transfer_template_owned_account foreign key(source_account_id, customer_id)
        references customer_account_snapshot(account_id, customer_id),
    constraint fk_transfer_template_owned_beneficiary foreign key(beneficiary_id, customer_id)
        references customer_beneficiary_snapshot(beneficiary_id, customer_id),
    constraint ck_transfer_template_name check (
        char_length(btrim(template_name)) between 1 and 50 and template_name=btrim(template_name)
    ),
    constraint ck_transfer_template_amount check (amount is null or amount between 1 and 100000000),
    constraint ck_transfer_template_currency check (currency='KRW'),
    constraint ck_transfer_template_purpose check (
        purpose_code in ('LIVING_EXPENSE','FAMILY_SUPPORT','BILL_PAYMENT','OWN_ACCOUNT','OTHER')
    ),
    constraint ck_transfer_template_status check (status in ('ACTIVE','DELETED')),
    constraint ck_transfer_template_deletion check (
        (status='ACTIVE' and deleted_at is null) or (status='DELETED' and deleted_at is not null)
    ),
    constraint uq_transfer_template_owner unique(template_id, customer_id),
    constraint ck_transfer_template_version check (row_version >= 1),
    constraint ck_transfer_template_hash check (integrity_hash ~ '^[0-9a-f]{64}$')
);
create index idx_transfer_template_owner
    on customer_transfer_template(customer_id, status, template_name, created_at, template_id);

create function guard_transfer_template_mutation() returns trigger language plpgsql as $$
begin
    if new.template_id is distinct from old.template_id
       or new.customer_id is distinct from old.customer_id
       or new.source_account_id is distinct from old.source_account_id
       or new.beneficiary_id is distinct from old.beneficiary_id
       or new.template_name is distinct from old.template_name
       or new.amount is distinct from old.amount
       or new.currency is distinct from old.currency
       or new.purpose_code is distinct from old.purpose_code
       or new.created_at is distinct from old.created_at
       or new.integrity_hash is distinct from old.integrity_hash then
        raise exception 'transfer template core fields are immutable';
    end if;
    if old.status <> 'ACTIVE' or new.status <> 'DELETED'
       or new.row_version <> old.row_version + 1 or new.deleted_at is null then
        raise exception 'invalid transfer template state transition';
    end if;
    return new;
end $$;
create trigger trg_transfer_template_guard before update on customer_transfer_template
for each row execute function guard_transfer_template_mutation();
create trigger trg_transfer_template_no_delete before delete on customer_transfer_template
for each row execute function reject_protected_event_mutation();

create table customer_transfer_template_event (
    event_id uuid primary key,
    template_id uuid not null,
    customer_id varchar(80) not null,
    event_type varchar(20) not null,
    source_account_id uuid not null,
    beneficiary_id uuid not null,
    template_name varchar(50) not null,
    amount numeric(19,0),
    currency char(3) not null,
    purpose_code varchar(30) not null,
    status_snapshot varchar(20) not null,
    version_snapshot bigint not null,
    actor_subject varchar(80) not null,
    occurred_at timestamptz not null,
    integrity_hash char(64) not null,
    constraint fk_transfer_template_event_owner foreign key(template_id, customer_id)
        references customer_transfer_template(template_id, customer_id),
    constraint ck_transfer_template_event_type check(event_type in ('CREATED','DELETED')),
    constraint ck_transfer_template_event_status check(status_snapshot in ('ACTIVE','DELETED')),
    constraint ck_transfer_template_event_hash check(integrity_hash ~ '^[0-9a-f]{64}$')
);
create index idx_transfer_template_event_owner
    on customer_transfer_template_event(customer_id, occurred_at desc, event_id desc);
create trigger trg_transfer_template_event_append_only
before update or delete on customer_transfer_template_event
for each row execute function reject_protected_event_mutation();

insert into auth_permission(permission_code, description) values
    ('TRANSFER_TEMPLATE_READ','본인의 저장 이체 양식 조회'),
    ('TRANSFER_TEMPLATE_WRITE','본인의 저장 이체 양식 생성·삭제');
insert into auth_role_permission(role_code, permission_code) values
    ('CUSTOMER','TRANSFER_TEMPLATE_READ'),
    ('CUSTOMER','TRANSFER_TEMPLATE_WRITE');

do $$ begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke update,delete on customer_transfer_template from alzswell_app;
        grant select,insert on customer_transfer_template to alzswell_app;
        grant update(status,row_version,deleted_at) on customer_transfer_template to alzswell_app;
        grant select,insert on customer_transfer_template_event to alzswell_app;
        revoke update,delete on customer_transfer_template_event from alzswell_app;
    end if;
end $$;

comment on table customer_transfer_template is '실제 송금을 실행하지 않는 고객 저장 이체 양식';
comment on table customer_transfer_template_event is '저장 이체 양식 생성·삭제의 추가 전용 감사 snapshot';
