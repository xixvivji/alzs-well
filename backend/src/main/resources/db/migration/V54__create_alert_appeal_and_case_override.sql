-- 고객 이의신청과 직원 재검토는 실제 금융조치 없이 append-only 기록과 검토 상태만 변경한다.
create unique index uq_operational_alert_owner_ref on operational_alert(customer_id,alert_id);

create table operational_alert_appeal (
    appeal_id uuid primary key,
    alert_id uuid not null,
    customer_id varchar(80) not null,
    reason_code varchar(40) not null,
    statement varchar(300) not null,
    previous_state varchar(30) not null,
    resulting_state varchar(30) not null,
    case_id uuid not null references operational_protection_case(case_id),
    status varchar(20) not null,
    request_hash char(64) not null,
    idempotency_key_hash char(64) not null,
    actor_customer_id varchar(80) not null,
    created_at timestamptz not null,
    integrity_hash char(64) not null,
    foreign key(customer_id,alert_id) references operational_alert(customer_id,alert_id),
    constraint uq_alert_appeal_once unique(alert_id),
    constraint uq_alert_appeal_idempotency unique(alert_id,idempotency_key_hash),
    constraint ck_alert_appeal_reason check(reason_code in ('DISAGREE_WITH_RESULT','MISSING_CONTEXT','REQUEST_HUMAN_REVIEW')),
    constraint ck_alert_appeal_transition check(resulting_state='BANK_REVIEW'),
    constraint ck_alert_appeal_status check(status='SUBMITTED'),
    constraint ck_alert_appeal_request_hash check(request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_alert_appeal_integrity_hash check(integrity_hash ~ '^[0-9a-f]{64}$')
);
create index idx_alert_appeal_customer on operational_alert_appeal(customer_id,created_at,appeal_id);
create trigger trg_alert_appeal_append_only before update or delete on operational_alert_appeal
for each row execute function reject_protected_event_mutation();

create table operational_case_override_event (
    override_event_id uuid primary key,
    case_id uuid not null references operational_protection_case(case_id),
    reason_code varchar(40) not null,
    rationale varchar(500) not null,
    policy_version varchar(80) not null,
    previous_status varchar(30) not null,
    resulting_status varchar(30) not null,
    reviewer_principal_id uuid not null references auth_principal(principal_id),
    request_hash char(64) not null,
    idempotency_key_hash char(64) not null,
    created_at timestamptz not null,
    integrity_hash char(64) not null,
    constraint uq_case_override_idempotency unique(case_id,idempotency_key_hash),
    constraint ck_case_override_reason check(reason_code in ('FALSE_POSITIVE_REVIEW','MISSING_CONTEXT_REVIEW','POLICY_EXCEPTION_REVIEW')),
    constraint ck_case_override_transition check(previous_status in ('GUIDANCE_APPROVED','COMPLETED') and resulting_status='IN_REVIEW'),
    constraint ck_case_override_request_hash check(request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_case_override_integrity_hash check(integrity_hash ~ '^[0-9a-f]{64}$')
);
create index idx_case_override_timeline on operational_case_override_event(case_id,created_at,override_event_id);
create trigger trg_case_override_append_only before update or delete on operational_case_override_event
for each row execute function reject_protected_event_mutation();

insert into auth_permission(permission_code,description) values
('ALERT_APPEAL','자신의 운영형 경보에 사람의 재검토 요청'),
('STAFF_CASE_OVERRIDE','배정 사건의 정책 결과를 사유와 함께 재검토');
insert into auth_role_permission(role_code,permission_code) values
('CUSTOMER','ALERT_APPEAL'),
('PROTECTION_STAFF','STAFF_CASE_OVERRIDE');
insert into staff_access_purpose_scope(purpose_code,scope_code) values
('PROTECTION_CASE_MANAGEMENT','CASE_OVERRIDE');

alter table staff_access_grant drop constraint ck_staff_access_grant_scope;
alter table staff_access_grant add constraint ck_staff_access_grant_scope check (
    cardinality(scopes) > 0 and scopes <@ array[
        'CONSENT_READ','CONSENT_WRITE','TRUSTED_CONTACT_READ','TRUSTED_CONTACT_WRITE',
        'FINANCIAL_INTENT_READ','CASE_READ','CASE_ASSIGN','CASE_REVIEW','CASE_GUIDANCE',
        'CASE_NOTE','CASE_FOLLOW_UP','CASE_OVERRIDE','PRIVACY_REQUEST_WRITE','ALERT_READ','ALERT_RESPOND',
        'PROTECTION_ENROLLMENT_READ'
    ]::varchar[]
);

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  revoke update,delete on operational_alert_appeal,operational_case_override_event from alzswell_app;
  grant select,insert on operational_alert_appeal,operational_case_override_event to alzswell_app;
 end if;
end $$;

comment on table operational_alert_appeal is '고객이 사람의 재검토를 요청한 추가 전용 이의신청';
comment on table operational_case_override_event is '배정 직원이 정책 결과를 직접 실행하지 않고 사건을 재검토로 돌린 불변 이력';
