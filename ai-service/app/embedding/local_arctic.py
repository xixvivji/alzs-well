from __future__ import annotations

from pathlib import Path

from app.embedding.local_sentence_transformer import (
    EncoderFactory,
    LocalSentenceTransformerEmbeddingProvider,
    LocalSentenceTransformerSpec,
)


ARCTIC_MODEL_ID = "dragonkue/snowflake-arctic-embed-l-v2.0-ko"
ARCTIC_MODEL_VERSION_NAME = "snowflake-arctic-embed-l-v2.0-ko"
ARCTIC_MODEL_REVISION = "55ec6e9358a56d56af759bc8372e970caf8c305f"
ARCTIC_MODEL_SHA256 = (
    "sha256:0b874517f0fd02dd9510fa2733aacaad1def6086387c88d1a21f4041351e15b0"
)
ARCTIC_DIMENSIONS = 1024


class LocalArcticKoEmbeddingProvider(LocalSentenceTransformerEmbeddingProvider):
    def __init__(
        self,
        model_path: Path,
        *,
        revision: str,
        encoder_factory: EncoderFactory | None = None,
    ) -> None:
        super().__init__(
            model_path,
            spec=LocalSentenceTransformerSpec(
                backend="local-arctic-ko",
                model_id=ARCTIC_MODEL_ID,
                model_version_name=ARCTIC_MODEL_VERSION_NAME,
                revision=revision,
                dimensions=ARCTIC_DIMENSIONS,
                query_prefix="query: ",
                passage_prefix="",
            ),
            encoder_factory=encoder_factory,
        )
