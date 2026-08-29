create or replace function ai_knowledge.reject_verified_document_version_mutation()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, ai_knowledge
as $$
begin
    if tg_op = 'INSERT' then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(new.document_id, 0));
    elsif tg_op = 'DELETE' then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
    elsif old.document_id = new.document_id then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
    elsif old.document_id < new.document_id then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(new.document_id, 0));
    else
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(new.document_id, 0));
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
    end if;

    if tg_op in ('UPDATE', 'DELETE') then
        if exists(
            select 1
              from public.knowledge_ingestion_import imported
             where imported.document_id = old.document_id
               and imported.version_label = old.version_label
               and imported.ai_proof_version = 'AI_DB_SNAPSHOT_V1'
               and imported.ai_verified_at is not null
        ) then
            raise exception 'verified knowledge snapshots are immutable; use a new version label'
                using errcode = '55000';
        end if;
    end if;

    if tg_op in ('INSERT', 'UPDATE') then
        if exists(
            select 1
              from public.knowledge_ingestion_import imported
             where imported.document_id = new.document_id
               and imported.version_label = new.version_label
               and imported.ai_proof_version = 'AI_DB_SNAPSHOT_V1'
               and imported.ai_verified_at is not null
        ) then
            raise exception 'verified knowledge snapshots are immutable; use a new version label'
                using errcode = '55000';
        end if;
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end $$;

revoke all on function ai_knowledge.reject_verified_document_version_mutation() from public;

create trigger trg_ai_chunk_verified_snapshot_immutable
before insert or update or delete on ai_knowledge.chunk
for each row execute function ai_knowledge.reject_verified_document_version_mutation();

create trigger trg_ai_document_verified_snapshot_immutable
before insert or update or delete on ai_knowledge.document_snapshot
for each row execute function ai_knowledge.reject_verified_document_version_mutation();

create or replace function ai_knowledge.reject_verified_ingestion_run_mutation()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, ai_knowledge
as $$
begin
    if tg_op = 'DELETE' then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
    elsif old.document_id = new.document_id then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
    elsif old.document_id < new.document_id then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(new.document_id, 0));
    else
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(new.document_id, 0));
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(old.document_id, 0));
    end if;

    if exists(
        select 1
          from public.knowledge_ingestion_import imported
         where imported.ingestion_run_id = old.run_id
           and imported.ai_proof_version = 'AI_DB_SNAPSHOT_V1'
           and imported.ai_verified_at is not null
    ) then
        raise exception 'verified knowledge ingestion runs are immutable'
            using errcode = '55000';
    end if;
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end $$;

revoke all on function ai_knowledge.reject_verified_ingestion_run_mutation() from public;

create trigger trg_ai_ingestion_run_verified_snapshot_immutable
before update or delete on ai_knowledge.ingestion_run
for each row execute function ai_knowledge.reject_verified_ingestion_run_mutation();

do $$ begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_ingestor') then
        -- V68 temporarily allowed in-place vector refreshes. Ingestion now replaces the
        -- complete unverified derived snapshot under the same document advisory lock used
        -- by Spring governance/import, so mutable chunk rows would weaken the proof boundary.
        revoke update on ai_knowledge.chunk from alzswell_ai_ingestor;
        revoke update(
            chunk_id, run_id, document_id, version_label, heading, section_path,
            page, page_start, page_end, chunk_order, content, text_hash, source_hash,
            extractor_version, chunker_version, created_at, embedding,
            embedding_model_version
        ) on ai_knowledge.chunk from alzswell_ai_ingestor;

        revoke update on ai_knowledge.chunk_embedding from alzswell_ai_ingestor;
        revoke update(
            chunk_id, embedding_model_id, embedding_model_version,
            embedding_dimensions, embedding, created_at
        ) on ai_knowledge.chunk_embedding from alzswell_ai_ingestor;
        revoke delete on ai_knowledge.chunk_embedding from alzswell_ai_ingestor;

        grant select, insert, delete on ai_knowledge.chunk to alzswell_ai_ingestor;
        grant select, insert on ai_knowledge.chunk_embedding to alzswell_ai_ingestor;
    end if;
end $$;

comment on table ai_knowledge.chunk is
    'Spring과 공유하는 문서 advisory lock 안에서 검증 import 전까지만 전체 교체할 수 있는 검색 chunk snapshot';
comment on table ai_knowledge.chunk_embedding is
    '부모 chunk 삭제 cascade 뒤 INSERT-only로 재생성하며 ingestion 역할의 직접 DELETE·UPDATE를 금지하는 모델별 파생 벡터';
comment on table ai_knowledge.document_snapshot is
    '미검증 문서·버전은 upsert할 수 있지만 검증 import 이후에는 trigger가 동결하는 AI 검색 manifest snapshot';
comment on table ai_knowledge.ingestion_run is
    '문서 ingestion 실행 이력이며 검증 import가 참조하는 terminal run은 trigger가 동결';
