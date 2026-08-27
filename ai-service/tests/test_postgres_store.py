from __future__ import annotations

from datetime import date
from typing import Any
from uuid import UUID, uuid4

import psycopg
import pytest

from app.domain.chunk import KnowledgeChunk
from app.domain.manifest import KnowledgeManifest
from app.embedding.base import EmbeddingDescriptor
from app.errors import KnowledgeContractError
from app.storage.database_config import DatabaseConfig
from app.storage.postgres import PostgresIngestionStore


class FakeCursor:
    def __init__(
        self,
        *,
        run: tuple[str, ...] | None = None,
        execute_error: Exception | None = None,
        many_error: Exception | None = None,
    ) -> None:
        self.run = run
        self.execute_error = execute_error
        self.many_error = many_error
        self.executions: list[tuple[str, object]] = []
        self.batch: list[tuple[object, ...]] = []
        self.batches: list[list[tuple[object, ...]]] = []
        self.rowcount = 1

    def __enter__(self) -> FakeCursor:
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def execute(self, statement: str, parameters: object = None) -> None:
        if self.execute_error is not None:
            raise self.execute_error
        self.executions.append((" ".join(statement.split()), parameters))

    def executemany(self, statement: str, parameters: list[tuple[object, ...]]) -> None:
        if self.many_error is not None:
            raise self.many_error
        self.executions.append((" ".join(statement.split()), "executemany"))
        self.batch.extend(parameters)
        self.batches.append(parameters)

    def fetchone(self) -> tuple[str, ...] | None:
        return self.run


class FakeConnection:
    def __init__(self, cursor: FakeCursor) -> None:
        self.fake_cursor = cursor

    def __enter__(self) -> FakeConnection:
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def cursor(self) -> FakeCursor:
        return self.fake_cursor


class Connector:
    def __init__(self, *cursors: FakeCursor) -> None:
        self.cursors = list(cursors)
        self.calls: list[dict[str, Any]] = []

    def __call__(self, **kwargs: Any) -> FakeConnection:
        self.calls.append(kwargs)
        return FakeConnection(self.cursors.pop(0))


class FakeEmbeddingProvider:
    descriptor = EmbeddingDescriptor(
        backend="local-e5",
        model_id="intfloat/multilingual-e5-small",
        model_version="multilingual-e5-small@test",
        dimensions=384,
    )

    def __init__(self) -> None:
        self.passages: list[str] = []

    def embed_query(self, value: str) -> tuple[float, ...]:
        del value
        return (1.0,) + (0.0,) * 383

    def embed_passage(self, value: str) -> tuple[float, ...]:
        self.passages.append(value)
        return (1.0,) + (0.0,) * 383


def test_starts_run_without_exposing_password() -> None:
    cursor = FakeCursor()
    connector = Connector(cursor)
    store = PostgresIngestionStore(_config(), connect=connector)

    run_id = store.start_run(
        document_id="DOC-SYN-STORE-001",
        version_label="1.0.0",
        source_hash="sha256:" + "1" * 64,
        as_of=date(2026, 8, 25),
    )

    assert isinstance(run_id, UUID)
    assert "insert into ai_knowledge.ingestion_run" in cursor.executions[0][0]
    assert connector.calls[0]["password"] == "secret-value"
    assert "secret-value" not in str(cursor.executions)


def test_atomically_replaces_chunks_and_completes_run() -> None:
    run_id = uuid4()
    cursor = FakeCursor(
        run=("DOC-SYN-STORE-001", "1.0.0", "sha256:" + "1" * 64, "RUNNING")
    )
    connector = Connector(cursor)
    store = PostgresIngestionStore(_config(), connect=connector)
    chunks = (_chunk(1, "first"), _chunk(2, "second"))

    store.complete_run(
        run_id, chunks, ("EMPTY_TEXT_PAGE", "EMPTY_TEXT_PAGE"), _manifest()
    )

    statements = [statement for statement, _ in cursor.executions]
    assert statements[0].startswith("select pg_advisory_xact_lock")
    snapshot_parameters = next(
        parameters
        for statement, parameters in cursor.executions
        if statement.startswith("insert into ai_knowledge.document_snapshot")
    )
    assert snapshot_parameters[7] == "SYNTHETIC_FIXTURE"  # type: ignore[index]
    stale_delete = next(
        (statement, parameters)
        for statement, parameters in cursor.executions
        if statement.startswith("delete from ai_knowledge.chunk")
    )
    assert "not (chunk_id = any(%s::text[]))" in stale_delete[0]
    assert stale_delete[1][2] == [chunks[0].chunk_id, chunks[1].chunk_id]  # type: ignore[index]
    chunk_insert = next(
        statement
        for statement, _ in cursor.executions
        if statement.startswith("insert into ai_knowledge.chunk(")
    )
    embedding_insert = next(
        statement
        for statement, _ in cursor.executions
        if statement.startswith("insert into ai_knowledge.chunk_embedding(")
    )
    assert "on conflict(chunk_id) do update" in chunk_insert
    assert "embedding = coalesce(" in chunk_insert
    assert (
        "on conflict(chunk_id, embedding_model_id, embedding_model_version)"
        in embedding_insert
    )
    assert statements[-1].startswith("update ai_knowledge.ingestion_run")
    assert len(cursor.batches) == 2
    chunk_batch, embedding_batch = cursor.batches
    assert len(chunk_batch) == 2
    assert len(embedding_batch) == 2
    assert chunk_batch[0][0] == chunks[0].chunk_id
    assert chunk_batch[0][1] == run_id
    assert chunk_batch[0][5] == ["문서", "절"]
    assert str(chunk_batch[0][15]).startswith("[")
    assert chunk_batch[0][16] == "local-hash-ngram-ko-v1"
    assert embedding_batch[0][0:4] == (
        chunks[0].chunk_id,
        "local-hash-ngram-ko",
        "local-hash-ngram-ko-v1",
        384,
    )
    assert str(embedding_batch[0][4]).startswith("[")


def test_ingestion_uses_injected_embedding_provider_and_records_version() -> None:
    run_id = uuid4()
    cursor = FakeCursor(
        run=("DOC-SYN-STORE-001", "1.0.0", "sha256:" + "1" * 64, "RUNNING")
    )
    provider = FakeEmbeddingProvider()
    store = PostgresIngestionStore(
        _config(), connect=Connector(cursor), embedding_provider=provider
    )

    store.complete_run(run_id, (_chunk(1, "body"),), (), _manifest())

    assert provider.passages == ["문서 절 절 body"]
    chunk_batch, embedding_batch = cursor.batches
    assert chunk_batch[0][15:17] == (None, None)
    assert embedding_batch[0][1:4] == (
        "intfloat/multilingual-e5-small",
        "multilingual-e5-small@test",
        384,
    )
    assert str(embedding_batch[0][4]).startswith("[1,")


def test_ingestion_stores_1024_dimension_embedding_without_legacy_write() -> None:
    class ArcticProvider(FakeEmbeddingProvider):
        descriptor = EmbeddingDescriptor(
            backend="local-arctic-ko",
            model_id="dragonkue/snowflake-arctic-embed-l-v2.0-ko",
            model_version="snowflake-arctic-embed-l-v2.0-ko@test",
            dimensions=1024,
        )

        def embed_passage(self, value: str) -> tuple[float, ...]:
            self.passages.append(value)
            return (1.0,) + (0.0,) * 1023

    cursor = FakeCursor(
        run=("DOC-SYN-STORE-001", "1.0.0", "sha256:" + "1" * 64, "RUNNING")
    )
    store = PostgresIngestionStore(
        _config(), connect=Connector(cursor), embedding_provider=ArcticProvider()
    )

    store.complete_run(uuid4(), (_chunk(1, "body"),), (), _manifest())

    chunk_batch, embedding_batch = cursor.batches
    assert chunk_batch[0][15:17] == (None, None)
    assert embedding_batch[0][3] == 1024
    assert len(str(embedding_batch[0][4])) > 1024


def test_reingestion_preserves_other_model_embeddings_for_unchanged_chunks() -> None:
    cursor = FakeCursor(
        run=("DOC-SYN-STORE-001", "1.0.0", "sha256:" + "1" * 64, "RUNNING")
    )
    store = PostgresIngestionStore(_config(), connect=Connector(cursor))
    chunks = (_chunk(1, "first"), _chunk(2, "second"))

    store.complete_run(uuid4(), chunks, (), _manifest())

    delete_statement, delete_parameters = next(
        (statement, parameters)
        for statement, parameters in cursor.executions
        if statement.startswith("delete from ai_knowledge.chunk")
    )
    assert "not (chunk_id = any(%s::text[]))" in delete_statement
    assert delete_parameters[2] == [chunk.chunk_id for chunk in chunks]  # type: ignore[index]
    assert not any(
        statement.startswith("delete from ai_knowledge.chunk_embedding")
        for statement, _ in cursor.executions
    )


def test_rejects_unsupported_embedding_dimension_before_database_write() -> None:
    class UnsupportedProvider(FakeEmbeddingProvider):
        descriptor = EmbeddingDescriptor(
            backend="unsupported",
            model_id="unsupported/model",
            model_version="unsupported@test",
            dimensions=768,
        )

    connector = Connector()
    store = PostgresIngestionStore(
        _config(), connect=connector, embedding_provider=UnsupportedProvider()
    )

    with pytest.raises(KnowledgeContractError) as caught:
        store.complete_run(uuid4(), (_chunk(1, "body"),), (), _manifest())

    assert caught.value.code == "EMBEDDING_VECTOR_INVALID"
    assert connector.calls == []


def test_records_failed_run_with_safe_code_only() -> None:
    cursor = FakeCursor()
    store = PostgresIngestionStore(_config(), connect=Connector(cursor))
    run_id = uuid4()

    store.fail_run(run_id, "OCR_REQUIRED")

    statement, parameters = cursor.executions[0]
    assert statement.startswith("update ai_knowledge.ingestion_run")
    assert parameters[0] == "OCR_REQUIRED"  # type: ignore[index]
    assert run_id in parameters  # type: ignore[operator]


def test_rejects_mixed_or_empty_chunks_before_database_write() -> None:
    connector = Connector()
    store = PostgresIngestionStore(_config(), connect=connector)

    with pytest.raises(KnowledgeContractError) as empty:
        store.complete_run(uuid4(), (), (), _manifest())
    assert empty.value.code == "CHUNK_VALIDATION_FAILED"

    mixed = (_chunk(1, "first"), _chunk(3, "third"))
    with pytest.raises(KnowledgeContractError) as invalid:
        store.complete_run(uuid4(), mixed, (), _manifest())
    assert invalid.value.code == "CHUNK_VALIDATION_FAILED"
    assert connector.calls == []


def test_rejects_run_identity_or_state_mismatch() -> None:
    cursor = FakeCursor(run=("OTHER", "1.0.0", "sha256:" + "1" * 64, "RUNNING"))
    store = PostgresIngestionStore(_config(), connect=Connector(cursor))

    with pytest.raises(KnowledgeContractError) as caught:
        store.complete_run(uuid4(), (_chunk(1, "body"),), (), _manifest())

    assert caught.value.code == "STORAGE_CONFLICT"


def test_maps_database_errors_to_sanitized_storage_codes() -> None:
    def unavailable(**kwargs: object) -> object:
        del kwargs
        raise psycopg.OperationalError("password=must-not-escape")

    store = PostgresIngestionStore(_config(), connect=unavailable)
    with pytest.raises(KnowledgeContractError) as unavailable_error:
        store.start_run(
            document_id="DOC-SYN-STORE-001",
            version_label="1.0.0",
            source_hash="sha256:" + "1" * 64,
            as_of=date(2026, 8, 25),
        )
    assert unavailable_error.value.code == "STORAGE_UNAVAILABLE"
    assert "must-not-escape" not in unavailable_error.value.safe_message

    cursor = FakeCursor(
        run=("DOC-SYN-STORE-001", "1.0.0", "sha256:" + "1" * 64, "RUNNING"),
        many_error=psycopg.IntegrityError("content=must-not-escape"),
    )
    conflict_store = PostgresIngestionStore(_config(), connect=Connector(cursor))
    with pytest.raises(KnowledgeContractError) as conflict:
        conflict_store.complete_run(uuid4(), (_chunk(1, "body"),), (), _manifest())
    assert conflict.value.code == "STORAGE_CONFLICT"
    assert "must-not-escape" not in conflict.value.safe_message


def _config() -> DatabaseConfig:
    return DatabaseConfig(
        host="postgres",
        port=5432,
        dbname="alzs_well",
        user="alzswell_ai_ingestor",
        password="secret-value",
        sslmode="prefer",
        connect_timeout=5,
    )


def _manifest() -> KnowledgeManifest:
    return KnowledgeManifest(
        payload={
            "contractVersion": "1.0.0",
            "documentId": "DOC-SYN-STORE-001",
            "versionLabel": "1.0.0",
            "title": "합성 문서",
            "issuer": "ALZ's well",
            "sourceUrl": None,
            "sourcePath": "synthetic.pdf",
            "sourceHash": "sha256:" + "1" * 64,
            "sourceTransformations": [],
            "documentType": "SYNTHETIC_FIXTURE",
            "classification": "INTERNAL",
            "audience": "STAFF",
            "allowedRoles": ["PROTECTION_STAFF"],
            "approvalStatus": "APPROVED",
            "lifecycleStatus": "ACTIVE",
            "effectiveFrom": "2026-08-21",
            "effectiveTo": None,
        }
    )


def _chunk(order: int, text: str) -> KnowledgeChunk:
    return KnowledgeChunk(
        chunk_id="chk_" + str(order) * 64,
        document_id="DOC-SYN-STORE-001",
        version_label="1.0.0",
        heading="절",
        section_path=("문서", "절"),
        page=order,
        chunk_order=order,
        text=text,
        text_hash="sha256:" + str(order) * 64,
        source_hash="sha256:" + "1" * 64,
        extractor_version="pypdf-text-v1",
        chunker_version="pdf-structure-ko-v1",
        page_start=order,
        page_end=order,
    )
