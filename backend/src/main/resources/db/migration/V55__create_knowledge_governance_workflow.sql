create table knowledge_document_governance (
    workflow_id uuid primary key,
    document_id varchar(80) not null,
    version_label varchar(40) not null,
    title varchar(200) not null,
    issuer varchar(160) not null,
    source_type varchar(30) not null,
    source_path varchar(500) not null,
    source_url varchar(1000),
    source_hash varchar(71) not null,
    source_transformations jsonb not null,
    document_type varchar(30) not null,
    classification varchar(30) not null,
    audience varchar(20) not null,
    allowed_roles varchar(60)[] not null,
    effective_from date not null,
    effective_to date,
    checked_at date not null,
    usage_rights varchar(40) not null,
    approval_status varchar(20) not null,
    lifecycle_status varchar(30) not null,
    approved_by varchar(120),
    approved_at timestamptz,
    supersedes_document_id varchar(80),
    supersedes_version_label varchar(40),
    row_version bigint not null,
    registered_by varchar(120) not null,
    registered_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_knowledge_governance_version unique(document_id,version_label),
    constraint ck_knowledge_governance_document_id check(document_id ~ '^[A-Z0-9]+(-[A-Z0-9]+)*$'),
    constraint ck_knowledge_governance_version_label check(version_label ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$'),
    constraint ck_knowledge_governance_source_type check(source_type in ('OFFICIAL_EXTERNAL','INTERNAL_POLICY','SYNTHETIC_FIXTURE')),
    constraint ck_knowledge_governance_source_path check(source_path !~ '^/' and source_path !~ '(^|/)\.\.(/|$)'),
    constraint ck_knowledge_governance_source_url check(source_url is null or source_url ~ '^https://'),
    constraint ck_knowledge_governance_source_boundary check(
      (source_type='OFFICIAL_EXTERNAL' and source_path like 'knowledge/official-source/%' and source_url is not null)
      or (source_type='INTERNAL_POLICY' and source_path like 'knowledge/internal-policy/%')
      or (source_type='SYNTHETIC_FIXTURE' and source_path like 'contracts/knowledge/fixtures/%'
          and source_url is null and usage_rights='SYNTHETIC_UNRESTRICTED')
    ),
    constraint ck_knowledge_governance_source_hash check(source_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_knowledge_governance_transformations check(jsonb_typeof(source_transformations)='array'),
    constraint ck_knowledge_governance_document_type check(document_type in ('LAW','REGULATION','PUBLIC_GUIDE','PUBLIC_NOTICE','FORM','INTERNAL_POLICY','SYNTHETIC_FIXTURE')),
    constraint ck_knowledge_governance_classification check(classification in ('PUBLIC_OFFICIAL','INTERNAL','CONFIDENTIAL')),
    constraint ck_knowledge_governance_audience check(audience in ('CUSTOMER','STAFF','BOTH')),
    constraint ck_knowledge_governance_roles check(cardinality(allowed_roles)>0 and allowed_roles <@ array['CUSTOMER','PROTECTION_STAFF','DETECTION_ADMIN','COMPLIANCE_REVIEWER','KNOWLEDGE_ADMIN','SECURITY_ADMIN']::varchar[]),
    constraint ck_knowledge_governance_period check(effective_to is null or effective_to>=effective_from),
    constraint ck_knowledge_governance_usage check(usage_rights in ('REVIEW_REQUIRED','INTERNAL_USE_APPROVED','PUBLIC_REUSE_ALLOWED','SYNTHETIC_UNRESTRICTED')),
    constraint ck_knowledge_governance_approval check(approval_status in ('IN_REVIEW','APPROVED')),
    constraint ck_knowledge_governance_lifecycle check(lifecycle_status in ('PENDING_ACTIVATION','ACTIVE','SUPERSEDED')),
    constraint ck_knowledge_governance_approval_metadata check(
        (approval_status='IN_REVIEW' and lifecycle_status='PENDING_ACTIVATION' and approved_by is null and approved_at is null)
        or (approval_status='APPROVED' and lifecycle_status in ('ACTIVE','SUPERSEDED') and approved_by is not null
            and approved_at is not null and usage_rights<>'REVIEW_REQUIRED')
    ),
    constraint ck_knowledge_governance_supersedes check(
        (supersedes_document_id is null and supersedes_version_label is null)
        or (supersedes_document_id is not null and supersedes_version_label is not null)
    )
);
create unique index uq_knowledge_governance_active on knowledge_document_governance(document_id)
where lifecycle_status='ACTIVE';

create or replace function guard_knowledge_governance_update() returns trigger language plpgsql as $$
begin
 if row(old.document_id,old.version_label,old.title,old.issuer,old.source_type,old.source_path,old.source_url,
        old.source_hash,old.source_transformations,old.document_type,old.classification,old.audience,old.allowed_roles,
        old.effective_from,old.effective_to,old.checked_at,old.usage_rights,old.supersedes_document_id,
        old.supersedes_version_label,old.registered_by,old.registered_at)
    is distinct from
    row(new.document_id,new.version_label,new.title,new.issuer,new.source_type,new.source_path,new.source_url,
        new.source_hash,new.source_transformations,new.document_type,new.classification,new.audience,new.allowed_roles,
        new.effective_from,new.effective_to,new.checked_at,new.usage_rights,new.supersedes_document_id,
        new.supersedes_version_label,new.registered_by,new.registered_at) then
   raise exception 'knowledge governance immutable metadata cannot be changed' using errcode='55000';
 end if;
 if not (
   (old.approval_status='IN_REVIEW' and old.lifecycle_status='PENDING_ACTIVATION'
     and new.approval_status='APPROVED' and new.lifecycle_status='ACTIVE')
   or (old.approval_status='APPROVED' and old.lifecycle_status='ACTIVE'
     and new.approval_status='APPROVED' and new.lifecycle_status='SUPERSEDED')
 ) or new.row_version<>old.row_version+1 then
   raise exception 'invalid knowledge governance transition' using errcode='55000';
 end if;
 return new;
end $$;
create trigger trg_knowledge_governance_guard before update on knowledge_document_governance
for each row execute function guard_knowledge_governance_update();

create table knowledge_governance_event (
    event_id uuid primary key,
    workflow_id uuid not null references knowledge_document_governance(workflow_id),
    document_id varchar(80) not null,
    version_label varchar(40) not null,
    event_type varchar(30) not null,
    actor_subject varchar(120) not null,
    approval_reference varchar(120),
    state_snapshot jsonb not null,
    occurred_at timestamptz not null,
    integrity_hash char(64) not null,
    constraint ck_knowledge_governance_event_type check(event_type in ('REGISTERED_FOR_REVIEW','PUBLISHED','SUPERSEDED')),
    constraint ck_knowledge_governance_integrity check(integrity_hash ~ '^[0-9a-f]{64}$')
);
create index idx_knowledge_governance_event_timeline
on knowledge_governance_event(document_id,version_label,occurred_at,event_id);
create trigger trg_knowledge_governance_event_append_only before update or delete on knowledge_governance_event
for each row execute function reject_protected_event_mutation();

insert into auth_permission(permission_code,description) values
('KNOWLEDGE_ADMIN_WRITE','지식 원문 메타데이터를 검토 등록하고 명시적으로 승인·게시');
insert into auth_role_permission(role_code,permission_code) values
('DETECTION_ADMIN','KNOWLEDGE_ADMIN_WRITE');

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  grant select,insert,update on knowledge_document_governance to alzswell_app;
  grant select,insert on knowledge_governance_event to alzswell_app;
  revoke delete on knowledge_document_governance from alzswell_app;
  revoke update,delete on knowledge_governance_event from alzswell_app;
 end if;
end $$;

comment on table knowledge_document_governance is 'AI ingestion 전 사람이 검토·승인하는 지식 문서 버전 메타데이터';
comment on table knowledge_governance_event is '지식 문서 등록·게시·대체의 추가 전용 감사이력';
