from __future__ import annotations

from datetime import date
from typing import Any
from uuid import UUID

import psycopg
import pytest

from app.domain.search import SearchRequest
from app.embedding.base import EmbeddingDescriptor
from app.errors import KnowledgeContractError
from app.storage.database_config import DatabaseConfig
from app.storage.search_postgres import (
    ARCTIC_INDEX_VERSION,
    E5_INDEX_VERSION,
    INDEX_VERSION,
    KEYWORD_WEIGHT,
    RESULT_THRESHOLD,
    VECTOR_THRESHOLD,
    VECTOR_WEIGHT,
    PostgresSearchRepository,
    hash_query,
)


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


class FakeEmbeddingProvider:
    descriptor = EmbeddingDescriptor(
        backend="local-e5",
        model_id="intfloat/multilingual-e5-small",
        model_version="multilingual-e5-small@test",
        dimensions=384,
    )

    def __init__(self) -> None:
        self.queries: list[str] = []

    def embed_query(self, value: str) -> tuple[float, ...]:
        self.queries.append(value)
        return (1.0,) + (0.0,) * 383

    def embed_passage(self, value: str) -> tuple[float, ...]:
        del value
        return (1.0,) + (0.0,) * 383


def test_search_sql_enforces_acl_audience_lifecycle_and_effective_date() -> None:
    cursor = FakeCursor([_row()])
    repository = PostgresSearchRepository(_config(), connect=Connector(cursor))

    results = repository.search(_request())

    statement, parameters = cursor.executions[0]
    assert "d.allowed_roles && %s::text[]" in statement
    assert "d.audience = 'BOTH'" in statement
    assert "d.approval_status = 'APPROVED'" in statement
    assert "d.lifecycle_status in ('PENDING_ACTIVATION', 'ACTIVE')" in statement
    assert "SUPERSEDED" not in statement
    assert "RETIRED" not in statement
    assert "d.effective_from <= %s" in statement
    assert "ai_knowledge.document_authority_rank(document_type)" in statement
    assert "order by authority_rank desc, score desc" in statement
    assert "left join ai_knowledge.chunk_embedding e" in statement
    assert "e.embedding::vector(384) <=> search_query.embedding" in statement
    assert "where score >= %s" in statement
    assert parameters[0] == "금융거래 안심차단"  # type: ignore[index]
    assert str(parameters[1]).startswith("[")  # type: ignore[index]
    assert parameters[2:] == (  # type: ignore[index]
        "local-hash-ngram-ko", "local-hash-ngram-ko-v1", 384,
        ["PROTECTION_STAFF"], ["STAFF"], date(2026, 8, 25),
        date(2026, 8, 25),
        KEYWORD_WEIGHT, VECTOR_WEIGHT, VECTOR_THRESHOLD, RESULT_THRESHOLD, 10,
    )
    assert "e.embedding_model_id = %s" in statement
    assert "e.embedding_model_version = %s" in statement
    assert "e.embedding_dimensions = %s" in statement
    assert INDEX_VERSION == "hybrid-hash-ngram-v3"
    assert RESULT_THRESHOLD == 0.35
    assert results[0].chunk_id == "chk_" + "1" * 64
    assert results[0].score == 0.5


def test_search_uses_injected_model_but_keeps_other_models_for_keyword_score() -> None:
    cursor = FakeCursor([_row()])
    provider = FakeEmbeddingProvider()
    repository = PostgresSearchRepository(
        _config(), connect=Connector(cursor), embedding_provider=provider
    )

    repository.search(_request())

    statement, parameters = cursor.executions[0]
    assert provider.queries == ["금융거래 안심차단"]
    assert parameters[2:5] == (  # type: ignore[index]
        "intfloat/multilingual-e5-small",
        "multilingual-e5-small@test",
        384,
    )
    assert "e.embedding_model_version = %s" in statement


def test_search_uses_1024_vector_cast_for_arctic_provider() -> None:
    class ArcticProvider(FakeEmbeddingProvider):
        descriptor = EmbeddingDescriptor(
            backend="local-arctic-ko",
            model_id="dragonkue/snowflake-arctic-embed-l-v2.0-ko",
            model_version="snowflake-arctic-embed-l-v2.0-ko@test",
            dimensions=1024,
        )

        def embed_query(self, value: str) -> tuple[float, ...]:
            self.queries.append(value)
            return (1.0,) + (0.0,) * 1023

    cursor = FakeCursor([_row()])
    repository = PostgresSearchRepository(
        _config(), connect=Connector(cursor), embedding_provider=ArcticProvider()
    )

    repository.search(_request())

    statement, parameters = cursor.executions[0]
    assert "%s::vector(1024) as embedding" in statement
    assert "e.embedding::vector(1024) <=> search_query.embedding" in statement
    assert parameters[2:5] == (  # type: ignore[index]
        "dragonkue/snowflake-arctic-embed-l-v2.0-ko",
        "snowflake-arctic-embed-l-v2.0-ko@test",
        1024,
    )
    assert repository.index_version == ARCTIC_INDEX_VERSION


def test_search_rejects_unsupported_embedding_dimension_before_database_call() -> None:
    class UnsupportedProvider(FakeEmbeddingProvider):
        descriptor = EmbeddingDescriptor(
            backend="unsupported",
            model_id="unsupported/model",
            model_version="unsupported@test",
            dimensions=768,
        )

    connector = Connector()
    repository = PostgresSearchRepository(
        _config(), connect=connector, embedding_provider=UnsupportedProvider()
    )

    with pytest.raises(KnowledgeContractError) as caught:
        repository.search(_request())

    assert caught.value.code == "EMBEDDING_VECTOR_INVALID"
    assert connector.cursors == []


def test_search_run_records_e5_index_version() -> None:
    cursor = FakeCursor()
    repository = PostgresSearchRepository(
        _config(), connect=Connector(cursor), embedding_provider=FakeEmbeddingProvider()
    )

    repository.start_run(_request(), "sha256:" + "0" * 64)

    parameters = cursor.executions[0][1]
    assert E5_INDEX_VERSION in parameters  # type: ignore[operator]
    assert repository.index_version == "hybrid-multilingual-e5-small-v2"


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
