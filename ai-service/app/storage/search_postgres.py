from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha256
from typing import Any
from uuid import UUID, uuid4

import psycopg

from app.domain.search import SearchRequest, StoredSearchResult
from app.embedding.base import EmbeddingProvider, vector_literal
from app.embedding.local_hash import LocalHashEmbeddingProvider
from app.errors import KnowledgeContractError
from app.retrieval.query import keyword_query
from app.storage.database_config import DatabaseConfig
from app.storage.embedding_index import vector_type


ConnectFunction = Callable[..., Any]
INDEX_VERSION = "hybrid-hash-ngram-v3"
E5_INDEX_VERSION = "hybrid-multilingual-e5-small-v2"
ARCTIC_INDEX_VERSION = "hybrid-arctic-ko-v1"
KEYWORD_WEIGHT = 0.35
VECTOR_WEIGHT = 0.65
VECTOR_THRESHOLD = 0.15
RESULT_THRESHOLD = 0.35
ARCTIC_KEYWORD_WEIGHT = 0.2
ARCTIC_VECTOR_WEIGHT = 0.8
ARCTIC_VECTOR_THRESHOLD = 0.15
ARCTIC_RESULT_THRESHOLD = 0.4
MAX_KEYWORD_CANDIDATES = 500
MAX_VECTOR_CANDIDATES = 500


@dataclass(frozen=True, slots=True)
class SearchReadiness:
    database: bool
    retrieval_audit_privileges: bool
    approved_embedding: bool
    vector_index: bool
    search_probe: bool


def index_version_for_backend(backend: str) -> str:
    return {
        "local-e5": E5_INDEX_VERSION,
        "local-arctic-ko": ARCTIC_INDEX_VERSION,
    }.get(backend, INDEX_VERSION)


def search_parameters(backend: str) -> tuple[float, float, float, float]:
    if backend == "local-arctic-ko":
        return (
            ARCTIC_KEYWORD_WEIGHT,
            ARCTIC_VECTOR_WEIGHT,
            ARCTIC_VECTOR_THRESHOLD,
            ARCTIC_RESULT_THRESHOLD,
        )
    return KEYWORD_WEIGHT, VECTOR_WEIGHT, VECTOR_THRESHOLD, RESULT_THRESHOLD


class PostgresSearchRepository:
    def __init__(
        self,
        config: DatabaseConfig,
        *,
        connect: ConnectFunction = psycopg.connect,
        embedding_provider: EmbeddingProvider | None = None,
    ) -> None:
        self._config = config
        self._connect_function = connect
        self._embedding_provider = embedding_provider or LocalHashEmbeddingProvider()
        self._index_version = index_version_for_backend(
            self._embedding_provider.descriptor.backend
        )

    @property
    def index_version(self) -> str:
        return self._index_version

    def start_run(self, request: SearchRequest, query_hash: str) -> UUID:
        run_id = uuid4()
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    """
                    insert into ai_knowledge.retrieval_run(
                        run_id, request_id, query_hash, as_of, principal_roles,
                        requester_audiences, requested_limit, index_version,
                        status, started_at
                    ) values (%s, %s, %s, %s, %s, %s, %s, %s, 'RUNNING', %s)
                    """,
                    (
                        run_id,
                        request.request_id,
                        query_hash,
                        request.as_of,
                        list(request.principal_roles),
                        list(request.requester_audiences),
                        request.limit,
                        self._index_version,
                        _now(),
                    ),
                )
            return run_id
        except psycopg.IntegrityError:
            raise KnowledgeContractError("SEARCH_REQUEST_CONFLICT") from None
        except psycopg.Error:
            raise KnowledgeContractError("STORAGE_UNAVAILABLE") from None

    def search(self, request: SearchRequest) -> tuple[StoredSearchResult, ...]:
        descriptor = self._embedding_provider.descriptor
        keyword_weight, vector_weight, vector_threshold, result_threshold = (
            search_parameters(descriptor.backend)
        )
        database_vector_type = vector_type(descriptor.dimensions)
        query_vector = vector_literal(
            self._embedding_provider.embed_query(request.query),
            dimensions=descriptor.dimensions,
        )
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    f"""
                    with search_query as (
                        select websearch_to_tsquery('simple', %s) as terms,
                            %s::{database_vector_type} as embedding
                    ), keyword_candidates as materialized (
                        select
                            c.chunk_id,
                            ts_rank_cd(to_tsvector('simple', c.content), search_query.terms, 32)
                                as keyword_score
                        from ai_knowledge.chunk c
                        join ai_knowledge.document_snapshot d
                          on d.document_id = c.document_id and d.version_label = c.version_label
                        cross join search_query
                        where d.allowed_roles && %s::text[]
                          and (d.audience = 'BOTH' or d.audience = any(%s::text[]))
                          and d.approval_status = 'APPROVED'
                          and d.lifecycle_status in ('PENDING_ACTIVATION', 'ACTIVE')
                          and d.effective_from <= %s
                          and (d.effective_to is null or d.effective_to >= %s)
                          and to_tsvector('simple', c.content) @@ search_query.terms
                        order by keyword_score desc,
                            ai_knowledge.document_authority_rank(d.document_type) desc,
                            c.chunk_id
                        limit %s
                    ), vector_candidates as materialized (
                        select
                            c.chunk_id,
                            greatest(
                                0.0,
                                1.0 - (
                                    e.embedding::{database_vector_type}
                                    <=> search_query.embedding
                                )
                            ) as vector_score
                        from ai_knowledge.chunk_embedding e
                        join ai_knowledge.chunk c on c.chunk_id = e.chunk_id
                        join ai_knowledge.document_snapshot d
                          on d.document_id = c.document_id and d.version_label = c.version_label
                        cross join search_query
                        where e.embedding_model_id = %s
                          and e.embedding_model_version = %s
                          and e.embedding_dimensions = %s
                          and d.allowed_roles && %s::text[]
                          and (d.audience = 'BOTH' or d.audience = any(%s::text[]))
                          and d.approval_status = 'APPROVED'
                          and d.lifecycle_status in ('PENDING_ACTIVATION', 'ACTIVE')
                          and d.effective_from <= %s
                          and (d.effective_to is null or d.effective_to >= %s)
                        order by e.embedding::{database_vector_type}
                            <=> search_query.embedding,
                            ai_knowledge.document_authority_rank(d.document_type) desc,
                            c.chunk_id
                        limit %s
                    ), candidate_scores as (
                        select
                            chunk_id,
                            max(keyword_score) as keyword_score,
                            max(vector_score) as vector_score
                        from (
                            select chunk_id, keyword_score, 0.0::double precision as vector_score
                            from keyword_candidates
                            union all
                            select chunk_id, 0.0::double precision as keyword_score, vector_score
                            from vector_candidates
                        ) bounded_candidates
                        group by chunk_id
                    ), ranked as (
                        select
                            c.document_id, c.version_label, c.chunk_id, c.chunk_order,
                            d.title, d.issuer, c.heading, c.section_path, c.page,
                            d.source_url, c.source_hash, c.text_hash, c.content,
                            d.document_type,
                            candidate_scores.keyword_score,
                            candidate_scores.vector_score
                        from candidate_scores
                        join ai_knowledge.chunk c on c.chunk_id = candidate_scores.chunk_id
                        join ai_knowledge.document_snapshot d
                          on d.document_id = c.document_id and d.version_label = c.version_label
                    ), scored as (
                        select ranked.*,
                            (least(1.0, keyword_score) * %s + vector_score * %s) as score,
                            ai_knowledge.document_authority_rank(document_type)
                                as authority_rank
                        from ranked
                        where keyword_score > 0 or vector_score >= %s
                    )
                    select
                        document_id, version_label, chunk_id, chunk_order,
                        title, issuer, heading, section_path, page,
                        source_url, source_hash, text_hash, content, score
                    from scored
                    where score >= %s
                    order by authority_rank desc, score desc,
                        document_id, version_label, chunk_order
                    limit %s
                    """,
                    (
                        keyword_query(request.query),
                        query_vector,
                        list(request.principal_roles),
                        list(request.requester_audiences),
                        request.as_of,
                        request.as_of,
                        MAX_KEYWORD_CANDIDATES,
                        descriptor.model_id,
                        descriptor.model_version,
                        descriptor.dimensions,
                        list(request.principal_roles),
                        list(request.requester_audiences),
                        request.as_of,
                        request.as_of,
                        MAX_VECTOR_CANDIDATES,
                        keyword_weight,
                        vector_weight,
                        vector_threshold,
                        result_threshold,
                        request.limit,
                    ),
                )
                return tuple(_stored_result(row) for row in cursor.fetchall())
        except psycopg.errors.QueryCanceled:
            raise KnowledgeContractError("SEARCH_TIMEOUT") from None
        except psycopg.Error:
            raise KnowledgeContractError("STORAGE_UNAVAILABLE") from None

    def readiness(self) -> SearchReadiness:
        descriptor = self._embedding_provider.descriptor
        database_vector_type = vector_type(descriptor.dimensions)
        query_vector = vector_literal(
            self._embedding_provider.embed_query("내부 검색 준비상태 확인"),
            dimensions=descriptor.dimensions,
        )
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    f"""
                    with approved_embedding as (
                        select e.embedding
                        from ai_knowledge.chunk_embedding e
                        join ai_knowledge.chunk c on c.chunk_id = e.chunk_id
                        join ai_knowledge.document_snapshot d
                          on d.document_id = c.document_id and d.version_label = c.version_label
                        where e.embedding_model_id = %s
                          and e.embedding_model_version = %s
                          and e.embedding_dimensions = %s
                          and d.approval_status = 'APPROVED'
                          and d.lifecycle_status in ('PENDING_ACTIVATION', 'ACTIVE')
                          and d.effective_from <= current_date
                          and (d.effective_to is null or d.effective_to >= current_date)
                        order by c.chunk_id
                        limit 1
                    )
                    select
                        current_database() is not null as database_ready,
                        (
                            has_table_privilege(
                                current_user,
                                'ai_knowledge.retrieval_run',
                                'INSERT'
                            )
                            and has_column_privilege(
                                current_user,
                                'ai_knowledge.retrieval_run',
                                'status',
                                'UPDATE'
                            )
                            and has_column_privilege(
                                current_user,
                                'ai_knowledge.retrieval_run',
                                'result_count',
                                'UPDATE'
                            )
                            and has_column_privilege(
                                current_user,
                                'ai_knowledge.retrieval_run',
                                'failure_code',
                                'UPDATE'
                            )
                            and has_column_privilege(
                                current_user,
                                'ai_knowledge.retrieval_run',
                                'finished_at',
                                'UPDATE'
                            )
                            and not has_table_privilege(
                                current_user,
                                'ai_knowledge.retrieval_run',
                                'DELETE'
                            )
                        ) as retrieval_audit_privileges,
                        exists(select 1 from approved_embedding) as approved_embedding_ready,
                        exists(
                            select 1
                            from pg_indexes
                            where schemaname = 'ai_knowledge'
                              and tablename = 'chunk_embedding'
                              and position('using hnsw' in lower(indexdef)) > 0
                              and position(lower(%s) in lower(indexdef)) > 0
                              and position(lower(%s) in lower(indexdef)) > 0
                        ) as vector_index_ready,
                        coalesce((
                            select (
                                embedding::{database_vector_type} <=> %s::{database_vector_type}
                            ) is not null
                            from approved_embedding
                        ), false) as search_probe_ready
                    """,
                    (
                        descriptor.model_id,
                        descriptor.model_version,
                        descriptor.dimensions,
                        descriptor.model_id,
                        descriptor.model_version,
                        query_vector,
                    ),
                )
                row = cursor.fetchone()
                if row is None or len(row) != 5:
                    raise KnowledgeContractError("STORAGE_UNAVAILABLE")
                return SearchReadiness(*(bool(value) for value in row))
        except KnowledgeContractError:
            raise
        except psycopg.errors.QueryCanceled:
            raise KnowledgeContractError("SEARCH_TIMEOUT") from None
        except psycopg.Error:
            raise KnowledgeContractError("STORAGE_UNAVAILABLE") from None

    def complete_run(self, run_id: UUID, result_count: int) -> None:
        self._finish_run(run_id, status="SUCCEEDED", result_count=result_count)

    def fail_run(self, run_id: UUID, failure_code: str) -> None:
        self._finish_run(run_id, status="FAILED", failure_code=failure_code)

    def _finish_run(
        self,
        run_id: UUID,
        *,
        status: str,
        result_count: int | None = None,
        failure_code: str | None = None,
    ) -> None:
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    """
                    update ai_knowledge.retrieval_run set
                        status = %s, result_count = %s, failure_code = %s, finished_at = %s
                    where run_id = %s and status = 'RUNNING'
                    """,
                    (status, result_count, failure_code, _now(), run_id),
                )
                if cursor.rowcount != 1:
                    raise KnowledgeContractError("SEARCH_REQUEST_CONFLICT")
        except KnowledgeContractError:
            raise
        except psycopg.Error:
            raise KnowledgeContractError("STORAGE_UNAVAILABLE") from None

    def _connect(self) -> Any:
        return self._connect_function(
            host=self._config.host,
            port=self._config.port,
            dbname=self._config.dbname,
            user=self._config.user,
            password=self._config.password,
            sslmode=self._config.sslmode,
            connect_timeout=self._config.connect_timeout,
            application_name="alzs-well-ai-search",
            options=f"-c statement_timeout={self._config.statement_timeout_ms}",
        )


def hash_query(query: str) -> str:
    return "sha256:" + sha256(query.encode("utf-8")).hexdigest()


def _stored_result(row: tuple[Any, ...]) -> StoredSearchResult:
    return StoredSearchResult(
        document_id=row[0],
        version_label=row[1],
        chunk_id=row[2],
        chunk_order=row[3],
        title=row[4],
        issuer=row[5],
        heading=row[6],
        section_path=tuple(row[7]),
        page=row[8],
        source_url=row[9],
        source_hash=row[10],
        text_hash=row[11],
        content=row[12],
        score=float(row[13]),
    )


def _now() -> datetime:
    return datetime.now(timezone.utc)
