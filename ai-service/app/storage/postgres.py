from __future__ import annotations

from collections.abc import Callable
from datetime import date, datetime, timezone
from typing import Any
from uuid import UUID, uuid4

import psycopg

from app.domain.chunk import KnowledgeChunk
from app.errors import KnowledgeContractError
from app.storage.database_config import DatabaseConfig


ConnectFunction = Callable[..., Any]


class PostgresIngestionStore:
    def __init__(
        self,
        config: DatabaseConfig,
        *,
        connect: ConnectFunction = psycopg.connect,
    ) -> None:
        self._config = config
        self._connect_function = connect

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
    ) -> None:
        document_id, version_label = _validate_chunks(chunks)
        try:
            with self._connect() as connection, connection.cursor() as cursor:
                cursor.execute(
                    "select pg_advisory_xact_lock(hashtextextended(%s, 0))",
                    (f"{document_id}\x1f{version_label}",),
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
                    "delete from ai_knowledge.chunk where document_id = %s and version_label = %s",
                    (document_id, version_label),
                )
                created_at = _now()
                cursor.executemany(
                    """
                    insert into ai_knowledge.chunk(
                        chunk_id, run_id, document_id, version_label, heading,
                        section_path, page, page_start, page_end, chunk_order,
                        content, text_hash, source_hash, extractor_version,
                        chunker_version, created_at
                    ) values (
                        %s, %s, %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s, %s, %s, %s
                    )
                    """,
                    [_chunk_parameters(run_id, chunk, created_at) for chunk in chunks],
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
    if not chunks:
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


def _chunk_parameters(
    run_id: UUID, chunk: KnowledgeChunk, created_at: datetime
) -> tuple[object, ...]:
    return (
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
        created_at,
    )


def _now() -> datetime:
    return datetime.now(timezone.utc)
