create table ai_knowledge.chunk_embedding (
    chunk_id varchar(68) not null
        references ai_knowledge.chunk(chunk_id) on delete cascade,
    embedding_model_id varchar(160) not null,
    embedding_model_version varchar(160) not null,
    embedding_dimensions smallint not null,
    embedding vector not null,
    created_at timestamptz not null,
    primary key(chunk_id, embedding_model_id, embedding_model_version),
    constraint ck_ai_chunk_embedding_model_id
        check (btrim(embedding_model_id) <> ''),
    constraint ck_ai_chunk_embedding_model_version
        check (btrim(embedding_model_version) <> ''),
    constraint ck_ai_chunk_embedding_dimensions
        check (embedding_dimensions in (384, 1024)),
    constraint ck_ai_chunk_embedding_vector_dimensions
        check (vector_dims(embedding) = embedding_dimensions)
);

insert into ai_knowledge.chunk_embedding(
    chunk_id, embedding_model_id, embedding_model_version,
    embedding_dimensions, embedding, created_at
)
select
    chunk_id, 'local-hash-ngram-ko', embedding_model_version,
    384, embedding, created_at
from ai_knowledge.chunk
where embedding is not null
  and embedding_model_version = 'local-hash-ngram-ko-v1'
on conflict do nothing;

create index idx_ai_chunk_embedding_hash_v1_hnsw
    on ai_knowledge.chunk_embedding
    using hnsw ((embedding::vector(384)) vector_cosine_ops)
    where embedding_dimensions = 384
      and embedding_model_id = 'local-hash-ngram-ko'
      and embedding_model_version = 'local-hash-ngram-ko-v1';

create index idx_ai_chunk_embedding_e5_small_614241f_hnsw
    on ai_knowledge.chunk_embedding
    using hnsw ((embedding::vector(384)) vector_cosine_ops)
    where embedding_dimensions = 384
      and embedding_model_id = 'intfloat/multilingual-e5-small'
      and embedding_model_version =
          'multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3';

create index idx_ai_chunk_embedding_arctic_ko_55ec6e9_hnsw
    on ai_knowledge.chunk_embedding
    using hnsw ((embedding::vector(1024)) vector_cosine_ops)
    where embedding_dimensions = 1024
      and embedding_model_id = 'dragonkue/snowflake-arctic-embed-l-v2.0-ko'
      and embedding_model_version =
          'snowflake-arctic-embed-l-v2.0-ko@55ec6e9358a56d56af759bc8372e970caf8c305f';

revoke all on ai_knowledge.chunk_embedding from public;
do $$ begin
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_ingestor') then
        grant select, insert, delete on ai_knowledge.chunk_embedding
            to alzswell_ai_ingestor;
        grant update(embedding_dimensions, embedding, created_at)
            on ai_knowledge.chunk_embedding to alzswell_ai_ingestor;
        grant update(
            run_id, heading, section_path, page, page_start, page_end,
            chunk_order, content, text_hash, source_hash, extractor_version,
            chunker_version, embedding, embedding_model_version, created_at
        ) on ai_knowledge.chunk to alzswell_ai_ingestor;
    end if;
    if exists(select 1 from pg_roles where rolname = 'alzswell_ai_runtime') then
        grant select on ai_knowledge.chunk_embedding to alzswell_ai_runtime;
    end if;
end $$;

comment on table ai_knowledge.chunk_embedding is
    'chunk별 모델 ID·고정 버전·차원을 격리한 재생성 가능한 pgvector 파생값';
comment on column ai_knowledge.chunk_embedding.embedding is
    '384/1024차원을 허용하되 embedding_dimensions와 vector_dims가 일치해야 하는 벡터';
comment on column ai_knowledge.chunk.embedding is
    '하위 호환용 384차원 Hash 벡터. 신규 검색은 ai_knowledge.chunk_embedding을 사용';
comment on column ai_knowledge.chunk.embedding_model_version is
    '하위 호환용 Hash 버전. 신규 검색은 ai_knowledge.chunk_embedding을 사용';
