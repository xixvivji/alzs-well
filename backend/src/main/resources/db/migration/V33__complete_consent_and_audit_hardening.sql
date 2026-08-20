create or replace function reject_consent_purpose_mutation()
returns trigger language plpgsql as $$
begin
    if old.purpose_code is distinct from new.purpose_code then
        raise exception 'customer_consent purpose_code is immutable';
    end if;
    return new;
end;
$$;

do $$
begin
    if exists (
        select 1
          from customer_consent c
          join customer_consent_scope s on s.consent_id = c.consent_id
         where not (
             (c.purpose_code = 'FINANCIAL_ANALYSIS'
                 and s.scope_code in ('ACCOUNT_SUMMARY', 'TRANSACTION_SUMMARY', 'BASELINE_SIGNAL'))
             or (c.purpose_code = 'PROTECTION_GUIDANCE'
                 and s.scope_code in ('BASELINE_SIGNAL', 'PROTECTION_CASE'))
             or (c.purpose_code = 'TRUSTED_CONTACT_DISCLOSURE'
                 and s.scope_code = 'CONTACT_MINIMUM')
         )
    ) then
        raise exception 'existing customer_consent purpose/scope combination is invalid';
    end if;
end;
$$;

create trigger trg_customer_consent_purpose_immutable
before update of purpose_code on customer_consent
for each row execute function reject_consent_purpose_mutation();

alter table trusted_contact_event alter column event_type type varchar(40);
alter table trusted_contact_event drop constraint ck_trusted_contact_event_type;
alter table trusted_contact_event add constraint ck_trusted_contact_event_type
    check (event_type in ('CREATED','UPDATED','REVOKED','REVOKED_BY_CONSENT','CONTACT_MASK_NORMALIZED'));

with normalized as (
    update trusted_contact
       set masked_contact = '000-****-' || lpad(right(regexp_replace(masked_contact, '[^0-9]', '', 'g'), 4), 4, '0'),
           row_version = row_version + 1,
           updated_at = now()
     where masked_contact !~ '^[0-9+]{2,4}-[*]{3,8}-[0-9]{2,4}$'
    returning contact_id, row_version, updated_at
)
insert into trusted_contact_event (
    event_id, contact_id, event_type, actor_id, reason, occurred_at, row_version,
    actor_principal_id, actor_customer_id, actor_session_id, actor_type
)
select gen_random_uuid(), contact_id, 'CONTACT_MASK_NORMALIZED', 'SYSTEM_MIGRATION_V33',
       'LEGACY_MASK_NORMALIZED', updated_at, row_version, null, null, null, 'SYSTEM'
  from normalized;

alter table trusted_contact drop constraint ck_trusted_contact_masked;
alter table trusted_contact add constraint ck_trusted_contact_masked
    check (masked_contact ~ '^[0-9+]{2,4}-[*]{3,8}-[0-9]{2,4}$');

alter table consent_access_audit_event drop constraint consent_access_audit_event_event_type_check;
alter table consent_access_audit_event add constraint ck_consent_access_audit_event_type check (
    event_type in (
        'CONSENT_READ', 'CONSENT_HISTORY_READ', 'DISCLOSURE_EVALUATED',
        'TRUSTED_CONTACT_LIST_READ', 'TRUSTED_CONTACT_DETAIL_READ'
    )
);

alter table operational_alert_context_event add column actor_principal_id uuid;
alter table operational_alert_context_event add column actor_customer_id varchar(80);
alter table operational_alert_context_event add column actor_session_id uuid;
alter table operational_alert_context_event add column actor_type varchar(20) not null default 'LEGACY';

alter table operational_alert_audit_event add column actor_principal_id uuid;
alter table operational_alert_audit_event add column actor_customer_id varchar(80);
alter table operational_alert_audit_event add column actor_session_id uuid;
alter table operational_alert_audit_event add column actor_type varchar(20) not null default 'LEGACY';

comment on column operational_alert_audit_event.actor_principal_id is 'Bearer 인증 principal UUID; 시스템·legacy 이벤트는 null';
