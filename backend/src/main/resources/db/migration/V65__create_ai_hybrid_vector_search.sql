create extension if not exists vector;

alter table ai_knowledge.chunk
    add column embedding vector(384),
    add column embedding_model_version varchar(80),
    add constraint ck_ai_chunk_embedding_pair check (
        (embedding is null and embedding_model_version is null)
        or
        (embedding is not null and embedding_model_version = 'local-hash-ngram-ko-v1')
    );

create index idx_ai_chunk_embedding_hnsw
    on ai_knowledge.chunk using hnsw (embedding vector_cosine_ops)
    where embedding is not null;

comment on column ai_knowledge.chunk.embedding is
    '외부 모델 다운로드 없이 재현 가능한 384차원 로컬 임베딩 파생값';
comment on column ai_knowledge.chunk.embedding_model_version is
    '임베딩 재생성과 검색 호환성을 검증하는 고정 버전';
