from __future__ import annotations

import unicodedata
from collections.abc import Callable
from pathlib import Path
from typing import Any, Protocol

from app.embedding.base import EmbeddingDescriptor, EmbeddingVector, normalized_vector
from app.errors import KnowledgeContractError


E5_MODEL_ID = "intfloat/multilingual-e5-small"
E5_DIMENSIONS = 384


class SentenceEncoder(Protocol):
    def encode(self, sentences: list[str], **kwargs: object) -> Any: ...


EncoderFactory = Callable[[Path], SentenceEncoder]


class LocalE5EmbeddingProvider:
    def __init__(
        self,
        model_path: Path,
        *,
        revision: str,
        encoder_factory: EncoderFactory | None = None,
    ) -> None:
        self.descriptor = EmbeddingDescriptor(
            backend="local-e5",
            model_id=E5_MODEL_ID,
            model_version=f"multilingual-e5-small@{revision}",
            dimensions=E5_DIMENSIONS,
        )
        factory = _load_sentence_transformer if encoder_factory is None else encoder_factory
        try:
            self._encoder = factory(model_path)
        except KnowledgeContractError:
            raise
        except Exception:
            raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE") from None

    def embed_query(self, value: str) -> EmbeddingVector:
        return self._embed(f"query: {_normalize(value)}")

    def embed_passage(self, value: str) -> EmbeddingVector:
        return self._embed(f"passage: {_normalize(value)}")

    def _embed(self, value: str) -> EmbeddingVector:
        try:
            encoded = self._encoder.encode(
                [value], normalize_embeddings=True, show_progress_bar=False
            )
            first = encoded[0]
        except KnowledgeContractError:
            raise
        except Exception:
            raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE") from None
        return normalized_vector(first, dimensions=self.descriptor.dimensions)


def _load_sentence_transformer(model_path: Path) -> SentenceEncoder:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError:
        raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE") from None
    try:
        return SentenceTransformer(
            str(model_path), local_files_only=True, trust_remote_code=False
        )
    except Exception:
        raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE") from None


def _normalize(value: str) -> str:
    return unicodedata.normalize("NFC", " ".join(value.split()))
