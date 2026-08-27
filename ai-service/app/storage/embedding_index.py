from __future__ import annotations

from app.errors import KnowledgeContractError


SUPPORTED_EMBEDDING_DIMENSIONS = frozenset({384, 1024})


def vector_type(dimensions: int) -> str:
    if dimensions not in SUPPORTED_EMBEDDING_DIMENSIONS:
        raise KnowledgeContractError("EMBEDDING_VECTOR_INVALID")
    return f"vector({dimensions})"
