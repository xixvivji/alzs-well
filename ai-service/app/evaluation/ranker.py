from __future__ import annotations

import math
import re
import unicodedata
from dataclasses import dataclass

from app.embedding.local_hash import embed_text
from app.evaluation.models import EvaluationCase, EvaluationChunk


TOKEN_PATTERN = re.compile(r"[0-9A-Za-z가-힣]{2,}")


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
) -> tuple[RankedChunk, ...]:
    query_embedding = embed_text(case.query)
    ranked: list[RankedChunk] = []
    for chunk in corpus:
        if not is_eligible(chunk, case):
            continue
        keyword_score = _keyword_score(case.query, chunk.searchable_text())
        vector_score = max(0.0, _cosine(query_embedding, embed_text(chunk.searchable_text())))
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
    ranked.sort(key=lambda item: (-item.score, item.chunk.document_id, item.chunk.chunk_id))
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
    terms = set(TOKEN_PATTERN.findall(_normalize(query)))
    if not terms:
        return 0.0
    searchable = _normalize(text)
    return sum(term in searchable for term in terms) / len(terms)


def _normalize(value: str) -> str:
    return unicodedata.normalize("NFC", " ".join(value.lower().split()))


def _cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    return sum(a * b for a, b in zip(left, right, strict=True))
