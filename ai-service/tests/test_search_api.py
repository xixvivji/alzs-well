from __future__ import annotations

from uuid import UUID

from fastapi.testclient import TestClient

from app.domain.search import SearchRequest, StoredSearchResult
from app.errors import KnowledgeContractError
from app.main import (
    _api_config,
    create_app,
    get_embedding_config,
    get_embedding_provider,
    get_search_repository,
)
from app.storage.search_postgres import SearchReadiness


TOKEN = "test-internal-token-that-is-longer-than-32-characters"


class FakeSearchRepository:
    index_version = "hybrid-hash-ngram-v3"

    def __init__(
        self,
        *,
        failure: str | None = None,
        start_failure: str | None = None,
        empty: bool = False,
        readiness_failure: str | None = None,
    ) -> None:
        self.run_id = UUID("98000000-0000-0000-0000-000000000001")
        self.failure = failure
        self.start_failure = start_failure
        self.empty = empty
        self.readiness_failure = readiness_failure
        self.started: tuple[SearchRequest, str] | None = None
        self.completed: tuple[UUID, int] | None = None
        self.failed: tuple[UUID, str] | None = None
        self.search_calls = 0

    def start_run(self, request: SearchRequest, query_hash: str) -> UUID:
        self.started = (request, query_hash)
        if self.start_failure is not None:
            raise KnowledgeContractError(self.start_failure)
        return self.run_id

    def search(self, request: SearchRequest) -> tuple[StoredSearchResult, ...]:
        self.search_calls += 1
        assert request.as_of.isoformat() == "2026-08-25"
        if self.failure is not None:
            raise KnowledgeContractError(self.failure)
        if self.empty:
            return ()
        return (
            StoredSearchResult(
                document_id="DOC-SYN-CONTRACT-001",
                version_label="1.0.0",
                chunk_id="chk_" + "1" * 64,
                chunk_order=1,
                title="합성 지식 계약 검증 안내",
                issuer="ALZ's well 테스트",
                heading="서비스 이용 안내",
                section_path=("안내", "서비스 이용 안내"),
                page=None,
                source_url=None,
                source_hash="sha256:" + "2" * 64,
                text_hash="sha256:" + "3" * 64,
                content="금융거래 안심차단 서비스 이용 안내입니다.",
                score=0.75,
            ),
        )

    def complete_run(self, run_id: UUID, result_count: int) -> None:
        self.completed = (run_id, result_count)

    def fail_run(self, run_id: UUID, failure_code: str) -> None:
        self.failed = (run_id, failure_code)

    def readiness(self) -> SearchReadiness:
        if self.readiness_failure is not None:
            raise KnowledgeContractError(self.readiness_failure)
        return SearchReadiness(True, True, True, True, True)


def test_health_does_not_require_internal_token(monkeypatch: object) -> None:
    client, _ = _client(monkeypatch)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"service": "ai-rag", "status": "UP"}


def test_liveness_does_not_load_configured_model(
    monkeypatch: object,
) -> None:
    monkeypatch.setenv("ALZS_EMBEDDING_BACKEND", "local-arctic-ko")  # type: ignore[attr-defined]
    monkeypatch.setenv("ALZS_ARCTIC_ROLLOUT_ENABLED", "false")  # type: ignore[attr-defined]
    get_embedding_provider.cache_clear()
    get_embedding_config.cache_clear()
    client, _ = _client(monkeypatch)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"service": "ai-rag", "status": "UP"}


def test_readiness_verifies_storage_index_embedding_and_assistance_contracts(
    monkeypatch: object,
) -> None:
    client, _ = _client(monkeypatch)

    response = client.get("/readiness")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "READY"
    assert body["checks"] == {
        "database": "UP",
        "retrievalAuditPrivileges": "UP",
        "approvedEmbedding": "UP",
        "vectorIndex": "UP",
        "searchProbe": "UP",
        "assistanceContracts": "UP",
    }
    assert body["embeddingBackend"] == "hash"
    assert body["indexVersion"] == "hybrid-hash-ngram-v3"


def test_readiness_is_not_ready_when_search_storage_times_out(monkeypatch: object) -> None:
    client, _ = _client(monkeypatch, readiness_failure="SEARCH_TIMEOUT")

    response = client.get("/readiness")

    assert response.status_code == 503
    body = response.json()
    assert body["status"] == "NOT_READY"
    assert body["checks"]["database"] == "DOWN"
    assert body["checks"]["searchProbe"] == "DOWN"
    assert body["checks"]["assistanceContracts"] == "UP"


def test_search_returns_ranked_content_and_contract_citation(monkeypatch: object) -> None:
    client, repository = _client(monkeypatch)

    response = client.post(
        "/internal/v1/search",
        headers={"X-Internal-Service-Token": TOKEN},
        json=_request(),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["contractVersion"] == "1.0.0"
    assert body["requestId"] == _request()["requestId"]
    assert body["queryHash"].startswith("sha256:")
    assert body["outcome"] == "RESULTS"
    assert body["retryable"] is False
    assert body["reasonCode"] is None
    assert body["results"][0]["score"] == 0.75
    assert body["results"][0]["citation"]["retrievalMethod"] == "HYBRID"
    assert body["results"][0]["citation"]["indexVersion"] == "hybrid-hash-ngram-v3"
    assert repository.completed == (repository.run_id, 1)
    assert repository.started is not None
    assert "금융거래" not in str(repository.started[1])


def test_search_rejects_missing_or_invalid_internal_token(monkeypatch: object) -> None:
    client, repository = _client(monkeypatch)

    response = client.post("/internal/v1/search", json=_request())

    assert response.status_code == 401
    assert response.json()["code"] == "INTERNAL_AUTHENTICATION_FAILED"
    assert repository.started is None


def test_search_requires_operation_specific_permission(monkeypatch: object) -> None:
    client, repository = _client(monkeypatch)
    payload = _request()
    payload["permissions"] = ["KNOWLEDGE_READ"]

    response = client.post(
        "/internal/v1/search",
        headers={"X-Internal-Service-Token": TOKEN},
        json=payload,
    )

    assert response.status_code == 403
    assert response.json()["code"] == "KNOWLEDGE_PERMISSION_DENIED"
    assert repository.started is None


def test_search_rejects_audience_claim_not_derived_from_roles(monkeypatch: object) -> None:
    client, repository = _client(monkeypatch)
    payload = _request()
    payload["requesterAudiences"] = ["CUSTOMER"]

    response = client.post(
        "/internal/v1/search",
        headers={"X-Internal-Service-Token": TOKEN},
        json=payload,
    )

    assert response.status_code == 422
    assert response.json() == {
        "ok": False,
        "code": "SEARCH_REQUEST_INVALID",
        "message": "지식 검색 요청이 계약을 충족하지 않습니다.",
    }
    assert repository.started is None


def test_search_records_safe_failure_code(monkeypatch: object) -> None:
    client, repository = _client(monkeypatch, failure="STORAGE_UNAVAILABLE")

    response = client.post(
        "/internal/v1/search",
        headers={"X-Internal-Service-Token": TOKEN},
        json=_request(),
    )

    assert response.status_code == 503
    assert response.json()["outcome"] == "INDEX_UNAVAILABLE"
    assert response.json()["retryable"] is True
    assert response.json()["reasonCode"] == "STORAGE_UNAVAILABLE"
    assert response.json()["results"] == []
    assert repository.failed == (repository.run_id, "STORAGE_UNAVAILABLE")


def test_search_abstains_before_retrieval_for_case_specific_final_decision(
    monkeypatch: object,
) -> None:
    client, repository = _client(monkeypatch)
    payload = _request()
    payload["query"] = "이 사건의 고객이 착오송금 반환지원 대상인지 최종 승인해 주세요."

    response = client.post(
        "/internal/v1/search",
        headers={"X-Internal-Service-Token": TOKEN},
        json=payload,
    )

    assert response.status_code == 200
    assert response.json()["results"] == []
    assert response.json()["outcome"] == "POLICY_ABSTAIN"
    assert response.json()["retryable"] is False
    assert response.json()["reasonCode"] == "POLICY_GUARDRAIL"
    assert repository.search_calls == 0
    assert repository.completed == (repository.run_id, 0)


def test_policy_abstention_cannot_become_index_fallback_when_audit_storage_fails(
    monkeypatch: object,
) -> None:
    client, repository = _client(monkeypatch, start_failure="STORAGE_UNAVAILABLE")
    payload = _request()
    payload["query"] = "이 사건의 고객이 착오송금 반환지원 대상인지 최종 승인해 주세요."

    response = client.post(
        "/internal/v1/search",
        headers={"X-Internal-Service-Token": TOKEN},
        json=payload,
    )

    assert response.status_code == 200
    assert response.json()["outcome"] == "POLICY_ABSTAIN"
    assert response.json()["reasonCode"] == "POLICY_GUARDRAIL"
    assert response.json()["retryable"] is False
    assert response.json()["results"] == []
    assert repository.search_calls == 0


def test_search_distinguishes_no_match_from_policy_abstention(monkeypatch: object) -> None:
    client, repository = _client(monkeypatch, empty=True)

    response = client.post(
        "/internal/v1/search",
        headers={"X-Internal-Service-Token": TOKEN},
        json=_request(),
    )

    assert response.status_code == 200
    assert response.json()["outcome"] == "NO_MATCH"
    assert response.json()["reasonCode"] == "NO_RELEVANT_MATCH"
    assert response.json()["retryable"] is False
    assert response.json()["results"] == []
    assert repository.search_calls == 1


def _client(
    monkeypatch: object,
    *,
    failure: str | None = None,
    start_failure: str | None = None,
    empty: bool = False,
    readiness_failure: str | None = None,
) -> tuple[TestClient, FakeSearchRepository]:
    monkeypatch.setenv("ALZS_AI_INTERNAL_TOKEN", TOKEN)  # type: ignore[attr-defined]
    _api_config.cache_clear()
    get_embedding_provider.cache_clear()
    get_embedding_config.cache_clear()
    repository = FakeSearchRepository(
        failure=failure,
        start_failure=start_failure,
        empty=empty,
        readiness_failure=readiness_failure,
    )
    application = create_app()
    application.dependency_overrides[get_search_repository] = lambda: repository
    return TestClient(application), repository


def _request() -> dict[str, object]:
    return {
        "contractVersion": "1.0.0",
        "requestId": "99000000-0000-0000-0000-000000000001",
        "query": "금융거래 안심차단",
        "permissions": ["KNOWLEDGE_SEARCH"],
        "principalRoles": ["PROTECTION_STAFF"],
        "requesterAudiences": ["STAFF"],
        "asOf": "2026-08-25",
        "limit": 10,
    }
