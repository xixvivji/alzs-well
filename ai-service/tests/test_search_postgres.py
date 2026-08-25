from __future__ import annotations

from datetime import date
from typing import Any
from uuid import UUID

import psycopg
import pytest

from app.domain.search import SearchRequest
from app.errors import KnowledgeContractError
from app.storage.database_config import DatabaseConfig
from app.storage.search_postgres import INDEX_VERSION, PostgresSearchRepository, hash_query


class FakeCursor:
    def __init__(self, rows: list[tuple[Any, ...]] | None = None) -> None:
        self.rows = rows or []
        self.executions: list[tuple[str, object]] = []
        self.rowcount = 1

    def __enter__(self) -> FakeCursor:
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def execute(self, statement: str, parameters: object = None) -> None:
        self.executions.append((" ".join(statement.split()), parameters))

    def fetchall(self) -> list[tuple[Any, ...]]:
        return self.rows


class FakeConnection:
    def __init__(self, cursor: FakeCursor) -> None:
        self.value = cursor

    def __enter__(self) -> FakeConnection:
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def cursor(self) -> FakeCursor:
        return self.value


class Connector:
    def __init__(self, *cursors: FakeCursor) -> None:
        self.cursors = list(cursors)

    def __call__(self, **kwargs: object) -> FakeConnection:
        del kwargs
        return FakeConnection(self.cursors.pop(0))


def test_search_sql_enforces_acl_audience_lifecycle_and_effective_date() -> None:
    cursor = FakeCursor([_row()])
    repository = PostgresSearchRepository(_config(), connect=Connector(cursor))

    results = repository.search(_request())

    statement, parameters = cursor.executions[0]
    assert "d.allowed_roles && %s::text[]" in statement
    assert "d.audience = 'BOTH'" in statement
    assert "d.approval_status = 'APPROVED'" in statement
    assert "d.lifecycle_status = 'ACTIVE'" in statement
    assert "d.effective_from <= %s" in statement
    assert "c.embedding <=> search_query.embedding" in statement
    assert parameters[0] == "금융거래 안심차단"  # type: ignore[index]
    assert str(parameters[1]).startswith("[")  # type: ignore[index]
    assert parameters[2:] == (  # type: ignore[index]
        ["PROTECTION_STAFF"], ["STAFF"], date(2026, 8, 25),
        date(2026, 8, 25), "local-hash-ngram-ko-v1", 10,
    )
    assert INDEX_VERSION == "hybrid-hash-ngram-v1"
    assert results[0].chunk_id == "chk_" + "1" * 64
    assert results[0].score == 0.5


def test_search_run_stores_only_query_hash_and_safe_metadata() -> None:
    cursor = FakeCursor()
    repository = PostgresSearchRepository(_config(), connect=Connector(cursor))
    request = _request()
    query_hash = hash_query(request.query)

    run_id = repository.start_run(request, query_hash)

    assert isinstance(run_id, UUID)
    statement, parameters = cursor.executions[0]
    assert "insert into ai_knowledge.retrieval_run" in statement
    assert query_hash in parameters  # type: ignore[operator]
    assert request.query not in str(parameters)


def test_search_run_terminal_updates_are_guarded() -> None:
    success_cursor = FakeCursor()
    failure_cursor = FakeCursor()
    repository = PostgresSearchRepository(
        _config(), connect=Connector(success_cursor, failure_cursor)
    )
    run_id = UUID("98000000-0000-0000-0000-000000000001")

    repository.complete_run(run_id, 2)
    repository.fail_run(run_id, "STORAGE_UNAVAILABLE")

    assert success_cursor.executions[0][1][0:3] == ("SUCCEEDED", 2, None)  # type: ignore[index]
    assert failure_cursor.executions[0][1][0:3] == (  # type: ignore[index]
        "FAILED",
        None,
        "STORAGE_UNAVAILABLE",
    )


def test_database_errors_are_sanitized() -> None:
    def unavailable(**kwargs: object) -> object:
        del kwargs
        raise psycopg.OperationalError("password=must-not-escape")

    repository = PostgresSearchRepository(_config(), connect=unavailable)

    with pytest.raises(KnowledgeContractError) as caught:
        repository.search(_request())

    assert caught.value.code == "STORAGE_UNAVAILABLE"
    assert "must-not-escape" not in caught.value.safe_message


def _request() -> SearchRequest:
    return SearchRequest.model_validate(
        {
            "requestId": "99000000-0000-0000-0000-000000000001",
            "query": "금융거래 안심차단",
            "permissions": ["KNOWLEDGE_SEARCH"],
            "principalRoles": ["PROTECTION_STAFF"],
            "requesterAudiences": ["STAFF"],
            "asOf": "2026-08-25",
            "limit": 10,
        }
    )


def _row() -> tuple[object, ...]:
    return (
        "DOC-SYN-CONTRACT-001",
        "1.0.0",
        "chk_" + "1" * 64,
        1,
        "합성 문서",
        "ALZ's well",
        "안내",
        ["문서", "안내"],
        None,
        None,
        "sha256:" + "2" * 64,
        "sha256:" + "3" * 64,
        "금융거래 안심차단 안내",
        0.5,
    )


def _config() -> DatabaseConfig:
    return DatabaseConfig(
        host="postgres",
        port=5432,
        dbname="alzs_well",
        user="alzswell_ai_runtime",
        password="secret-value",
        sslmode="prefer",
        connect_timeout=5,
    )
