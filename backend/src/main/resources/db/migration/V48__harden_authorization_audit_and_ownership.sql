-- 직원 접근권은 고객·목적·범위·만료를 모두 만족해야 한다.
create table staff_access_purpose_scope (
    purpose_code varchar(60) not null,
    scope_code varchar(60) not null,
    primary key(purpose_code, scope_code)
);

insert into staff_access_purpose_scope values
    ('CUSTOMER_CONSENT_MANAGEMENT','CONSENT_READ'),
    ('CUSTOMER_CONSENT_MANAGEMENT','CONSENT_WRITE'),
    ('TRUSTED_CONTACT_MANAGEMENT','TRUSTED_CONTACT_READ'),
    ('TRUSTED_CONTACT_MANAGEMENT','TRUSTED_CONTACT_WRITE'),
    ('FINANCIAL_INTENT_REVIEW','FINANCIAL_INTENT_READ'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_READ'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_ASSIGN'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_REVIEW'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_GUIDANCE'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_NOTE'),
    ('PROTECTION_CASE_MANAGEMENT','CASE_FOLLOW_UP'),
    ('PRIVACY_REQUEST_ASSISTANCE','PRIVACY_REQUEST_WRITE'),
    ('ALERT_MANAGEMENT','ALERT_READ'),
    ('ALERT_MANAGEMENT','ALERT_RESPOND'),
    ('PROTECTION_ENROLLMENT_REVIEW','PROTECTION_ENROLLMENT_READ');

update staff_access_grant set purpose_code = case
    when scopes <@ array['CONSENT_READ','CONSENT_WRITE']::varchar[] then 'CUSTOMER_CONSENT_MANAGEMENT'
    when scopes <@ array['TRUSTED_CONTACT_READ','TRUSTED_CONTACT_WRITE']::varchar[] then 'TRUSTED_CONTACT_MANAGEMENT'
    when scopes <@ array['FINANCIAL_INTENT_READ']::varchar[] then 'FINANCIAL_INTENT_REVIEW'
    when scopes <@ array['CASE_READ','CASE_ASSIGN','CASE_REVIEW','CASE_GUIDANCE','CASE_NOTE','CASE_FOLLOW_UP']::varchar[] then 'PROTECTION_CASE_MANAGEMENT'
    when scopes <@ array['PRIVACY_REQUEST_WRITE']::varchar[] then 'PRIVACY_REQUEST_ASSISTANCE'
    else purpose_code end;

do $$
begin
    if exists(
        select 1 from staff_access_grant g cross join lateral unnest(g.scopes) requested(scope_code)
         where not exists(
             select 1 from staff_access_purpose_scope p
              where p.purpose_code=g.purpose_code and p.scope_code=requested.scope_code
         )
    ) then
        raise exception 'legacy staff access grant has an unsupported purpose/scope combination' using errcode='23514';
    end if;
end $$;

alter table staff_access_grant drop constraint ck_staff_access_grant_scope;
alter table staff_access_grant add constraint ck_staff_access_grant_scope check (
    cardinality(scopes) > 0 and scopes <@ array[
        'CONSENT_READ','CONSENT_WRITE','TRUSTED_CONTACT_READ','TRUSTED_CONTACT_WRITE',
        'FINANCIAL_INTENT_READ','CASE_READ','CASE_ASSIGN','CASE_REVIEW','CASE_GUIDANCE',
        'CASE_NOTE','CASE_FOLLOW_UP','PRIVACY_REQUEST_WRITE','ALERT_READ','ALERT_RESPOND',
        'PROTECTION_ENROLLMENT_READ'
    ]::varchar[]
);

create or replace function validate_staff_access_purpose_scopes() returns trigger language plpgsql as $$
begin
    if exists(
        select 1 from unnest(new.scopes) as requested(scope_code)
         where not exists(
             select 1 from staff_access_purpose_scope p
              where p.purpose_code=new.purpose_code and p.scope_code=requested.scope_code
         )
    ) then
        raise exception 'staff access purpose/scope mismatch' using errcode='23514';
    end if;
    return new;
end $$;
create trigger trg_staff_access_purpose_scopes before insert or update of purpose_code,scopes on staff_access_grant
for each row execute function validate_staff_access_purpose_scopes();

alter table staff_access_grant_event drop constraint ck_staff_access_grant_event_type;
alter table staff_access_grant_event add constraint ck_staff_access_grant_event_type check (
    event_type in ('GRANTED','ACCESS_USED','REVOKED','EVALUATED','EXPIRED')
);

create table staff_access_decision_audit_event (
    evaluation_id uuid primary key,
    grant_id uuid references staff_access_grant(grant_id),
    staff_principal_id uuid not null,
    customer_id varchar(80) not null references customer_profile(customer_id),
    purpose_code varchar(60) not null,
    scope_code varchar(60) not null,
    allowed boolean not null,
    decision_code varchar(60) not null,
    resource_type varchar(60),
    resource_id varchar(120),
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    occurred_at timestamptz not null
);
create index idx_staff_access_decision_customer
    on staff_access_decision_audit_event(customer_id,occurred_at,evaluation_id);
create trigger trg_staff_access_decision_append_only before update or delete on staff_access_decision_audit_event
for each row execute function reject_protected_event_mutation();

-- V40 이전 이벤트는 당시 snapshot을 복원할 수 없으므로 현재값으로 가장하지 않는다.
alter table trusted_contact_event add column snapshot_accuracy varchar(20) not null default 'EXACT';
drop trigger trg_trusted_contact_event_append_only on trusted_contact_event;
update trusted_contact_event
   set status_snapshot='LEGACY_UNKNOWN', scope_snapshot='{}'::varchar[], snapshot_accuracy='LEGACY_UNKNOWN'
 where occurred_at < coalesce(
    (select installed_on at time zone current_setting('TIMEZONE')
       from flyway_schema_history where version='40' and success order by installed_rank desc limit 1),
    '-infinity'::timestamptz
 );
create trigger trg_trusted_contact_event_append_only before update or delete on trusted_contact_event
for each row execute function reject_protected_event_mutation();
alter table trusted_contact_event add constraint ck_trusted_contact_snapshot_accuracy
    check(snapshot_accuracy in ('EXACT','LEGACY_UNKNOWN'));

-- 승인된 안내계획은 추가 전용이다.
create trigger trg_operational_guidance_plan_immutable before update or delete on operational_guidance_plan
for each row execute function reject_protected_event_mutation();

-- 게시·퇴역 정책 본문은 불변이며, ACTIVE -> RETIRED 상태 전환만 허용한다.
create or replace function guard_published_detection_policy() returns trigger language plpgsql as $$
begin
    if tg_op='DELETE' then
        raise exception 'published detection policy cannot be deleted' using errcode='42501';
    end if;
    if old.status='DRAFT' then return new; end if;
    if old.status='ACTIVE' and new.status='RETIRED'
       and new.policy_id=old.policy_id and new.version_code=old.version_code
       and new.description=old.description and new.rules=old.rules and new.rules_hash=old.rules_hash
       and new.based_on_policy_id is not distinct from old.based_on_policy_id
       and new.created_by=old.created_by and new.created_at=old.created_at
       and new.published_by=old.published_by and new.published_at=old.published_at
       and new.row_version=old.row_version then
        return new;
    end if;
    raise exception 'active or retired detection policy is immutable' using errcode='42501';
end $$;
create trigger trg_published_detection_policy_guard before update or delete on detection_policy_version
for each row execute function guard_published_detection_policy();

create or replace function audit_detection_policy_retirement() returns trigger language plpgsql as $$
begin
    if old.status='ACTIVE' and new.status='RETIRED' then
        insert into detection_policy_event(event_id,policy_id,event_type,actor_subject,from_status,to_status,rules_hash,occurred_at)
        values(gen_random_uuid(),new.policy_id,'POLICY_RETIRED','DATABASE_POLICY_GUARD','ACTIVE','RETIRED',new.rules_hash,clock_timestamp());
    end if;
    return new;
end $$;
create trigger trg_detection_policy_retirement_audit after update of status on detection_policy_version
for each row execute function audit_detection_policy_retirement();

-- 최초 응답을 그대로 재생하는 고객 변경 명령 저장소.
create table customer_mutation_command (
    command_scope varchar(180) not null,
    idempotency_key_hash char(64) not null,
    request_hash char(64) not null,
    result_payload jsonb,
    created_at timestamptz not null,
    completed_at timestamptz,
    primary key(command_scope,idempotency_key_hash),
    constraint ck_customer_mutation_hashes check (
        idempotency_key_hash ~ '^[0-9a-f]{64}$' and request_hash ~ '^[0-9a-f]{64}$'
    )
);

-- 소유 고객이 다른 참조를 DB에서도 만들 수 없도록 복합 소유권 FK를 추가한다.
alter table customer_connection add constraint uq_connection_owner_institution
    unique(connection_id,customer_id,institution_id);
alter table customer_account_snapshot add constraint uq_account_owner_pair unique(account_id,customer_id);
alter table customer_account_snapshot add constraint fk_account_owned_connection
    foreign key(connection_id,customer_id,institution_id)
    references customer_connection(connection_id,customer_id,institution_id);
alter table financial_counterparty_snapshot add constraint uq_counterparty_owner_pair
    unique(counterparty_id,customer_id);
alter table financial_transaction_snapshot add constraint uq_transaction_owner_pair
    unique(transaction_id,customer_id);
alter table financial_transaction_snapshot add constraint fk_transaction_owned_account
    foreign key(account_id,customer_id) references customer_account_snapshot(account_id,customer_id);
alter table financial_transaction_snapshot add constraint fk_transaction_owned_counterparty
    foreign key(counterparty_id,customer_id) references financial_counterparty_snapshot(counterparty_id,customer_id);
alter table account_display_setting add constraint fk_account_display_owned_account
    foreign key(account_id,customer_id) references customer_account_snapshot(account_id,customer_id);
alter table account_display_setting_event add constraint fk_account_display_event_owned_account
    foreign key(account_id,customer_id) references customer_account_snapshot(account_id,customer_id);
alter table customer_transaction_preference add constraint fk_transaction_preference_owned_transaction
    foreign key(transaction_id,customer_id) references financial_transaction_snapshot(transaction_id,customer_id);
alter table customer_transaction_preference_event add constraint fk_transaction_preference_event_owned_transaction
    foreign key(transaction_id,customer_id) references financial_transaction_snapshot(transaction_id,customer_id);
alter table customer_asset_calendar_snapshot add constraint fk_asset_calendar_owned_account
    foreign key(account_id,customer_id) references customer_account_snapshot(account_id,customer_id);

alter table account_recurring_counterparty_snapshot add column customer_id varchar(80);
drop trigger trg_recurring_counterparty_append_only on account_recurring_counterparty_snapshot;
update account_recurring_counterparty_snapshot r set customer_id=a.customer_id
  from customer_account_snapshot a where a.account_id=r.account_id;
create trigger trg_recurring_counterparty_append_only before update or delete on account_recurring_counterparty_snapshot
for each row execute function reject_protected_event_mutation();
alter table account_recurring_counterparty_snapshot alter column customer_id set not null;
alter table account_recurring_counterparty_snapshot add constraint fk_recurring_owned_account
    foreign key(account_id,customer_id) references customer_account_snapshot(account_id,customer_id);
alter table account_recurring_counterparty_snapshot add constraint fk_recurring_owned_counterparty
    foreign key(counterparty_id,customer_id) references financial_counterparty_snapshot(counterparty_id,customer_id);

alter table customer_account_group_snapshot add constraint uq_group_owner_pair unique(group_id,customer_id);
alter table customer_account_group_member_snapshot add column customer_id varchar(80);
drop trigger trg_account_group_member_append_only on customer_account_group_member_snapshot;
update customer_account_group_member_snapshot m set customer_id=g.customer_id
  from customer_account_group_snapshot g where g.group_id=m.group_id;
create trigger trg_account_group_member_append_only before update or delete on customer_account_group_member_snapshot
for each row execute function reject_protected_event_mutation();
alter table customer_account_group_member_snapshot alter column customer_id set not null;
alter table customer_account_group_member_snapshot add constraint fk_group_member_owned_group
    foreign key(group_id,customer_id) references customer_account_group_snapshot(group_id,customer_id);
alter table customer_account_group_member_snapshot add constraint fk_group_member_owned_account
    foreign key(account_id,customer_id) references customer_account_snapshot(account_id,customer_id);

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke update,delete on operational_guidance_plan,detection_policy_version,
            staff_access_decision_audit_event,customer_mutation_command from alzswell_app;
        grant select,insert on staff_access_decision_audit_event,customer_mutation_command to alzswell_app;
        grant update(result_payload,completed_at) on customer_mutation_command to alzswell_app;
        grant update on detection_policy_version to alzswell_app;
    end if;
end $$;

comment on table staff_access_purpose_scope is '직원 접근권 목적별 최소 허용 scope 매트릭스';
comment on table staff_access_decision_audit_event is '허용과 거부를 모두 보존하는 직원 접근 판단 감사이력';
comment on table customer_mutation_command is '고객 변경 API의 멱등키 해시와 최초 응답 저장소';
