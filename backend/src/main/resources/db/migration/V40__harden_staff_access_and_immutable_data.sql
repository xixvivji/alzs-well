create table staff_access_grant (
    grant_id uuid primary key,
    staff_principal_id uuid not null references auth_principal(principal_id),
    customer_id varchar(80) not null references customer_profile(customer_id),
    purpose_code varchar(60) not null,
    scopes varchar(60)[] not null,
    status varchar(20) not null,
    granted_by uuid references auth_principal(principal_id),
    granted_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    revocation_reason varchar(300),
    idempotency_key_hash char(64) not null,
    request_hash char(64) not null,
    row_version bigint not null default 1,
    constraint ck_staff_access_grant_status check (status in ('ACTIVE','REVOKED','EXPIRED')),
    constraint ck_staff_access_grant_period check (expires_at > granted_at),
    constraint ck_staff_access_grant_revoke check (
        (status = 'ACTIVE' and revoked_at is null and revocation_reason is null)
        or (status = 'EXPIRED' and revoked_at is null and revocation_reason is null)
        or (status = 'REVOKED' and revoked_at is not null and revocation_reason is not null)
    ),
    constraint ck_staff_access_grant_scope check (
        cardinality(scopes) > 0 and scopes <@ array[
            'CONSENT_READ','CONSENT_WRITE','TRUSTED_CONTACT_READ','TRUSTED_CONTACT_WRITE',
            'FINANCIAL_INTENT_READ','CASE_READ','CASE_ASSIGN','CASE_REVIEW','CASE_GUIDANCE',
            'CASE_NOTE','CASE_FOLLOW_UP','PRIVACY_REQUEST_WRITE'
        ]::varchar[]
    )
);

create unique index uq_staff_access_grant_active
    on staff_access_grant(staff_principal_id, customer_id, purpose_code)
    where status = 'ACTIVE';
create unique index uq_staff_access_grant_idempotency
    on staff_access_grant(customer_id, idempotency_key_hash);
create index idx_staff_access_grant_evaluation
    on staff_access_grant(staff_principal_id, customer_id, status, expires_at);

create table staff_access_grant_event (
    event_id uuid primary key,
    grant_id uuid not null references staff_access_grant(grant_id),
    event_type varchar(30) not null,
    status_snapshot varchar(20) not null,
    scopes_snapshot varchar(60)[] not null,
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    detail jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null,
    constraint ck_staff_access_grant_event_type check (
        event_type in ('GRANTED','ACCESS_USED','REVOKED','EVALUATED')
    )
);
create index idx_staff_access_grant_event_history
    on staff_access_grant_event(grant_id, occurred_at, event_id);
create trigger trg_staff_access_grant_event_append_only before update or delete on staff_access_grant_event
for each row execute function reject_protected_event_mutation();

insert into auth_permission(permission_code, description) values
    ('STAFF_ACCESS_GRANT_READ', '고객별 직원 접근권 조회'),
    ('STAFF_ACCESS_GRANT_WRITE', '고객별 직원 접근권 생성·철회'),
    ('STAFF_ACCESS_EVALUATE', '직원·고객·범위 접근 가능성 평가');
insert into auth_role_permission(role_code, permission_code) values
    ('DETECTION_ADMIN', 'STAFF_ACCESS_GRANT_READ'),
    ('DETECTION_ADMIN', 'STAFF_ACCESS_GRANT_WRITE'),
    ('DETECTION_ADMIN', 'STAFF_ACCESS_EVALUATE');

alter table trusted_contact_event add column status_snapshot varchar(30);
alter table trusted_contact_event add column scope_snapshot varchar(60)[];
update trusted_contact_event e
   set status_snapshot = t.status,
       scope_snapshot = coalesce((select array_agg(s.scope_code order by s.scope_code)
                                    from trusted_contact_scope s where s.contact_id=t.contact_id), '{}')
  from trusted_contact t where t.contact_id=e.contact_id;
alter table trusted_contact_event alter column status_snapshot set not null;
alter table trusted_contact_event alter column scope_snapshot set not null;

alter table financial_intent_command rename column idempotency_key to idempotency_key_hash;
alter table financial_intent_command drop constraint financial_intent_command_pkey;
update financial_intent_command
   set idempotency_key_hash = md5(idempotency_key_hash) || md5('alzs-well:' || idempotency_key_hash);
alter table financial_intent_command alter column idempotency_key_hash type char(64);
alter table financial_intent_command add primary key(command_scope, idempotency_key_hash);

alter table customer_privacy_request
    add constraint fk_privacy_request_customer foreign key(customer_id) references customer_profile(customer_id);

alter table auth_login_event drop constraint ck_auth_login_event_outcome;
alter table auth_login_event add constraint ck_auth_login_event_outcome
    check (outcome in ('PENDING','SUCCEEDED','FAILED','RATE_LIMITED','ERROR'));
drop index idx_auth_login_event_rate_limit;
create index idx_auth_login_event_rate_limit on auth_login_event(login_id_hash, occurred_at desc)
    where outcome in ('PENDING','FAILED');

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        execute format(
            'alter default privileges for role %I in schema public revoke update, delete on tables from alzswell_app',
            current_user
        );
        revoke update, delete on
            decision_audit, case_note, operational_alert_context_event, operational_alert_audit_event,
            operational_case_review_event, operational_case_activity, operational_case_note,
            operational_case_follow_up_event, customer_consent_event, trusted_contact_event,
            consent_access_audit_event, detection_policy_event, feature_flag_change_event,
            customer_privacy_request_event, audit_export_request_event, financial_intent_event,
            financial_intent_revision, knowledge_document, knowledge_document_version,
            knowledge_passage, staff_access_grant_event
        from alzswell_app;
        grant select, insert, update on staff_access_grant to alzswell_app;
        grant select, insert on staff_access_grant_event to alzswell_app;
    end if;
end $$;

comment on table staff_access_grant is '직원 principal과 고객·목적·범위·만료를 결합한 최소권한 접근권';
comment on table staff_access_grant_event is '접근권 생성·사용·평가·철회의 추가 전용 감사이력';
comment on column financial_intent_command.idempotency_key_hash is '원문을 저장하지 않는 Idempotency-Key SHA-256';
comment on column auth_login_event.outcome is 'PENDING은 DB 연결을 놓고 자격증명을 검증하기 전에 원자적으로 예약한 로그인 시도';
