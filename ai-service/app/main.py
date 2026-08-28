from __future__ import annotations

import hmac
from functools import lru_cache
from typing import Annotated

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api_config import ApiConfig
from app.domain.search import Citation, SearchRequest, SearchResponse, SearchResult
from app.embedding.base import EmbeddingProvider
from app.embedding.config import EmbeddingConfig, create_embedding_provider
from app.errors import KnowledgeContractError
from app.retrieval.query import requires_abstention
from app.storage.database_config import DatabaseConfig
from app.storage.search_postgres import PostgresSearchRepository, hash_query


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
        descriptor = get_embedding_provider().descriptor
        return {
            "status": "UP",
            "service": "ai-rag",
            "embeddingBackend": descriptor.backend,
            "embeddingModelVersion": descriptor.model_version,
        }

    @application.post(
        "/internal/v1/search",
        response_model=SearchResponse,
        response_model_by_alias=True,
        dependencies=[Depends(_verify_internal_token)],
    )
    def search(
        payload: SearchRequest,
        repository: Annotated[PostgresSearchRepository, Depends(get_search_repository)],
    ) -> SearchResponse:
        if "KNOWLEDGE_SEARCH" not in payload.permissions:
            raise KnowledgeContractError("KNOWLEDGE_PERMISSION_DENIED")
        query_hash = hash_query(payload.query)
        run_id = repository.start_run(payload, query_hash)
        try:
            stored_results = (
                () if requires_abstention(payload.query) else repository.search(payload)
            )
            repository.complete_run(run_id, len(stored_results))
        except KnowledgeContractError as error:
            try:
                repository.fail_run(run_id, error.code)
            except KnowledgeContractError:
                pass
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
            results=results,
        )

    return application


@lru_cache
def get_search_repository() -> PostgresSearchRepository:
    return PostgresSearchRepository(
        DatabaseConfig.from_environment(), embedding_provider=get_embedding_provider()
    )


@lru_cache
def get_embedding_provider() -> EmbeddingProvider:
    return create_embedding_provider(EmbeddingConfig.from_environment())


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
    del request, error
    contract_error = KnowledgeContractError("SEARCH_REQUEST_INVALID")
    return JSONResponse(
        status_code=422,
        content={
            "ok": False,
            "code": contract_error.code,
            "message": contract_error.safe_message,
        },
    )


app = create_app()
