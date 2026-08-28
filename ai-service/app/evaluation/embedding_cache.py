from __future__ import annotations

from app.embedding.base import EmbeddingDescriptor, EmbeddingProvider, EmbeddingVector


class CachedEmbeddingProvider:
    """Per-evaluation memoization for deterministic local embedding providers."""

    def __init__(self, delegate: EmbeddingProvider) -> None:
        self._delegate = delegate
        self.descriptor: EmbeddingDescriptor = delegate.descriptor
        self._queries: dict[str, EmbeddingVector] = {}
        self._passages: dict[str, EmbeddingVector] = {}

    def embed_query(self, value: str) -> EmbeddingVector:
        if value not in self._queries:
            self._queries[value] = self._delegate.embed_query(value)
        return self._queries[value]

    def embed_passage(self, value: str) -> EmbeddingVector:
        if value not in self._passages:
            self._passages[value] = self._delegate.embed_passage(value)
        return self._passages[value]


def cached(provider: EmbeddingProvider | None) -> EmbeddingProvider | None:
    if provider is None or isinstance(provider, CachedEmbeddingProvider):
        return provider
    return CachedEmbeddingProvider(provider)
