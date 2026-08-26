from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Protocol, runtime_checkable

from app.errors import KnowledgeContractError


EmbeddingVector = tuple[float, ...]


@dataclass(frozen=True, slots=True)
class EmbeddingDescriptor:
    backend: str
    model_id: str
    model_version: str
    dimensions: int


@runtime_checkable
class EmbeddingProvider(Protocol):
    descriptor: EmbeddingDescriptor

    def embed_query(self, value: str) -> EmbeddingVector: ...

    def embed_passage(self, value: str) -> EmbeddingVector: ...


def normalized_vector(values: object, *, dimensions: int) -> EmbeddingVector:
    try:
        vector = tuple(float(value) for value in values)  # type: ignore[union-attr]
    except (TypeError, ValueError):
        raise KnowledgeContractError("EMBEDDING_VECTOR_INVALID") from None
    if len(vector) != dimensions or not all(math.isfinite(value) for value in vector):
        raise KnowledgeContractError("EMBEDDING_VECTOR_INVALID")
    norm = math.sqrt(sum(value * value for value in vector))
    if norm == 0:
        raise KnowledgeContractError("EMBEDDING_VECTOR_INVALID")
    return tuple(value / norm for value in vector)


def vector_literal(vector: EmbeddingVector, *, dimensions: int) -> str:
    if len(vector) != dimensions or not all(math.isfinite(value) for value in vector):
        raise KnowledgeContractError("EMBEDDING_VECTOR_INVALID")
    return "[" + ",".join(format(value, ".9g") for value in vector) + "]"
