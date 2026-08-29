from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Any
from uuid import UUID, uuid4

import psycopg

from app.domain.chunk import KnowledgeChunk
from app.domain.manifest import KnowledgeManifest
from app.embedding.base import EmbeddingProvider, vector_literal
from app.embedding.local_hash import (
    EMBEDDING_DIMENSIONS as HASH_DIMENSIONS,
    EMBEDDING_MODEL_ID as HASH_MODEL_ID,
    EMBEDDING_MODEL_VERSION as HASH_MODEL_VERSION,
    LocalHashEmbeddingProvider,
)
from app.errors import KnowledgeContractError
from app.storage.database_config import DatabaseConfig
from app.storage.embedding_index import vector_type


ConnectFunction = Callable[..., Any]
MAX_CHUNKS_PER_RUN = 500


class PostgresIngestionStore:
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

    def start_run(
        self,
        *,
        document_id: str,
        version_label: str,
        source_hash: str,
        as_of: date,
    ) -> UUID:
        run_id = uuid4()
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    """
                    insert into ai_knowledge.ingestion_run(
                        run_id, document_id, version_label, source_hash, as_of,
                        status, started_at
                    ) values (%s, %s, %s, %s, %s, 'RUNNING', %s)
                    """,
                    (
                        run_id,
                        document_id,
                        version_label,
                        source_hash,
                        as_of,
                        _now(),
                    ),
                )
            return run_id
        except psycopg.Error:
            raise KnowledgeContractError("STORAGE_UNAVAILABLE") from None

    def complete_run(
        self,
        run_id: UUID,
        chunks: tuple[KnowledgeChunk, ...],
        warnings: tuple[str, ...],
        manifest: KnowledgeManifest,
    ) -> None:
        document_id, version_label = _validate_chunks(chunks)
        vector_type(self._embedding_provider.descriptor.dimensions)
        if (
            manifest.document_id != document_id
            or manifest.version_label != version_label
            or manifest.source_hash != chunks[0].source_hash
        ):
            raise KnowledgeContractError("STORAGE_CONFLICT")
        created_at = _now()
        chunk_writes = tuple(
            _chunk_write(run_id, chunk, created_at, self._embedding_provider)
            for chunk in chunks
        )
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    "select pg_advisory_xact_lock(hashtextextended(%s, 0))",
                    (document_id,),
                )
                cursor.execute(
                    """
                    select document_id, version_label, source_hash, status
                    from ai_knowledge.ingestion_run
                    where run_id = %s
                    for update
                    """,
                    (run_id,),
                )
                run = cursor.fetchone()
                if run != (document_id, version_label, chunks[0].source_hash, "RUNNING"):
                    raise KnowledgeContractError("STORAGE_CONFLICT")

                cursor.execute(
                    """
                    insert into ai_knowledge.document_snapshot(
                        document_id, version_label, contract_version, title, issuer,
                        source_url, source_hash, document_type, classification, audience,
                        allowed_roles, approval_status, lifecycle_status, effective_from,
                        effective_to, indexed_at
                    ) values (
                        %s, %s, %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s, %s, %s, %s
                    )
                    on conflict(document_id, version_label) do update set
                        contract_version = excluded.contract_version,
                        title = excluded.title,
                        issuer = excluded.issuer,
                        source_url = excluded.source_url,
                        source_hash = excluded.source_hash,
                        document_type = excluded.document_type,
                        classification = excluded.classification,
                        audience = excluded.audience,
                        allowed_roles = excluded.allowed_roles,
                        approval_status = excluded.approval_status,
                        lifecycle_status = excluded.lifecycle_status,
                        effective_from = excluded.effective_from,
                        effective_to = excluded.effective_to,
                        indexed_at = excluded.indexed_at
                    """,
                    (
                        manifest.document_id,
                        manifest.version_label,
                        manifest.contract_version,
                        manifest.title,
                        manifest.issuer,
                        manifest.source_url,
                        manifest.source_hash,
                        manifest.document_type,
                        manifest.classification,
                        manifest.audience,
                        list(manifest.allowed_roles),
                        manifest.approval_status,
                        manifest.lifecycle_status,
                        manifest.effective_from,
                        manifest.effective_to,
                        _now(),
                    ),
                )

                cursor.execute(
                    """
                    delete from ai_knowledge.chunk
                    where document_id = %s and version_label = %s
                    """,
                    (document_id, version_label),
                )
                cursor.executemany(
                    """
                    insert into ai_knowledge.chunk(
                        chunk_id, run_id, document_id, version_label, heading,
                        section_path, page, page_start, page_end, chunk_order,
                        content, text_hash, source_hash, extractor_version,
                        chunker_version, embedding, embedding_model_version, created_at
                    ) values (
                        %s, %s, %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s, %s, %s, %s::vector, %s, %s
                    )
                    """,
                    [write.chunk_parameters for write in chunk_writes],
                )
                cursor.executemany(
                    """
                    insert into ai_knowledge.chunk_embedding(
                        chunk_id, embedding_model_id, embedding_model_version,
                        embedding_dimensions, embedding, created_at
                    ) values (%s, %s, %s, %s, %s::vector, %s)
                    """,
                    [write.embedding_parameters for write in chunk_writes],
                )
                cursor.execute(
                    """
                    update ai_knowledge.ingestion_run set
                        status = 'SUCCEEDED', extractor_version = %s,
                        chunker_version = %s, chunk_count = %s,
                        warning_codes = %s, finished_at = %s
                    where run_id = %s and status = 'RUNNING'
                    """,
                    (
                        chunks[0].extractor_version,
                        chunks[0].chunker_version,
                        len(chunks),
                        list(dict.fromkeys(warnings)),
                        _now(),
                        run_id,
                    ),
                )
                if cursor.rowcount != 1:
                    raise KnowledgeContractError("STORAGE_CONFLICT")
        except KnowledgeContractError:
            raise
        except psycopg.IntegrityError:
            raise KnowledgeContractError("STORAGE_CONFLICT") from None
        except psycopg.errors.ObjectNotInPrerequisiteState:
            raise KnowledgeContractError("STORAGE_CONFLICT") from None
        except psycopg.Error:
            raise KnowledgeContractError("STORAGE_UNAVAILABLE") from None

    def fail_run(self, run_id: UUID, failure_code: str) -> None:
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    """
                    update ai_knowledge.ingestion_run set
                        status = 'FAILED', failure_code = %s, finished_at = %s
                    where run_id = %s and status in ('PENDING', 'RUNNING')
                    """,
                    (failure_code, _now(), run_id),
                )
                if cursor.rowcount != 1:
                    raise KnowledgeContractError("STORAGE_CONFLICT")
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
            application_name="alzs-well-ai-ingestion",
        )


def _validate_chunks(chunks: tuple[KnowledgeChunk, ...]) -> tuple[str, str]:
    if not 1 <= len(chunks) <= MAX_CHUNKS_PER_RUN:
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")
    first = chunks[0]
    if any(
        chunk.document_id != first.document_id
        or chunk.version_label != first.version_label
        or chunk.source_hash != first.source_hash
        or chunk.extractor_version != first.extractor_version
        or chunk.chunker_version != first.chunker_version
        or chunk.chunk_order != expected_order
        for expected_order, chunk in enumerate(chunks, start=1)
    ):
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")
    return first.document_id, first.version_label


@dataclass(frozen=True, slots=True)
class _ChunkWrite:
    chunk_parameters: tuple[object, ...]
    embedding_parameters: tuple[object, ...]


def _chunk_write(
    run_id: UUID,
    chunk: KnowledgeChunk,
    created_at: datetime,
    embedding_provider: EmbeddingProvider,
) -> _ChunkWrite:
    descriptor = embedding_provider.descriptor
    searchable_text = " ".join((*chunk.section_path, chunk.heading, chunk.text))
    vector = embedding_provider.embed_passage(searchable_text)
    literal = vector_literal(vector, dimensions=descriptor.dimensions)
    legacy_compatible = (
        descriptor.model_id == HASH_MODEL_ID
        and descriptor.model_version == HASH_MODEL_VERSION
        and descriptor.dimensions == HASH_DIMENSIONS
    )
    return _ChunkWrite(
        chunk_parameters=(
            chunk.chunk_id,
            run_id,
            chunk.document_id,
            chunk.version_label,
            chunk.heading,
            list(chunk.section_path),
            chunk.page,
            chunk.page_start,
            chunk.page_end,
            chunk.chunk_order,
            chunk.text,
            chunk.text_hash,
            chunk.source_hash,
            chunk.extractor_version,
            chunk.chunker_version,
            literal if legacy_compatible else None,
            descriptor.model_version if legacy_compatible else None,
            created_at,
        ),
        embedding_parameters=(
            chunk.chunk_id,
            descriptor.model_id,
            descriptor.model_version,
            descriptor.dimensions,
            literal,
            created_at,
        ),
    )


def _now() -> datetime:
    return datetime.now(timezone.utc)
