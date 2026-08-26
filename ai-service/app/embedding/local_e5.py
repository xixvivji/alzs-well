from __future__ import annotations

from pathlib import Path

from app.embedding.local_sentence_transformer import (
    EncoderFactory,
    LocalSentenceTransformerEmbeddingProvider,
    LocalSentenceTransformerSpec,
)


E5_MODEL_ID = "intfloat/multilingual-e5-small"
E5_DIMENSIONS = 384


class LocalE5EmbeddingProvider(LocalSentenceTransformerEmbeddingProvider):
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
                backend="local-e5",
                model_id=E5_MODEL_ID,
                model_version_name="multilingual-e5-small",
                revision=revision,
                dimensions=E5_DIMENSIONS,
                query_prefix="query: ",
                passage_prefix="passage: ",
            ),
            encoder_factory=encoder_factory,
        )
