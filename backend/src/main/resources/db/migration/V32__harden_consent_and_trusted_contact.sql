create or replace function reject_protected_event_mutation()
returns trigger language plpgsql as $$
begin
    raise exception '% is append-only', tg_table_name;
end;
$$;

create trigger trg_alert_context_event_append_only before update or delete on operational_alert_context_event
for each row execute function reject_protected_event_mutation();
create trigger trg_alert_audit_event_append_only before update or delete on operational_alert_audit_event
for each row execute function reject_protected_event_mutation();
create trigger trg_case_review_event_append_only before update or delete on operational_case_review_event
for each row execute function reject_protected_event_mutation();
create trigger trg_customer_consent_event_append_only before update or delete on customer_consent_event
for each row execute function reject_protected_event_mutation();
create trigger trg_trusted_contact_event_append_only before update or delete on trusted_contact_event
for each row execute function reject_protected_event_mutation();

alter table customer_consent add column idempotency_key_hash varchar(64);
alter table customer_consent add column request_hash varchar(64);
create unique index uq_customer_consent_idempotency on customer_consent(customer_id,idempotency_key_hash)
where idempotency_key_hash is not null;

create or replace function validate_consent_scope_for_purpose()
returns trigger language plpgsql as $$
declare consent_purpose varchar(50);
begin
    select purpose_code into consent_purpose from customer_consent where consent_id=new.consent_id;
    if not ((consent_purpose='FINANCIAL_ANALYSIS' and new.scope_code in ('ACCOUNT_SUMMARY','TRANSACTION_SUMMARY','BASELINE_SIGNAL'))
        or (consent_purpose='PROTECTION_GUIDANCE' and new.scope_code in ('BASELINE_SIGNAL','PROTECTION_CASE'))
        or (consent_purpose='TRUSTED_CONTACT_DISCLOSURE' and new.scope_code='CONTACT_MINIMUM')) then
        raise exception 'scope % is not allowed for purpose %', new.scope_code, consent_purpose;
    end if;
    return new;
end;
$$;
create trigger trg_validate_consent_scope before insert or update on customer_consent_scope
for each row execute function validate_consent_scope_for_purpose();

alter table trusted_contact drop constraint ck_trusted_contact_recipient;
alter table trusted_contact drop constraint ck_trusted_contact_status;
alter table trusted_contact drop constraint ck_trusted_contact_revocation;
alter table trusted_contact add column acceptance_status varchar(30) not null default 'PENDING_ACCEPTANCE';
alter table trusted_contact add column idempotency_key_hash varchar(64);
alter table trusted_contact add column request_hash varchar(64);
update trusted_contact set recipient_accepted=false;
alter table trusted_contact add constraint ck_trusted_contact_recipient_unverified
    check (not recipient_accepted and acceptance_status in ('PENDING_ACCEPTANCE','UNVERIFIED'));
alter table trusted_contact add constraint ck_trusted_contact_masked check (position('*' in masked_contact)>0);
alter table trusted_contact add constraint ck_trusted_contact_status
    check (status in ('ACTIVE','REVOKED','REVOKED_BY_CONSENT'));
alter table trusted_contact add constraint ck_trusted_contact_revocation check (
    (status='ACTIVE' and revoked_at is null and revocation_reason is null) or
    (status in ('REVOKED','REVOKED_BY_CONSENT') and revoked_at is not null and revocation_reason is not null)
);
create unique index uq_trusted_contact_idempotency on trusted_contact(customer_id,idempotency_key_hash)
where idempotency_key_hash is not null;

alter table customer_consent_event add column actor_principal_id uuid;
alter table customer_consent_event add column actor_customer_id varchar(80);
alter table customer_consent_event add column actor_session_id uuid;
alter table customer_consent_event add column actor_type varchar(20) not null default 'LEGACY';
alter table trusted_contact_event add column actor_principal_id uuid;
alter table trusted_contact_event add column actor_customer_id varchar(80);
alter table trusted_contact_event add column actor_session_id uuid;
alter table trusted_contact_event add column actor_type varchar(20) not null default 'LEGACY';
alter table trusted_contact_event drop constraint ck_trusted_contact_event_type;
alter table trusted_contact_event add constraint ck_trusted_contact_event_type
    check (event_type in ('CREATED','UPDATED','REVOKED','REVOKED_BY_CONSENT'));

create table consent_access_audit_event (
    evaluation_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id),
    consent_id uuid references customer_consent(consent_id),
    event_type varchar(40) not null check (event_type in ('CONSENT_READ','CONSENT_HISTORY_READ','DISCLOSURE_EVALUATED')),
    actor_principal_id uuid,
    actor_customer_id varchar(80),
    actor_session_id uuid,
    actor_type varchar(20) not null,
    policy_version varchar(50),
    request_hash varchar(64) not null,
    decision varchar(40),
    detail jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null
);
create index idx_consent_access_audit_customer on consent_access_audit_event(customer_id,occurred_at,evaluation_id);
create trigger trg_consent_access_audit_append_only before update or delete on consent_access_audit_event
for each row execute function reject_protected_event_mutation();

do $$
begin
    if exists(select 1 from pg_roles where rolname='alzswell_app') then
        revoke update, delete on operational_alert_context_event, operational_alert_audit_event,
            operational_case_review_event, customer_consent_event, trusted_contact_event,
            consent_access_audit_event from alzswell_app;
        grant select, insert on consent_access_audit_event to alzswell_app;
    end if;
end $$;

comment on table consent_access_audit_event is '동의 조회와 최소정보 제공 정책 평가의 추가 전용 감사이력';
