from __future__ import annotations

import hmac
from functools import lru_cache
from typing import Annotated

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api_config import ApiConfig
from app.assistance.change import analyze_changes
from app.assistance.intent import structure_intent
from app.assistance.plain_language import plain_language
from app.domain.assistance import (
    ChangeAnalysisRequest,
    ChangeAnalysisResponse,
    IntentStructureRequest,
    IntentStructureResponse,
    PlainLanguageRequest,
    PlainLanguageResponse,
)
from app.domain.search import Citation, SearchRequest, SearchResponse, SearchResult
from app.embedding.base import EmbeddingProvider
from app.embedding.config import EmbeddingConfig, create_embedding_provider
from app.errors import KnowledgeContractError
from app.readiness import assistance_contracts_ready
from app.retrieval.query import requires_abstention
from app.storage.database_config import DatabaseConfig
from app.storage.search_postgres import (
    PostgresSearchRepository,
    hash_query,
    index_version_for_backend,
)


def create_app() -> FastAPI:
    application = FastAPI(
        title="ALZ's well internal AI/RAG",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    application.add_exception_handler(KnowledgeContractError, _knowledge_error_handler)
    application.add_exception_handler(RequestValidationError, _validation_error_handler)

    @application.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP", "service": "ai-rag"}

    @application.get("/readiness")
    def readiness(
        repository: Annotated[PostgresSearchRepository, Depends(get_search_repository)],
    ) -> JSONResponse:
        config = get_embedding_config()
        descriptor = get_embedding_provider().descriptor
        try:
            storage = repository.readiness()
            checks = {
                "database": storage.database,
                "retrievalAuditPrivileges": storage.retrieval_audit_privileges,
                "approvedEmbedding": storage.approved_embedding,
                "vectorIndex": storage.vector_index,
                "searchProbe": storage.search_probe,
                "assistanceContracts": assistance_contracts_ready(),
            }
        except KnowledgeContractError:
            checks = {
                "database": False,
                "retrievalAuditPrivileges": False,
                "approvedEmbedding": False,
                "vectorIndex": False,
                "searchProbe": False,
                "assistanceContracts": assistance_contracts_ready(),
            }
        ready = all(checks.values())
        content: dict[str, object] = {
            "status": "READY" if ready else "NOT_READY",
            "service": "ai-rag",
            "checks": {name: "UP" if value else "DOWN" for name, value in checks.items()},
            "embeddingConfiguredBackend": config.backend,
            "embeddingBackend": descriptor.backend,
            "embeddingModelVersion": descriptor.model_version,
            "embeddingDimensions": descriptor.dimensions,
            "modelStatus": config.model_status or "BUILT_IN",
            "modelRevision": config.model_revision or descriptor.model_version,
            "artifactSha256": config.model_sha256,
            "goldenSetSha256": config.golden_set_sha256,
            "indexVersion": index_version_for_backend(descriptor.backend),
            "arcticRolloutEnabled": config.arctic_rollout_enabled,
            "deploymentEnvironment": config.deployment_environment,
            "stagedApprovalEnabled": config.staged_approval_enabled,
            "embeddingFallbackUsed": (
                config.backend == "local-arctic-ko"
                and config.arctic_rollout_enabled
                and descriptor.backend == "hash"
            ),
        }
        return JSONResponse(status_code=200 if ready else 503, content=content)

    @application.post(
        "/internal/v1/search",
        response_model=SearchResponse,
        response_model_by_alias=True,
        dependencies=[Depends(_verify_internal_token)],
    )
    def search(
        payload: SearchRequest,
        repository: Annotated[PostgresSearchRepository, Depends(get_search_repository)],
    ) -> SearchResponse | JSONResponse:
        if "KNOWLEDGE_SEARCH" not in payload.permissions:
            raise KnowledgeContractError("KNOWLEDGE_PERMISSION_DENIED")
        query_hash = hash_query(payload.query)
        policy_abstain = requires_abstention(payload.query)
        if policy_abstain:
            # 정책 거절은 검색·감사 저장소보다 먼저 결정한다. 감사 저장 실패가 더 허용적인
            # INDEX_UNAVAILABLE 폴백으로 바뀌어 정책을 우회해서는 안 된다.
            policy_run_id = None
            try:
                policy_run_id = repository.start_run(payload, query_hash)
                repository.complete_run(policy_run_id, 0)
            except KnowledgeContractError as error:
                if policy_run_id is not None:
                    try:
                        repository.fail_run(policy_run_id, error.code)
                    except KnowledgeContractError:
                        pass
            return SearchResponse(
                request_id=payload.request_id,
                query_hash=query_hash,
                outcome="POLICY_ABSTAIN",
                retryable=False,
                reason_code="POLICY_GUARDRAIL",
                results=(),
            )
        run_id = None
        try:
            run_id = repository.start_run(payload, query_hash)
            stored_results = repository.search(payload)
            repository.complete_run(run_id, len(stored_results))
        except KnowledgeContractError as error:
            if run_id is not None:
                try:
                    repository.fail_run(run_id, error.code)
                except KnowledgeContractError:
                    pass
            if error.code in {
                "STORAGE_UNAVAILABLE",
                "SEARCH_TIMEOUT",
                "EMBEDDING_MODEL_UNAVAILABLE",
                "EMBEDDING_VECTOR_INVALID",
            }:
                unavailable = SearchResponse(
                    request_id=payload.request_id,
                    query_hash=query_hash,
                    outcome="INDEX_UNAVAILABLE",
                    retryable=True,
                    reason_code=error.code,
                    results=(),
                )
                return JSONResponse(
                    status_code=503,
                    content=unavailable.model_dump(mode="json", by_alias=True),
                )
            raise
        results = tuple(
            SearchResult(
                score=result.score,
                content=result.content,
                citation=Citation(
                    document_id=result.document_id,
                    version_label=result.version_label,
                    chunk_id=result.chunk_id,
                    chunk_order=result.chunk_order,
                    title=result.title,
                    issuer=result.issuer,
                    heading=result.heading,
                    section_path=result.section_path,
                    page=result.page,
                    citation_label=f"{result.title} > {result.heading}",
                    source_url=result.source_url,
                    source_hash=result.source_hash,
                    text_hash=result.text_hash,
                    retrieved_as_of=payload.as_of,
                    index_version=repository.index_version,
                ),
            )
            for result in stored_results
        )
        return SearchResponse(
            request_id=payload.request_id,
            query_hash=query_hash,
            outcome=(
                "RESULTS" if results else "NO_MATCH"
            ),
            retryable=False,
            reason_code=(
                None if results else "NO_RELEVANT_MATCH"
            ),
            results=results,
        )

    @application.post(
        "/internal/v1/intent-structure",
        response_model=IntentStructureResponse,
        response_model_by_alias=True,
        dependencies=[Depends(_verify_internal_token)],
    )
    def intent_structure(payload: IntentStructureRequest) -> IntentStructureResponse:
        return structure_intent(payload, get_embedding_provider())

    @application.post(
        "/internal/v1/change-analysis",
        response_model=ChangeAnalysisResponse,
        response_model_by_alias=True,
        dependencies=[Depends(_verify_internal_token)],
    )
    def change_analysis(payload: ChangeAnalysisRequest) -> ChangeAnalysisResponse:
        return analyze_changes(payload)

    @application.post(
        "/internal/v1/plain-language",
        response_model=PlainLanguageResponse,
        response_model_by_alias=True,
        dependencies=[Depends(_verify_internal_token)],
    )
    def generate_plain_language(payload: PlainLanguageRequest) -> PlainLanguageResponse:
        return plain_language(payload)

    return application


@lru_cache
def get_search_repository() -> PostgresSearchRepository:
    return PostgresSearchRepository(
        DatabaseConfig.from_environment(), embedding_provider=get_embedding_provider()
    )


@lru_cache
def get_embedding_provider() -> EmbeddingProvider:
    return create_embedding_provider(get_embedding_config())


@lru_cache
def get_embedding_config() -> EmbeddingConfig:
    return EmbeddingConfig.from_environment()


@lru_cache
def _api_config() -> ApiConfig:
    return ApiConfig.from_environment()


def _verify_internal_token(
    token: Annotated[str | None, Header(alias="X-Internal-Service-Token")] = None,
) -> None:
    expected = _api_config().internal_token
    if token is None or not hmac.compare_digest(token, expected):
        raise KnowledgeContractError("INTERNAL_AUTHENTICATION_FAILED")


async def _knowledge_error_handler(
    request: Request, error: KnowledgeContractError
) -> JSONResponse:
    del request
    status = {
        "INTERNAL_AUTHENTICATION_FAILED": 401,
        "KNOWLEDGE_PERMISSION_DENIED": 403,
        "SEARCH_REQUEST_CONFLICT": 409,
        "SEARCH_TIMEOUT": 503,
        "STORAGE_UNAVAILABLE": 503,
        "API_CONFIGURATION_INVALID": 500,
        "DATABASE_CONFIGURATION_INVALID": 500,
        "EMBEDDING_CONFIGURATION_INVALID": 500,
        "EMBEDDING_MODEL_UNAVAILABLE": 503,
        "EMBEDDING_VECTOR_INVALID": 503,
    }.get(error.code, 500)
    return JSONResponse(
        status_code=status,
        content={"ok": False, "code": error.code, "message": error.safe_message},
    )


async def _validation_error_handler(
    request: Request, error: RequestValidationError
) -> JSONResponse:
    del error
    code = (
        "AI_ASSISTANCE_REQUEST_INVALID"
        if request.url.path.startswith("/internal/v1/") and request.url.path != "/internal/v1/search"
        else "SEARCH_REQUEST_INVALID"
    )
    contract_error = KnowledgeContractError(code)
    return JSONResponse(
        status_code=422,
        content={
            "ok": False,
            "code": contract_error.code,
            "message": contract_error.safe_message,
        },
    )


app = create_app()
