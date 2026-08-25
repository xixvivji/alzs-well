create schema ai_knowledge;

create table ai_knowledge.ingestion_run (
    run_id uuid primary key,
    document_id varchar(80) not null,
    version_label varchar(40) not null,
    source_hash varchar(71) not null,
    as_of date not null,
    status varchar(20) not null,
    extractor_version varchar(80),
    chunker_version varchar(80),
    chunk_count integer,
    warning_codes text[] not null default '{}',
    failure_code varchar(80),
    started_at timestamptz not null,
    finished_at timestamptz,
    constraint ck_ai_ingestion_run_source_hash
        check (source_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_ai_ingestion_run_status
        check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    constraint ck_ai_ingestion_run_chunk_count
        check (chunk_count is null or chunk_count >= 0),
    constraint ck_ai_ingestion_run_terminal_state check (
        (status in ('PENDING', 'RUNNING') and finished_at is null and failure_code is null)
        or
        (status = 'SUCCEEDED' and finished_at is not null and failure_code is null
            and extractor_version is not null and chunker_version is not null and chunk_count is not null)
        or
        (status = 'FAILED' and finished_at is not null and failure_code is not null)
    )
);
create index idx_ai_ingestion_run_document
    on ai_knowledge.ingestion_run(document_id, version_label, started_at desc);

create table ai_knowledge.chunk (
    chunk_id varchar(68) primary key,
    run_id uuid not null references ai_knowledge.ingestion_run(run_id) on delete restrict,
    document_id varchar(80) not null,
    version_label varchar(40) not null,
    heading varchar(240) not null,
    section_path text[] not null,
    page integer,
    page_start integer,
    page_end integer,
    chunk_order integer not null,
    content text not null,
    text_hash varchar(71) not null,
    source_hash varchar(71) not null,
    extractor_version varchar(80) not null,
    chunker_version varchar(80) not null,
    created_at timestamptz not null,
    constraint uq_ai_chunk_order unique(document_id, version_label, chunk_order),
    constraint ck_ai_chunk_id check (chunk_id ~ '^chk_[0-9a-f]{64}$'),
    constraint ck_ai_chunk_order check (chunk_order > 0),
    constraint ck_ai_chunk_content check (btrim(content) <> '' and char_length(content) <= 1200),
    constraint ck_ai_chunk_text_hash check (text_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_ai_chunk_source_hash check (source_hash ~ '^sha256:[0-9a-f]{64}$'),
    constraint ck_ai_chunk_page_range check (
        (page is null and page_start is null and page_end is null)
        or
        (page is not null and page_start is not null and page_end is not null
            and page = page_start and page_start >= 1 and page_end >= page_start)
    )
);
create index idx_ai_chunk_document on ai_knowledge.chunk(document_id, version_label, chunk_order);
create index idx_ai_chunk_keyword on ai_knowledge.chunk
    using gin (to_tsvector('simple', content));

revoke all on schema ai_knowledge from public;
revoke all on all tables in schema ai_knowledge from public;
do $$ begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_ingestor') then
        grant usage on schema ai_knowledge to alzswell_ai_ingestor;
        grant select, insert on ai_knowledge.ingestion_run to alzswell_ai_ingestor;
        grant update(status, extractor_version, chunker_version, chunk_count, warning_codes, failure_code, finished_at)
            on ai_knowledge.ingestion_run to alzswell_ai_ingestor;
        grant select, insert, delete on ai_knowledge.chunk to alzswell_ai_ingestor;
    end if;
end $$;

comment on schema ai_knowledge is '교체 가능한 내부 AI 파이프라인의 파생 데이터 전용 경계';
comment on table ai_knowledge.ingestion_run is '문서 ingestion 실행 상태와 안전한 오류코드 리포트';
comment on table ai_knowledge.chunk is '원문·manifest에서 결정론적으로 재생성 가능한 검색 chunk';
