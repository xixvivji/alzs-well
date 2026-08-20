create table trusted_contact (
    contact_id uuid primary key,
    customer_id varchar(80) not null references customer_profile(customer_id) on delete cascade,
    consent_id uuid not null references customer_consent(consent_id),
    display_name varchar(80) not null,
    relationship_code varchar(30) not null,
    masked_contact varchar(40) not null,
    recipient_accepted boolean not null,
    status varchar(20) not null,
    valid_from timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    revocation_reason varchar(300),
    row_version bigint not null default 1,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_trusted_contact_relationship check (relationship_code in ('FAMILY','CAREGIVER','OTHER')),
    constraint ck_trusted_contact_status check (status in ('ACTIVE','REVOKED')),
    constraint ck_trusted_contact_period check (valid_from < expires_at),
    constraint ck_trusted_contact_recipient check (recipient_accepted),
    constraint ck_trusted_contact_revocation check (
      (status='ACTIVE' and revoked_at is null and revocation_reason is null) or
      (status='REVOKED' and revoked_at is not null and revocation_reason is not null)
    )
);
create index idx_trusted_contact_customer_active on trusted_contact(customer_id,expires_at) where status='ACTIVE';

create table trusted_contact_scope (
    contact_id uuid not null references trusted_contact(contact_id) on delete cascade,
    scope_code varchar(50) not null,
    primary key(contact_id,scope_code),
    constraint ck_trusted_contact_scope check (scope_code in (
      'ALERT_REASON_SUMMARY','CONTACT_REQUEST_STATUS','PROTECTION_GUIDANCE_SUMMARY'
    ))
);

create table trusted_contact_event (
    event_id uuid primary key,
    contact_id uuid not null references trusted_contact(contact_id),
    event_type varchar(20) not null,
    actor_id varchar(80) not null,
    reason varchar(300),
    occurred_at timestamptz not null,
    row_version bigint not null,
    constraint ck_trusted_contact_event_type check (event_type in ('CREATED','UPDATED','REVOKED'))
);

insert into auth_permission(permission_code,description) values
 ('TRUSTED_CONTACT_READ','자신의 신뢰연락인 지정 조회'),('TRUSTED_CONTACT_WRITE','자신의 신뢰연락인 지정 변경'),
 ('TRUSTED_CONTACT_READ_ALL','보호업무 목적의 신뢰연락인 지정 조회'),
 ('TRUSTED_CONTACT_WRITE_ALL','보호업무 목적의 신뢰연락인 지정 변경');
insert into auth_role_permission(role_code,permission_code) values
 ('CUSTOMER','TRUSTED_CONTACT_READ'),('CUSTOMER','TRUSTED_CONTACT_WRITE'),
 ('PROTECTION_STAFF','TRUSTED_CONTACT_READ_ALL'),('PROTECTION_STAFF','TRUSTED_CONTACT_WRITE_ALL');

comment on table trusted_contact is '대리권과 외부 연락 실행을 부여하지 않는 최소정보 신뢰연락인 지정';
