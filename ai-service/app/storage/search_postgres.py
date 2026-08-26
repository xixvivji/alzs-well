from __future__ import annotations

from collections.abc import Callable
from datetime import datetime, timezone
from hashlib import sha256
from typing import Any
from uuid import UUID, uuid4

import psycopg

from app.domain.search import SearchRequest, StoredSearchResult
from app.embedding.base import EmbeddingProvider, vector_literal
from app.embedding.local_hash import LocalHashEmbeddingProvider
from app.errors import KnowledgeContractError
from app.storage.database_config import DatabaseConfig


ConnectFunction = Callable[..., Any]
INDEX_VERSION = "hybrid-hash-ngram-v3"
E5_INDEX_VERSION = "hybrid-multilingual-e5-small-v2"
KEYWORD_WEIGHT = 0.35
VECTOR_WEIGHT = 0.65
VECTOR_THRESHOLD = 0.15
RESULT_THRESHOLD = 0.35


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
        self._index_version = (
            E5_INDEX_VERSION
            if self._embedding_provider.descriptor.backend == "local-e5"
            else INDEX_VERSION
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
        query_vector = vector_literal(
            self._embedding_provider.embed_query(request.query),
            dimensions=self._embedding_provider.descriptor.dimensions,
        )
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    """
                    with search_query as (
                        select websearch_to_tsquery('simple', %s) as terms, %s::vector as embedding
                    ), ranked as (
                        select
                            c.document_id, c.version_label, c.chunk_id, c.chunk_order,
                            d.title, d.issuer, c.heading, c.section_path, c.page,
                            d.source_url, c.source_hash, c.text_hash, c.content,
                            d.document_type,
                            ts_rank_cd(to_tsvector('simple', c.content), search_query.terms, 32)
                                as keyword_score,
                            case
                                when c.embedding is null
                                  or c.embedding_model_version is distinct from %s
                                    then 0.0
                                else greatest(0.0, 1.0 - (c.embedding <=> search_query.embedding))
                            end as vector_score
                        from ai_knowledge.chunk c
                        join ai_knowledge.document_snapshot d
                          on d.document_id = c.document_id and d.version_label = c.version_label
                        cross join search_query
                        where d.allowed_roles && %s::text[]
                          and (d.audience = 'BOTH' or d.audience = any(%s::text[]))
                          and d.approval_status = 'APPROVED'
                          and d.lifecycle_status = 'ACTIVE'
                          and d.effective_from <= %s
                          and (d.effective_to is null or d.effective_to >= %s)
                    ), scored as (
                        select ranked.*,
                            (least(1.0, keyword_score) * %s + vector_score * %s) as score,
                            case document_type
                                when 'LAW' then 600
                                when 'REGULATION' then 500
                                when 'INTERNAL_POLICY' then 400
                                when 'PUBLIC_GUIDE' then 300
                                when 'PUBLIC_NOTICE' then 200
                                when 'FORM' then 100
                                else 0
                            end as authority_rank
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
                        request.query,
                        query_vector,
                        self._embedding_provider.descriptor.model_version,
                        list(request.principal_roles),
                        list(request.requester_audiences),
                        request.as_of,
                        request.as_of,
                        KEYWORD_WEIGHT,
                        VECTOR_WEIGHT,
                        VECTOR_THRESHOLD,
                        RESULT_THRESHOLD,
                        request.limit,
                    ),
                )
                return tuple(_stored_result(row) for row in cursor.fetchall())
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
