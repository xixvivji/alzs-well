from __future__ import annotations

import unicodedata
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from app.embedding.base import EmbeddingDescriptor, EmbeddingVector, normalized_vector
from app.errors import KnowledgeContractError


class SentenceEncoder(Protocol):
    def encode(self, sentences: list[str], **kwargs: object) -> Any: ...


EncoderFactory = Callable[[Path], SentenceEncoder]


@dataclass(frozen=True, slots=True)
class LocalSentenceTransformerSpec:
    backend: str
    model_id: str
    model_version_name: str
    revision: str
    dimensions: int
    query_prefix: str
    passage_prefix: str


class LocalSentenceTransformerEmbeddingProvider:
    def __init__(
        self,
        model_path: Path,
        *,
        spec: LocalSentenceTransformerSpec,
        encoder_factory: EncoderFactory | None = None,
    ) -> None:
        self.descriptor = EmbeddingDescriptor(
            backend=spec.backend,
            model_id=spec.model_id,
            model_version=f"{spec.model_version_name}@{spec.revision}",
            dimensions=spec.dimensions,
        )
        self._query_prefix = spec.query_prefix
        self._passage_prefix = spec.passage_prefix
        factory = _load_sentence_transformer if encoder_factory is None else encoder_factory
        try:
            self._encoder = factory(model_path)
        except KnowledgeContractError:
            raise
        except Exception:
            raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE") from None

    def embed_query(self, value: str) -> EmbeddingVector:
        return self._embed(self._query_prefix + _normalize(value))

    def embed_passage(self, value: str) -> EmbeddingVector:
        return self._embed(self._passage_prefix + _normalize(value))

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
            str(model_path),
            device="cpu",
            local_files_only=True,
            trust_remote_code=False,
        )
    except Exception:
        raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE") from None


def _normalize(value: str) -> str:
    return unicodedata.normalize("NFC", " ".join(value.split()))
