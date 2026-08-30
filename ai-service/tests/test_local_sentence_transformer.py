from __future__ import annotations

import math
import sys
import types
from pathlib import Path

import pytest

from app.embedding.local_sentence_transformer import (
    LocalSentenceTransformerEmbeddingProvider,
    LocalSentenceTransformerSpec,
)
from app.errors import KnowledgeContractError


class FakeEncoder:
    def __init__(
        self, vector: list[float] | None = None, *, failure: bool = False
    ) -> None:
        self.vector = vector or [1.0] * 1024
        self.failure = failure
        self.calls: list[tuple[list[str], dict[str, object]]] = []

    def encode(self, sentences: list[str], **kwargs: object) -> list[list[float]]:
        self.calls.append((sentences, kwargs))
        if self.failure:
            raise RuntimeError("sensitive runtime detail")
        return [self.vector]


def _spec(*, dimensions: int = 1024) -> LocalSentenceTransformerSpec:
    return LocalSentenceTransformerSpec(
        backend="local-sentence-transformer",
        model_id="dragonkue/snowflake-arctic-embed-l-v2.0-ko",
        model_version_name="snowflake-arctic-embed-l-v2.0-ko",
        revision="a" * 40,
        dimensions=dimensions,
        query_prefix="query: ",
        passage_prefix="",
    )


def test_generic_provider_applies_model_specific_prefixes_and_normalizes() -> None:
    encoder = FakeEncoder()
    paths: list[Path] = []

    def factory(path: Path) -> FakeEncoder:
        paths.append(path)
        return encoder

    provider = LocalSentenceTransformerEmbeddingProvider(
        Path("/approved/arctic"), spec=_spec(), encoder_factory=factory
    )

    query = provider.embed_query("  금융거래   안심차단 ")
    passage = provider.embed_passage("Cafe\u0301   안내")

    assert paths == [Path("/approved/arctic")]
    assert encoder.calls[0][0] == ["query: 금융거래 안심차단"]
    assert encoder.calls[1][0] == ["Café 안내"]
    assert encoder.calls[0][1] == {
        "normalize_embeddings": True,
        "show_progress_bar": False,
    }
    assert len(query) == len(passage) == 1024
    assert math.sqrt(sum(value * value for value in query)) == pytest.approx(1.0)
    assert provider.descriptor.model_version == (
        "snowflake-arctic-embed-l-v2.0-ko@" + "a" * 40
    )


def test_generic_provider_sanitizes_factory_and_encoding_failures() -> None:
    def unavailable(path: Path) -> FakeEncoder:
        del path
        raise RuntimeError("private model path")

    with pytest.raises(KnowledgeContractError) as factory_failure:
        LocalSentenceTransformerEmbeddingProvider(
            Path("/approved/arctic"), spec=_spec(), encoder_factory=unavailable
        )
    assert factory_failure.value.code == "EMBEDDING_MODEL_UNAVAILABLE"
    assert "private" not in factory_failure.value.safe_message

    provider = LocalSentenceTransformerEmbeddingProvider(
        Path("/approved/arctic"),
        spec=_spec(),
        encoder_factory=lambda path: FakeEncoder(failure=True),
    )
    with pytest.raises(KnowledgeContractError) as encoding_failure:
        provider.embed_query("질문")
    assert encoding_failure.value.code == "EMBEDDING_MODEL_UNAVAILABLE"


def test_generic_provider_rejects_invalid_vector_and_preserves_contract_error() -> None:
    provider = LocalSentenceTransformerEmbeddingProvider(
        Path("/approved/arctic"),
        spec=_spec(),
        encoder_factory=lambda path: FakeEncoder([1.0] * 1023),
    )
    with pytest.raises(KnowledgeContractError) as invalid:
        provider.embed_passage("근거")
    assert invalid.value.code == "EMBEDDING_VECTOR_INVALID"

    def contract_failure(path: Path) -> FakeEncoder:
        del path
        raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE")

    with pytest.raises(KnowledgeContractError) as preserved:
        LocalSentenceTransformerEmbeddingProvider(
            Path("/approved/arctic"), spec=_spec(), encoder_factory=contract_failure
        )
    assert preserved.value.code == "EMBEDDING_MODEL_UNAVAILABLE"


def test_default_loader_forces_cpu_and_offline_only_options(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls: list[tuple[str, dict[str, object]]] = []

    class StubSentenceTransformer(FakeEncoder):
        def __init__(self, path: str, **kwargs: object) -> None:
            super().__init__()
            calls.append((path, kwargs))

    module = types.ModuleType("sentence_transformers")
    module.SentenceTransformer = StubSentenceTransformer  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "sentence_transformers", module)

    provider = LocalSentenceTransformerEmbeddingProvider(
        Path("/approved/arctic"), spec=_spec()
    )
    provider.embed_query("질문")

    assert calls == [
        (
            "/approved/arctic",
            {
                "device": "cpu",
                "local_files_only": True,
                "trust_remote_code": False,
            },
        )
    ]


def test_default_loader_sanitizes_runtime_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class BrokenSentenceTransformer:
        def __init__(self, path: str, **kwargs: object) -> None:
            del path, kwargs
            raise RuntimeError("private loader detail")

    module = types.ModuleType("sentence_transformers")
    module.SentenceTransformer = BrokenSentenceTransformer  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "sentence_transformers", module)

    with pytest.raises(KnowledgeContractError) as failure:
        LocalSentenceTransformerEmbeddingProvider(
            Path("/approved/arctic"), spec=_spec()
        )
    assert failure.value.code == "EMBEDDING_MODEL_UNAVAILABLE"
    assert "private" not in failure.value.safe_message
