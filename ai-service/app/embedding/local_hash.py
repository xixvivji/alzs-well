from __future__ import annotations

import math
import unicodedata
from hashlib import sha256

from app.embedding.base import EmbeddingDescriptor, EmbeddingVector
from app.embedding.base import vector_literal as _vector_literal


EMBEDDING_DIMENSIONS = 384
EMBEDDING_MODEL_VERSION = "local-hash-ngram-ko-v1"


class LocalHashEmbeddingProvider:
    descriptor = EmbeddingDescriptor(
        backend="hash",
        model_id="local-hash-ngram-ko",
        model_version=EMBEDDING_MODEL_VERSION,
        dimensions=EMBEDDING_DIMENSIONS,
    )

    def embed_query(self, value: str) -> EmbeddingVector:
        return embed_text(value)

    def embed_passage(self, value: str) -> EmbeddingVector:
        return embed_text(value)


def embed_text(value: str) -> tuple[float, ...]:
    """Return a deterministic, normalized feature-hashing embedding.

    Character bi/tri-grams work without a tokenizer, cover Korean text, and keep
    the MVP fully air-gapped. This is intentionally replaceable by an approved
    internal model while preserving the pgvector storage and search boundary.
    """

    normalized = unicodedata.normalize("NFC", " ".join(value.lower().split()))
    features: list[str] = []
    for token in normalized.split():
        padded = f"^{token}$"
        features.append(f"w:{token}")
        for width in (2, 3):
            features.extend(
                f"c{width}:{padded[index:index + width]}"
                for index in range(max(0, len(padded) - width + 1))
            )
    vector = [0.0] * EMBEDDING_DIMENSIONS
    for feature in features:
        digest = sha256(feature.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % EMBEDDING_DIMENSIONS
        vector[index] += -1.0 if digest[4] & 1 else 1.0
    norm = math.sqrt(sum(component * component for component in vector))
    if norm == 0:
        return tuple(vector)
    return tuple(component / norm for component in vector)


def vector_literal(vector: tuple[float, ...]) -> str:
    return _vector_literal(vector, dimensions=EMBEDDING_DIMENSIONS)
