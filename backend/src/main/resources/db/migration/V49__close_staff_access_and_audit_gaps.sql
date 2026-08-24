-- V49: 직원 접근권, 멱등 응답, 사건 감사 snapshot, 탐지정책 상태전이를 DB에서도 강제한다.

-- 직원 접근권을 받을 수 있는 역할을 업무 목적별로 고정한다.
create table staff_access_purpose_role (
    purpose_code varchar(60) not null,
    role_code varchar(60) not null references auth_role(role_code),
    primary key(purpose_code, role_code)
);

insert into staff_access_purpose_role values
    ('ALERT_MANAGEMENT','DETECTION_ADMIN'),
    ('CUSTOMER_CONSENT_MANAGEMENT','PROTECTION_STAFF'),
    ('FINANCIAL_INTENT_REVIEW','PROTECTION_STAFF'),
    ('PRIVACY_REQUEST_ASSISTANCE','PROTECTION_STAFF'),
    ('PROTECTION_CASE_MANAGEMENT','PROTECTION_STAFF'),
    ('PROTECTION_ENROLLMENT_REVIEW','PROTECTION_STAFF'),
    ('TRUSTED_CONTACT_MANAGEMENT','PROTECTION_STAFF');

create or replace function validate_staff_access_principal_role() returns trigger language plpgsql as $$
begin
    if not exists(
        select 1
          from auth_principal p
          join auth_principal_role r using(principal_id)
          join staff_access_purpose_role allowed on allowed.role_code=r.role_code
         where p.principal_id=new.staff_principal_id
           and p.status='ACTIVE'
           and allowed.purpose_code=new.purpose_code
    ) then
        raise exception 'staff principal is not eligible for access purpose' using errcode='23514';
    end if;
    return new;
end $$;
create trigger trg_staff_access_principal_role
before insert or update of staff_principal_id,purpose_code on staff_access_grant
for each row execute function validate_staff_access_principal_role();

-- 거부 감사는 존재하지 않는 고객 식별자를 대상으로 한 탐색 시도도 보존해야 한다.
-- 업무 데이터가 아니라 시도 당시 입력 snapshot이므로 고객 master FK에 종속시키지 않는다.
alter table staff_access_decision_audit_event
    drop constraint if exists staff_access_decision_audit_event_customer_id_fkey;

-- grant의 고객·직원·목적·scope·기간·hash는 생성 후 바꿀 수 없다.
create or replace function guard_staff_access_grant_transition() returns trigger language plpgsql as $$
begin
    if new.grant_id is distinct from old.grant_id
       or new.staff_principal_id is distinct from old.staff_principal_id
       or new.customer_id is distinct from old.customer_id
       or new.purpose_code is distinct from old.purpose_code
       or new.scopes is distinct from old.scopes
       or new.granted_by is distinct from old.granted_by
       or new.granted_at is distinct from old.granted_at
       or new.expires_at is distinct from old.expires_at
       or new.idempotency_key_hash is distinct from old.idempotency_key_hash
       or new.request_hash is distinct from old.request_hash then
        raise exception 'staff access grant identity is immutable' using errcode='42501';
    end if;
    if old.status <> 'ACTIVE' or new.row_version <> old.row_version + 1 then
        raise exception 'invalid staff access grant transition' using errcode='42501';
    end if;
    if new.status='EXPIRED'
       and new.revoked_at is null and new.revocation_reason is null then
        return new;
    end if;
    if new.status='REVOKED'
       and new.revoked_at is not null and new.revocation_reason is not null then
        return new;
    end if;
    raise exception 'invalid staff access grant transition' using errcode='42501';
end $$;
create trigger trg_staff_access_grant_transition
before update on staff_access_grant
for each row execute function guard_staff_access_grant_transition();

-- 직원 접근 이벤트도 당시 고객·목적·직원을 snapshot으로 보존한다.
drop trigger trg_staff_access_grant_event_append_only on staff_access_grant_event;
alter table staff_access_grant_event
    add column customer_id_snapshot varchar(80),
    add column purpose_code_snapshot varchar(60),
    add column staff_principal_id_snapshot uuid,
    add column snapshot_accuracy varchar(20) not null default 'LEGACY_CURRENT';
update staff_access_grant_event e
   set customer_id_snapshot=g.customer_id,
       purpose_code_snapshot=g.purpose_code,
       staff_principal_id_snapshot=g.staff_principal_id
  from staff_access_grant g where g.grant_id=e.grant_id;
alter table staff_access_grant_event alter column customer_id_snapshot set not null;
alter table staff_access_grant_event alter column purpose_code_snapshot set not null;
alter table staff_access_grant_event alter column staff_principal_id_snapshot set not null;
alter table staff_access_grant_event alter column snapshot_accuracy drop default;
alter table staff_access_grant_event add constraint ck_staff_access_event_snapshot_accuracy
    check(snapshot_accuracy in ('EXACT','LEGACY_CURRENT'));
create trigger trg_staff_access_grant_event_append_only before update or delete on staff_access_grant_event
for each row execute function reject_protected_event_mutation();

-- 과거 사건 배정은 현재 사건 상태를 참조하지 않고 이벤트 당시 snapshot만 반환한다.
alter table operational_case_activity
    add column previous_status varchar(30),
    add column resulting_status varchar(30),
    add column snapshot_accuracy varchar(20) not null default 'LEGACY_UNKNOWN';
alter table operational_case_activity alter column snapshot_accuracy drop default;
alter table operational_case_activity add constraint ck_case_activity_snapshot_accuracy
    check(snapshot_accuracy in ('EXACT','LEGACY_UNKNOWN'));

-- 완료된 멱등 응답은 최초 NULL -> 결과 전환 한 번만 허용한다.
create or replace function guard_customer_mutation_completion() returns trigger language plpgsql as $$
begin
    if new.command_scope is distinct from old.command_scope
       or new.idempotency_key_hash is distinct from old.idempotency_key_hash
       or new.request_hash is distinct from old.request_hash
       or new.created_at is distinct from old.created_at
       or old.result_payload is not null
       or old.completed_at is not null
       or new.result_payload is null
       or new.completed_at is null then
        raise exception 'idempotent command result is immutable after completion' using errcode='42501';
    end if;
    return new;
end $$;
create trigger trg_customer_mutation_completion
before update on customer_mutation_command
for each row execute function guard_customer_mutation_completion();

-- 초안만 직접 생성할 수 있고 정책 상태전이는 제한된 필드만 변경한다.
create or replace function guard_detection_policy_insert() returns trigger language plpgsql as $$
begin
    if new.status <> 'DRAFT' or new.published_by is not null or new.published_at is not null then
        raise exception 'detection policy must be inserted as draft' using errcode='42501';
    end if;
    return new;
end $$;
create trigger trg_detection_policy_insert_guard before insert on detection_policy_version
for each row execute function guard_detection_policy_insert();

create or replace function guard_published_detection_policy() returns trigger language plpgsql as $$
begin
    if tg_op='DELETE' then
        raise exception 'detection policy cannot be deleted' using errcode='42501';
    end if;
    if old.status='DRAFT' and new.status='DRAFT'
       and new.policy_id=old.policy_id and new.version_code=old.version_code
       and new.based_on_policy_id is not distinct from old.based_on_policy_id
       and new.created_by=old.created_by and new.created_at=old.created_at
       and new.published_by is null and new.published_at is null
       and new.row_version=old.row_version+1 then
        return new;
    end if;
    if old.status='DRAFT' and new.status='ACTIVE'
       and new.policy_id=old.policy_id and new.description=old.description
       and new.rules=old.rules and new.rules_hash=old.rules_hash
       and new.based_on_policy_id is not distinct from old.based_on_policy_id
       and new.created_by=old.created_by and new.created_at=old.created_at
       and new.published_by is not null and new.published_at is not null
       and new.row_version=old.row_version+1 then
        return new;
    end if;
    if old.status='ACTIVE' and new.status='RETIRED'
       and new.policy_id=old.policy_id and new.version_code=old.version_code
       and new.description=old.description and new.rules=old.rules and new.rules_hash=old.rules_hash
       and new.based_on_policy_id is not distinct from old.based_on_policy_id
       and new.created_by=old.created_by and new.created_at=old.created_at
       and new.published_by=old.published_by and new.published_at=old.published_at
       and new.row_version=old.row_version then
        return new;
    end if;
    raise exception 'invalid or unaudited detection policy transition' using errcode='42501';
end $$;

create or replace function audit_detection_policy_activation() returns trigger language plpgsql as $$
begin
    if old.status='DRAFT' and new.status='ACTIVE' then
        insert into detection_policy_event(event_id,policy_id,event_type,actor_subject,from_status,to_status,rules_hash,occurred_at)
        values(gen_random_uuid(),new.policy_id,'POLICY_ACTIVATED_DB_GUARD','DATABASE_POLICY_GUARD',
               'DRAFT','ACTIVE',new.rules_hash,clock_timestamp());
    end if;
    return new;
end $$;
create trigger trg_detection_policy_activation_audit after update of status on detection_policy_version
for each row execute function audit_detection_policy_activation();

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke update,delete on staff_access_grant from alzswell_app;
        grant select,insert on staff_access_grant to alzswell_app;
        grant update(status,revoked_at,revocation_reason,row_version) on staff_access_grant to alzswell_app;

        revoke insert,update,delete on staff_access_purpose_scope,staff_access_purpose_role from alzswell_app;
        grant select on staff_access_purpose_scope,staff_access_purpose_role to alzswell_app;

        revoke update,delete on customer_mutation_command from alzswell_app;
        grant select,insert on customer_mutation_command to alzswell_app;
        grant update(result_payload,completed_at) on customer_mutation_command to alzswell_app;

        revoke delete on detection_policy_version from alzswell_app;
        grant select,insert,update on detection_policy_version to alzswell_app;
    end if;
end $$;

comment on table staff_access_purpose_role is '직원 접근권 목적별 수임 가능 역할 매트릭스';
comment on column staff_access_decision_audit_event.customer_id is '정책 평가 시도 당시 고객 식별자 snapshot; 미존재 고객 탐색 시도도 감사하므로 master FK를 두지 않음';
comment on column operational_case_activity.snapshot_accuracy is 'EXACT 또는 V49 이전 LEGACY_UNKNOWN';
comment on column staff_access_grant_event.snapshot_accuracy is 'EXACT 또는 V49 이전 현재값 기반 LEGACY_CURRENT';
