from __future__ import annotations

from datetime import date
from typing import Any
from uuid import UUID, uuid4

import psycopg
import pytest

from app.domain.chunk import KnowledgeChunk
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

    store.complete_run(run_id, chunks, ("EMPTY_TEXT_PAGE", "EMPTY_TEXT_PAGE"))

    statements = [statement for statement, _ in cursor.executions]
    assert statements[0].startswith("select pg_advisory_xact_lock")
    assert any(statement.startswith("delete from ai_knowledge.chunk") for statement in statements)
    assert any(statement.startswith("insert into ai_knowledge.chunk") for statement in statements)
    assert statements[-1].startswith("update ai_knowledge.ingestion_run")
    assert len(cursor.batch) == 2
    assert cursor.batch[0][0] == chunks[0].chunk_id
    assert cursor.batch[0][1] == run_id
    assert cursor.batch[0][5] == ["문서", "절"]


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
        store.complete_run(uuid4(), (), ())
    assert empty.value.code == "CHUNK_VALIDATION_FAILED"

    mixed = (_chunk(1, "first"), _chunk(3, "third"))
    with pytest.raises(KnowledgeContractError) as invalid:
        store.complete_run(uuid4(), mixed, ())
    assert invalid.value.code == "CHUNK_VALIDATION_FAILED"
    assert connector.calls == []


def test_rejects_run_identity_or_state_mismatch() -> None:
    cursor = FakeCursor(run=("OTHER", "1.0.0", "sha256:" + "1" * 64, "RUNNING"))
    store = PostgresIngestionStore(_config(), connect=Connector(cursor))

    with pytest.raises(KnowledgeContractError) as caught:
        store.complete_run(uuid4(), (_chunk(1, "body"),), ())

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
        conflict_store.complete_run(uuid4(), (_chunk(1, "body"),), ())
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
