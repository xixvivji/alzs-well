from __future__ import annotations

import math
from dataclasses import dataclass

from app.embedding.base import EmbeddingProvider
from app.embedding.local_hash import LocalHashEmbeddingProvider
from app.evaluation.models import EvaluationCase, EvaluationChunk
from app.retrieval.query import keyword_terms, normalize, requires_abstention


DOCUMENT_AUTHORITY = {
    "LAW": 600,
    "REGULATION": 500,
    "INTERNAL_POLICY": 400,
    "PUBLIC_GUIDE": 300,
    "PUBLIC_NOTICE": 200,
    "FORM": 100,
    "SYNTHETIC_FIXTURE": 0,
}


@dataclass(frozen=True, slots=True)
class SearchConfiguration:
    keyword_weight: float = 0.35
    vector_weight: float = 0.65
    vector_threshold: float = 0.15
    result_threshold: float = 0.35

    def __post_init__(self) -> None:
        if min(
            self.keyword_weight, self.vector_weight, self.vector_threshold, self.result_threshold
        ) < 0:
            raise ValueError("search configuration cannot be negative")
        if max(
            self.keyword_weight, self.vector_weight, self.vector_threshold, self.result_threshold
        ) > 1:
            raise ValueError("search configuration cannot exceed one")
        if not math.isclose(self.keyword_weight + self.vector_weight, 1.0, abs_tol=1e-9):
            raise ValueError("search weights must sum to one")


@dataclass(frozen=True, slots=True)
class RankedChunk:
    chunk: EvaluationChunk
    score: float
    keyword_score: float
    vector_score: float


def rank(
    case: EvaluationCase,
    corpus: tuple[EvaluationChunk, ...],
    configuration: SearchConfiguration,
    *,
    limit: int = 5,
    embedding_provider: EmbeddingProvider | None = None,
) -> tuple[RankedChunk, ...]:
    if requires_abstention(case.query):
        return ()
    provider = embedding_provider or LocalHashEmbeddingProvider()
    query_embedding = provider.embed_query(case.query)
    ranked: list[RankedChunk] = []
    for chunk in corpus:
        if not is_eligible(chunk, case):
            continue
        keyword_score = _keyword_score(case.query, chunk.searchable_text())
        vector_score = max(
            0.0, _cosine(query_embedding, provider.embed_passage(chunk.searchable_text()))
        )
        if keyword_score == 0 and vector_score < configuration.vector_threshold:
            continue
        score = (
            keyword_score * configuration.keyword_weight
            + vector_score * configuration.vector_weight
        )
        if score < configuration.result_threshold:
            continue
        ranked.append(
            RankedChunk(
                chunk=chunk,
                score=score,
                keyword_score=keyword_score,
                vector_score=vector_score,
            )
        )
    ranked.sort(
        key=lambda item: (
            -item.score,
            -DOCUMENT_AUTHORITY.get(item.chunk.document_type, 0),
            item.chunk.document_id,
            item.chunk.chunk_id,
        )
    )
    return tuple(ranked[:limit])


def is_eligible(chunk: EvaluationChunk, case: EvaluationCase) -> bool:
    if not set(chunk.allowed_roles) & set(case.principal_roles):
        return False
    if chunk.audience != "BOTH" and chunk.audience not in case.requester_audiences:
        return False
    if chunk.approval_status != "APPROVED" or chunk.lifecycle_status != "ACTIVE":
        return False
    if chunk.effective_from > case.as_of:
        return False
    return chunk.effective_to is None or chunk.effective_to >= case.as_of


def _keyword_score(query: str, text: str) -> float:
    terms = set(keyword_terms(query))
    if not terms:
        return 0.0
    searchable = normalize(text)
    return sum(term in searchable for term in terms) / len(terms)


def _cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    return sum(a * b for a, b in zip(left, right, strict=True))
