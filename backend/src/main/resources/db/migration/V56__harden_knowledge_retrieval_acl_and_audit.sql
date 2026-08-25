alter table knowledge_document add column approval_status varchar(20);
alter table knowledge_document add column lifecycle_status varchar(30);
alter table knowledge_document add column allowed_roles varchar(60)[];

update knowledge_document set
 approval_status='APPROVED',
 lifecycle_status=case when status='EXPIRED' then 'EXPIRED' else 'ACTIVE' end,
 allowed_roles=array['PROTECTION_STAFF','DETECTION_ADMIN']::varchar[];

alter table knowledge_document alter column approval_status set not null;
alter table knowledge_document alter column lifecycle_status set not null;
alter table knowledge_document alter column allowed_roles set not null;
alter table knowledge_document add constraint ck_knowledge_document_approval_status
 check(approval_status in ('APPROVED'));
alter table knowledge_document add constraint ck_knowledge_document_lifecycle_status
 check(lifecycle_status in ('ACTIVE','SUPERSEDED','EXPIRED','RETIRED'));
alter table knowledge_document add constraint ck_knowledge_document_allowed_roles
 check(cardinality(allowed_roles)>0 and allowed_roles <@ array[
  'CUSTOMER','PROTECTION_STAFF','DETECTION_ADMIN','COMPLIANCE_REVIEWER','KNOWLEDGE_ADMIN','SECURITY_ADMIN'
 ]::varchar[]);

create table knowledge_access_audit_event (
 access_event_id uuid primary key,
 event_type varchar(30) not null,
 actor_principal_id uuid,
 actor_subject varchar(120) not null,
 permission_code varchar(60) not null,
 principal_roles varchar(60)[] not null,
 requester_audiences varchar(20)[] not null,
 requested_resource_id varchar(160),
 query_hash char(64),
 as_of date not null,
 returned_resource_ids text[] not null,
 outcome varchar(20) not null,
 detail jsonb not null,
 occurred_at timestamptz not null,
 integrity_hash char(64) not null,
 constraint ck_knowledge_access_event_type check(event_type in ('DOCUMENT_LIST','DOCUMENT_DETAIL','VERSION_LIST','PASSAGE_DETAIL','SEARCH')),
 constraint ck_knowledge_access_permission check(permission_code in ('KNOWLEDGE_READ','KNOWLEDGE_SEARCH')),
 constraint ck_knowledge_access_roles check(cardinality(principal_roles)>0),
 constraint ck_knowledge_access_audiences check(requester_audiences <@ array['CUSTOMER','STAFF']::varchar[]),
 constraint ck_knowledge_access_query_hash check(query_hash is null or query_hash ~ '^[0-9a-f]{64}$'),
 constraint ck_knowledge_access_outcome check(outcome in ('ALLOWED','FILTERED','NOT_FOUND')),
 constraint ck_knowledge_access_integrity check(integrity_hash ~ '^[0-9a-f]{64}$')
);
create index idx_knowledge_access_audit_time on knowledge_access_audit_event(occurred_at desc,access_event_id desc);
create trigger trg_knowledge_access_audit_append_only before update or delete on knowledge_access_audit_event
for each row execute function reject_protected_event_mutation();
create trigger trg_knowledge_version_append_only before update or delete on knowledge_document_version
for each row execute function reject_protected_event_mutation();
create trigger trg_knowledge_passage_append_only before update or delete on knowledge_passage
for each row execute function reject_protected_event_mutation();

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  revoke update,delete on knowledge_document,knowledge_document_version,knowledge_passage,knowledge_access_audit_event from alzswell_app;
  grant select on knowledge_document,knowledge_document_version,knowledge_passage to alzswell_app;
  grant select,insert on knowledge_access_audit_event to alzswell_app;
 end if;
end $$;

comment on column knowledge_document.allowed_roles is 'audience와 별도로 적용하는 문서 단위 역할 ACL';
comment on table knowledge_access_audit_event is '지식 목록·상세·문단·검색의 필터와 결과 ID를 남기는 추가 전용 접근 감사';
