alter table knowledge_document drop constraint ck_knowledge_source_type;
alter table knowledge_document add constraint ck_knowledge_source_type
check (source_type in ('OFFICIAL_PUBLIC','SYNTHETIC_DEMO','INTERNAL_POLICY'));

alter table knowledge_passage alter column heading type varchar(240);
alter table knowledge_passage alter column citation_label type varchar(400);

create table knowledge_ingestion_import (
    import_id uuid primary key,
    ingestion_run_id uuid not null unique,
    document_id varchar(80) not null,
    version_label varchar(40) not null,
    source_hash varchar(71) not null,
    as_of date not null,
    extractor_version varchar(80) not null,
    chunker_version varchar(80) not null,
    chunk_count integer not null,
    imported_by varchar(120) not null,
    imported_at timestamptz not null,
    payload_hash char(64) not null,
    integrity_hash char(64) not null,
    constraint uq_knowledge_import_version unique(document_id,version_label),
    constraint ck_knowledge_import_source_hash check(source_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_knowledge_import_chunk_count check(chunk_count > 0 and chunk_count <= 500),
    constraint ck_knowledge_import_payload_hash check(payload_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_knowledge_import_integrity_hash check(integrity_hash ~ '^[0-9a-f]{64}$')
);

create table knowledge_ai_passage_binding (
    chunk_id varchar(68) primary key,
    passage_id uuid not null unique references knowledge_passage(passage_id) on delete restrict,
    import_id uuid not null references knowledge_ingestion_import(import_id) on delete restrict,
    document_id varchar(80) not null,
    version_label varchar(40) not null,
    chunk_order integer not null,
    section_path text[] not null,
    page integer,
    page_start integer,
    page_end integer,
    source_hash varchar(71) not null,
    text_hash varchar(71) not null,
    extractor_version varchar(80) not null,
    chunker_version varchar(80) not null,
    constraint uq_knowledge_ai_binding_order unique(document_id,version_label,chunk_order),
    constraint ck_knowledge_ai_binding_chunk_id check(chunk_id ~ '^chk_[0-9a-f]{64}$'),
    constraint ck_knowledge_ai_binding_order check(chunk_order > 0),
    constraint ck_knowledge_ai_binding_source_hash check(source_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_knowledge_ai_binding_text_hash check(text_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_knowledge_ai_binding_page check(
      (page is null and page_start is null and page_end is null)
      or (page=page_start and page_start>=1 and page_end>=page_start)
    )
);

create trigger trg_knowledge_ingestion_import_append_only
before update or delete on knowledge_ingestion_import
for each row execute function reject_protected_event_mutation();
create trigger trg_knowledge_ai_binding_append_only
before update or delete on knowledge_ai_passage_binding
for each row execute function reject_protected_event_mutation();

do $$ begin
 if exists(select 1 from pg_roles where rolname='alzswell_app') then
  grant select,insert on knowledge_document,knowledge_document_version,knowledge_passage,
    knowledge_ingestion_import,knowledge_ai_passage_binding to alzswell_app;
  revoke update,delete on knowledge_document,knowledge_document_version,knowledge_passage,
    knowledge_ingestion_import,knowledge_ai_passage_binding from alzswell_app;
 end if;
end $$;

comment on table knowledge_ingestion_import is '검증된 AI ingestion 결과를 Spring 권위 카탈로그에 반영한 추가 전용 감사기록';
comment on table knowledge_ai_passage_binding is 'AI chunk ID와 Spring 인용 passage ID 사이의 불변 연결';
