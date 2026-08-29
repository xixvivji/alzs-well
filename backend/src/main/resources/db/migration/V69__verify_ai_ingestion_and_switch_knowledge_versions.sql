alter table knowledge_ingestion_import
    add column ai_proof_version varchar(30),
    add column ai_verified_at timestamptz,
    add constraint ck_knowledge_import_ai_proof check (
        (ai_proof_version is null and ai_verified_at is null)
        or (ai_proof_version is not null and ai_proof_version = 'AI_DB_SNAPSHOT_V1'
            and ai_verified_at is not null)
    );

comment on column knowledge_ingestion_import.ai_proof_version is
    'Spring이 SUCCEEDED ingestion_run과 모든 AI chunk를 동일 statement snapshot에서 대조한 검증 규칙 버전. null은 V69 이전 legacy import';
comment on column knowledge_ingestion_import.ai_verified_at is
    'AI 파생 DB snapshot 검증 완료시각. 원문이나 외부 증명을 저장하지 않음';

-- V64~V68의 legacy import 가운데 당시 AI run과 전체 binding/passage graph를 지금도 완전히
-- 재검증할 수 있는 행만 migration owner가 결정론적으로 승격한다. 나머지는 null로 보존하여
-- 조회에서 fail-closed하고, 새 승인 version으로 명시적 supersede/import해야 한다.
drop trigger trg_knowledge_ingestion_import_append_only on knowledge_ingestion_import;
update knowledge_ingestion_import imported
   set ai_proof_version='AI_DB_SNAPSHOT_V1',ai_verified_at=transaction_timestamp()
  from ai_knowledge.ingestion_run run
 where run.run_id=imported.ingestion_run_id
   and run.document_id=imported.document_id
   and run.version_label=imported.version_label
   and run.source_hash=imported.source_hash
   and run.as_of=imported.as_of
   and run.status='SUCCEEDED'
   and run.extractor_version=imported.extractor_version
   and run.chunker_version=imported.chunker_version
   and run.chunk_count=imported.chunk_count
   and (select count(*) from ai_knowledge.chunk chunk where chunk.run_id=run.run_id)=imported.chunk_count
   and (select count(distinct chunk.chunk_order) from ai_knowledge.chunk chunk
         where chunk.run_id=run.run_id)=imported.chunk_count
   and (select min(chunk.chunk_order) from ai_knowledge.chunk chunk
         where chunk.run_id=run.run_id)=1
   and (select max(chunk.chunk_order) from ai_knowledge.chunk chunk
         where chunk.run_id=run.run_id)=imported.chunk_count
   and (select count(*) from knowledge_ai_passage_binding binding
         where binding.import_id=imported.import_id)=imported.chunk_count
   and (
       select count(*)
         from ai_knowledge.chunk chunk
         join knowledge_ai_passage_binding binding
           on binding.import_id=imported.import_id and binding.chunk_id=chunk.chunk_id
         join knowledge_passage passage on passage.passage_id=binding.passage_id
         join knowledge_document_version version on version.document_version_id=passage.document_version_id
        where chunk.run_id=run.run_id
          and chunk.document_id=imported.document_id and chunk.version_label=imported.version_label
          and chunk.source_hash=imported.source_hash
          and chunk.extractor_version=imported.extractor_version
          and chunk.chunker_version=imported.chunker_version
          and binding.document_id=chunk.document_id and binding.version_label=chunk.version_label
          and binding.chunk_order=chunk.chunk_order and binding.section_path=chunk.section_path
          and binding.page is not distinct from chunk.page
          and binding.page_start is not distinct from chunk.page_start
          and binding.page_end is not distinct from chunk.page_end
          and binding.source_hash=chunk.source_hash and binding.text_hash=chunk.text_hash
          and binding.extractor_version=chunk.extractor_version
          and binding.chunker_version=chunk.chunker_version
          and version.document_id=chunk.document_id and version.version_label=chunk.version_label
          and passage.passage_order=chunk.chunk_order
          and passage.heading=chunk.heading and passage.content=chunk.content
   )=imported.chunk_count;
create trigger trg_knowledge_ingestion_import_append_only
before update or delete on knowledge_ingestion_import
for each row execute function reject_protected_event_mutation();

create or replace function require_verified_knowledge_import()
returns trigger language plpgsql as $$
declare
    verified_run_count integer;
begin
    select count(*) into verified_run_count
      from ai_knowledge.ingestion_run run
     where run.run_id=new.ingestion_run_id
       and run.document_id=new.document_id
       and run.version_label=new.version_label
       and run.source_hash=new.source_hash
       and run.as_of=new.as_of
       and run.status='SUCCEEDED'
       and run.extractor_version=new.extractor_version
       and run.chunker_version=new.chunker_version
       and run.chunk_count=new.chunk_count
       and (select count(*) from ai_knowledge.chunk chunk where chunk.run_id=run.run_id)=new.chunk_count
       and (select count(distinct chunk.chunk_order) from ai_knowledge.chunk chunk where chunk.run_id=run.run_id)=new.chunk_count
       and (select min(chunk.chunk_order) from ai_knowledge.chunk chunk where chunk.run_id=run.run_id)=1
       and (select max(chunk.chunk_order) from ai_knowledge.chunk chunk where chunk.run_id=run.run_id)=new.chunk_count
       and not exists(
           select 1 from ai_knowledge.chunk chunk
            where chunk.run_id=run.run_id
              and (chunk.document_id<>new.document_id
                   or chunk.version_label<>new.version_label
                   or chunk.source_hash<>new.source_hash
                   or chunk.extractor_version<>new.extractor_version
                   or chunk.chunker_version<>new.chunker_version)
       );
    if verified_run_count<>1 then
        raise exception 'new knowledge imports require an exact successful AI run and complete chunk set'
            using errcode = '55000';
    end if;
    new.ai_proof_version := 'AI_DB_SNAPSHOT_V1';
    new.ai_verified_at := transaction_timestamp();
    return new;
end $$;

create trigger trg_knowledge_import_requires_ai_proof
before insert on knowledge_ingestion_import
for each row execute function require_verified_knowledge_import();

create or replace function verify_knowledge_import_graph()
returns trigger language plpgsql as $$
declare
    binding_count integer;
    exact_count integer;
begin
    select count(*) into binding_count
      from knowledge_ai_passage_binding binding
     where binding.import_id=new.import_id;

    select count(*) into exact_count
      from ai_knowledge.chunk chunk
      join knowledge_ai_passage_binding binding
        on binding.import_id=new.import_id and binding.chunk_id=chunk.chunk_id
      join knowledge_passage passage on passage.passage_id=binding.passage_id
      join knowledge_document_version version on version.document_version_id=passage.document_version_id
     where chunk.run_id=new.ingestion_run_id
       and chunk.document_id=new.document_id and chunk.version_label=new.version_label
       and chunk.source_hash=new.source_hash
       and chunk.extractor_version=new.extractor_version
       and chunk.chunker_version=new.chunker_version
       and binding.document_id=chunk.document_id and binding.version_label=chunk.version_label
       and binding.chunk_order=chunk.chunk_order
       and binding.section_path=chunk.section_path
       and binding.page is not distinct from chunk.page
       and binding.page_start is not distinct from chunk.page_start
       and binding.page_end is not distinct from chunk.page_end
       and binding.source_hash=chunk.source_hash and binding.text_hash=chunk.text_hash
       and binding.extractor_version=chunk.extractor_version
       and binding.chunker_version=chunk.chunker_version
       and version.document_id=chunk.document_id and version.version_label=chunk.version_label
       and passage.passage_order=chunk.chunk_order
       and passage.heading=chunk.heading and passage.content=chunk.content;

    if binding_count<>new.chunk_count or exact_count<>new.chunk_count then
        raise exception 'verified knowledge import graph does not exactly match the AI run chunks'
            using errcode='55000';
    end if;
    return new;
end $$;

create constraint trigger trg_knowledge_import_graph_integrity
after insert on knowledge_ingestion_import
deferrable initially deferred
for each row execute function verify_knowledge_import_graph();

alter table knowledge_document_governance
    drop constraint ck_knowledge_governance_approval_metadata;
alter table knowledge_document_governance
    drop constraint ck_knowledge_governance_lifecycle;
alter table knowledge_document_governance
    add constraint ck_knowledge_governance_lifecycle
        check(lifecycle_status in ('PENDING_ACTIVATION','ACTIVE','SUPERSEDED','RETIRED'));
alter table knowledge_document_governance
    add constraint ck_knowledge_governance_approval_metadata check (
        (approval_status='IN_REVIEW' and lifecycle_status='PENDING_ACTIVATION'
            and approved_by is null and approved_at is null)
        or (approval_status='APPROVED' and lifecycle_status in ('PENDING_ACTIVATION','ACTIVE','SUPERSEDED','RETIRED')
            and approved_by is not null and approved_at is not null and usage_rights<>'REVIEW_REQUIRED')
    );

drop trigger trg_knowledge_governance_guard on knowledge_document_governance;

-- V55에서 승인됐지만 아직 권위 catalog로 import되지 않은 행은 새 2단계 activation 모델로 승격한다.
-- 이미 catalog head인 legacy 행은 기존 검색 가용성을 유지하고 다음 verified import부터 새 규칙을 적용한다.
update knowledge_document_governance governance
   set lifecycle_status='PENDING_ACTIVATION',row_version=row_version+1,updated_at=clock_timestamp()
 where approval_status='APPROVED' and lifecycle_status='ACTIVE'
   and not exists (
       select 1 from knowledge_document document
        where document.document_id=governance.document_id
          and document.current_version=governance.version_label
   );

create unique index uq_knowledge_governance_pending_approved
    on knowledge_document_governance(document_id)
    where approval_status='APPROVED' and lifecycle_status='PENDING_ACTIVATION';

create or replace function guard_knowledge_governance_update()
returns trigger language plpgsql as $$
begin
    if row(old.document_id,old.version_label,old.title,old.issuer,old.source_type,old.source_path,old.source_url,
           old.source_hash,old.source_transformations,old.document_type,old.classification,old.audience,
           old.allowed_roles,old.effective_from,old.effective_to,old.checked_at,old.usage_rights,
           old.supersedes_document_id,old.supersedes_version_label,old.registered_by,old.registered_at)
       is distinct from
       row(new.document_id,new.version_label,new.title,new.issuer,new.source_type,new.source_path,new.source_url,
           new.source_hash,new.source_transformations,new.document_type,new.classification,new.audience,
           new.allowed_roles,new.effective_from,new.effective_to,new.checked_at,new.usage_rights,
           new.supersedes_document_id,new.supersedes_version_label,new.registered_by,new.registered_at) then
        raise exception 'knowledge governance immutable metadata cannot be changed' using errcode='55000';
    end if;
    if new.row_version<>old.row_version+1 or new.updated_at<old.updated_at then
        raise exception 'invalid knowledge governance version transition' using errcode='55000';
    end if;

    if old.approval_status='IN_REVIEW' and old.lifecycle_status='PENDING_ACTIVATION'
       and new.approval_status='APPROVED' and new.lifecycle_status='PENDING_ACTIVATION' then
        if new.approved_by is null or new.approved_at is null
           or old.approved_by is not null or old.approved_at is not null then
            raise exception 'knowledge approval metadata is invalid' using errcode='55000';
        end if;
        if exists(select 1 from knowledge_document document where document.document_id=new.document_id) then
            if not exists(
                select 1 from knowledge_document document
                 where document.document_id=new.document_id
                   and new.supersedes_document_id=new.document_id
                   and new.supersedes_version_label=document.current_version
            ) then
                raise exception 'approved knowledge candidate must explicitly supersede the catalog head'
                    using errcode='55000';
            end if;
        elsif new.supersedes_document_id is not null or new.supersedes_version_label is not null then
            raise exception 'first knowledge version cannot supersede a missing catalog head' using errcode='55000';
        end if;
        return new;
    end if;

    if old.approval_status='APPROVED' and old.lifecycle_status='PENDING_ACTIVATION'
       and new.approval_status='APPROVED' and new.lifecycle_status='ACTIVE'
       and new.approved_by=old.approved_by and new.approved_at=old.approved_at then
        if not exists(
            select 1 from knowledge_ingestion_import imported
             where imported.document_id=new.document_id and imported.version_label=new.version_label
               and imported.ai_proof_version='AI_DB_SNAPSHOT_V1' and imported.ai_verified_at is not null
        ) then
            raise exception 'knowledge activation requires a verified import' using errcode='55000';
        end if;
        return new;
    end if;

    if old.approval_status='APPROVED' and old.lifecycle_status='PENDING_ACTIVATION'
       and new.approval_status='APPROVED' and new.lifecycle_status='RETIRED'
       and new.approved_by=old.approved_by and new.approved_at=old.approved_at then
        if exists(
            select 1 from knowledge_ingestion_import imported
             where imported.document_id=old.document_id and imported.version_label=old.version_label
        ) or not exists(
            select 1 from knowledge_document_governance replacement
             where replacement.document_id=old.document_id and replacement.workflow_id<>old.workflow_id
               and replacement.approval_status='IN_REVIEW'
               and replacement.lifecycle_status='PENDING_ACTIVATION'
               and replacement.supersedes_document_id is not distinct from old.supersedes_document_id
               and replacement.supersedes_version_label is not distinct from old.supersedes_version_label
        ) then
            raise exception 'pending knowledge can only retire for an explicit reviewed replacement before import'
                using errcode='55000';
        end if;
        return new;
    end if;

    if old.approval_status='APPROVED' and old.lifecycle_status='ACTIVE'
       and new.approval_status='APPROVED' and new.lifecycle_status='SUPERSEDED'
       and new.approved_by=old.approved_by and new.approved_at=old.approved_at then
        if not exists(
            select 1 from knowledge_document_governance target
            join knowledge_ingestion_import imported
              on imported.document_id=target.document_id and imported.version_label=target.version_label
             and imported.ai_proof_version='AI_DB_SNAPSHOT_V1' and imported.ai_verified_at is not null
             where target.document_id=old.document_id
               and target.approval_status='APPROVED' and target.lifecycle_status='PENDING_ACTIVATION'
               and target.supersedes_document_id=old.document_id
               and target.supersedes_version_label=old.version_label
        ) then
            raise exception 'active knowledge can only be superseded by a verified pending version'
                using errcode='55000';
        end if;
        return new;
    end if;

    raise exception 'invalid knowledge governance transition' using errcode='55000';
end $$;

create trigger trg_knowledge_governance_guard
before update on knowledge_document_governance
for each row execute function guard_knowledge_governance_update();

alter table knowledge_governance_event drop constraint ck_knowledge_governance_event_type;
alter table knowledge_governance_event add constraint ck_knowledge_governance_event_type
    check(event_type in ('REGISTERED_FOR_REVIEW','PUBLISHED','ACTIVATED','SUPERSEDED','RETIRED'));

create or replace function guard_knowledge_catalog_version_switch()
returns trigger language plpgsql as $$
declare
    target_count integer;
begin
    if tg_op='UPDATE' and (old.document_id is distinct from new.document_id
                           or old.current_version is not distinct from new.current_version) then
        raise exception 'knowledge catalog only permits an audited current-version switch'
            using errcode = '55000';
    end if;

    select count(*) into target_count
      from knowledge_document_version version
      join knowledge_document_governance governance
        on governance.document_id = version.document_id
       and governance.version_label = version.version_label
      join knowledge_ingestion_import imported
        on imported.document_id = version.document_id
       and imported.version_label = version.version_label
       and imported.ai_proof_version='AI_DB_SNAPSHOT_V1'
       and imported.ai_verified_at is not null
     where version.document_id = new.document_id
       and version.version_label = new.current_version
       and version.superseded_at is null
       and governance.approval_status = 'APPROVED'
       and governance.lifecycle_status = 'ACTIVE'
       and new.title = governance.title
       and new.source_type = case governance.source_type
           when 'OFFICIAL_EXTERNAL' then 'OFFICIAL_PUBLIC'
           when 'SYNTHETIC_FIXTURE' then 'SYNTHETIC_DEMO'
           when 'INTERNAL_POLICY' then 'INTERNAL_POLICY'
           else ''
       end
       and new.issuer = governance.issuer
       and new.source_url is not distinct from governance.source_url
       and new.audience = governance.audience
       and new.status = 'APPROVED'
       and new.effective_from = governance.effective_from
       and new.effective_to is not distinct from governance.effective_to
       and new.checked_at = governance.checked_at
       and new.approval_status = 'APPROVED'
       and new.lifecycle_status = 'ACTIVE'
       and new.allowed_roles = governance.allowed_roles;

    if target_count <> 1 then
        raise exception 'knowledge catalog target is not an active, verified governed version'
            using errcode = '55000';
    end if;

    if tg_op='INSERT' then
        if exists(
            select 1 from knowledge_document_governance target
             where target.document_id=new.document_id and target.version_label=new.current_version
               and (target.supersedes_document_id is not null or target.supersedes_version_label is not null)
        ) then
            raise exception 'first catalog version cannot supersede another version' using errcode='55000';
        end if;
        return new;
    end if;

    if not exists(
        select 1 from knowledge_document_governance target
         where target.document_id=new.document_id and target.version_label=new.current_version
           and target.supersedes_document_id=old.document_id
           and target.supersedes_version_label=old.current_version
    ) then
        raise exception 'knowledge catalog target does not explicitly supersede its previous head'
            using errcode='55000';
    end if;

    if exists(
        select 1 from knowledge_document_governance previous
         where previous.document_id=old.document_id and previous.version_label=old.current_version
    ) and not exists(
        select 1 from knowledge_document_governance previous
         where previous.document_id=old.document_id and previous.version_label=old.current_version
           and previous.approval_status='APPROVED' and previous.lifecycle_status='SUPERSEDED'
    ) then
        raise exception 'knowledge catalog previous governed version is not superseded' using errcode='55000';
    end if;
    if not exists(
        select 1 from knowledge_document_version previous
         where previous.document_id=old.document_id and previous.version_label=old.current_version
           and previous.superseded_at is not null
    ) then
        raise exception 'knowledge catalog previous version has no superseded timestamp' using errcode='55000';
    end if;
    return new;
end $$;

create constraint trigger trg_knowledge_catalog_version_switch
after insert or update on knowledge_document
deferrable initially deferred
for each row execute function guard_knowledge_catalog_version_switch();

drop trigger trg_knowledge_version_append_only on knowledge_document_version;
create or replace function guard_knowledge_version_supersession()
returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'knowledge document versions are append-only' using errcode = '55000';
    end if;
    if row(old.document_version_id, old.document_id, old.version_label, old.content_checksum,
           old.published_at, old.approved_at)
       is distinct from
       row(new.document_version_id, new.document_id, new.version_label, new.content_checksum,
           new.published_at, new.approved_at)
       or old.superseded_at is not null
       or new.superseded_at is null
       or new.superseded_at < old.approved_at then
        raise exception 'knowledge version only permits first superseded timestamp assignment'
            using errcode = '55000';
    end if;
    if exists(
        select 1 from knowledge_document document
         where document.document_id = old.document_id
           and document.current_version = old.version_label
    ) then
        raise exception 'knowledge version cannot be superseded before catalog head switch'
            using errcode = '55000';
    end if;
    if not exists(
        select 1 from knowledge_document_governance target
        join knowledge_document document on document.document_id=target.document_id
         where target.document_id=old.document_id and target.version_label=document.current_version
           and target.approval_status='APPROVED' and target.lifecycle_status='ACTIVE'
           and target.supersedes_document_id=old.document_id
           and target.supersedes_version_label=old.version_label
    ) then
        raise exception 'knowledge version has no active governed successor' using errcode='55000';
    end if;
    if exists(
        select 1 from knowledge_document_governance previous
         where previous.document_id=old.document_id and previous.version_label=old.version_label
    ) and not exists(
        select 1 from knowledge_document_governance previous
         where previous.document_id=old.document_id and previous.version_label=old.version_label
           and previous.approval_status='APPROVED' and previous.lifecycle_status='SUPERSEDED'
    ) then
        raise exception 'governed knowledge version is not superseded' using errcode='55000';
    end if;
    return new;
end $$;

create trigger trg_knowledge_version_supersession
before update or delete on knowledge_document_version
for each row execute function guard_knowledge_version_supersession();

do $$ begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_app') then
        grant usage on schema ai_knowledge to alzswell_app;
        grant select on ai_knowledge.ingestion_run, ai_knowledge.chunk to alzswell_app;

        revoke update, delete on knowledge_document_governance from alzswell_app;
        grant update(approval_status,lifecycle_status,approved_by,approved_at,row_version,updated_at)
            on knowledge_document_governance to alzswell_app;

        revoke update, delete on knowledge_document, knowledge_document_version from alzswell_app;
        grant update(title, source_type, issuer, source_url, audience, status, effective_from,
            effective_to, checked_at, current_version, approval_status, lifecycle_status, allowed_roles)
            on knowledge_document to alzswell_app;
        grant update(superseded_at) on knowledge_document_version to alzswell_app;
    end if;
end $$;

comment on function require_verified_knowledge_import() is
    'V69 이후 신규 Spring import를 exact SUCCEEDED AI run과 완전한 chunk 집합에 결속하고 proof 필드를 DB가 생성';
comment on function verify_knowledge_import_graph() is
    '커밋 시 import binding과 Spring passage가 AI run의 모든 chunk와 1:1 일치하는지 검증';
comment on function guard_knowledge_governance_update() is
    '검수 승인과 verified import activation을 분리하고 이전 ACTIVE 대체를 같은 import 트랜잭션에 결속';
comment on function guard_knowledge_catalog_version_switch() is
    '커밋 시점에 verified ACTIVE governance, catalog head, version supersession의 원자적 일치를 검증';
comment on function guard_knowledge_version_supersession() is
    'catalog head 전환 뒤 이전 승인 버전에 superseded_at을 한 번만 기록';
