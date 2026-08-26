from __future__ import annotations

import math
from hashlib import sha256
from pathlib import Path
from typing import Any

import pytest

from app.embedding.base import EmbeddingDescriptor
from app.embedding.config import EmbeddingConfig, create_embedding_provider
from app.embedding.local_e5 import E5_DIMENSIONS, LocalE5EmbeddingProvider
from app.embedding.local_hash import LocalHashEmbeddingProvider
from app.errors import KnowledgeContractError


class FakeEncoder:
    def __init__(self, vector: list[float] | None = None, *, failure: bool = False) -> None:
        self.vector = vector or [1.0] * E5_DIMENSIONS
        self.failure = failure
        self.calls: list[tuple[list[str], dict[str, object]]] = []

    def encode(self, sentences: list[str], **kwargs: object) -> list[list[float]]:
        self.calls.append((sentences, kwargs))
        if self.failure:
            raise RuntimeError("sensitive model failure")
        return [self.vector]


class StubProvider:
    descriptor = EmbeddingDescriptor(
        backend="local-e5",
        model_id="intfloat/multilingual-e5-small",
        model_version="multilingual-e5-small@test-revision",
        dimensions=384,
    )

    def embed_query(self, value: str) -> tuple[float, ...]:
        del value
        return (1.0,) + (0.0,) * 383

    def embed_passage(self, value: str) -> tuple[float, ...]:
        del value
        return (1.0,) + (0.0,) * 383


def test_local_e5_uses_query_and_passage_prefixes_and_normalizes() -> None:
    encoder = FakeEncoder()
    provider = LocalE5EmbeddingProvider(
        Path("/approved/model"),
        revision="approved-revision",
        encoder_factory=lambda path: encoder,
    )

    query = provider.embed_query("  금융거래   안심차단 ")
    passage = provider.embed_passage("서비스 신청 안내")

    assert encoder.calls[0][0] == ["query: 금융거래 안심차단"]
    assert encoder.calls[1][0] == ["passage: 서비스 신청 안내"]
    assert encoder.calls[0][1] == {
        "normalize_embeddings": True,
        "show_progress_bar": False,
    }
    assert len(query) == len(passage) == 384
    assert math.sqrt(sum(value * value for value in query)) == pytest.approx(1.0)
    assert provider.descriptor.model_version == "multilingual-e5-small@approved-revision"


def test_local_e5_sanitizes_failures_and_rejects_invalid_vector() -> None:
    unavailable = LocalE5EmbeddingProvider(
        Path("/approved/model"),
        revision="approved-revision",
        encoder_factory=lambda path: FakeEncoder(failure=True),
    )
    with pytest.raises(KnowledgeContractError) as failure:
        unavailable.embed_query("질문")
    assert failure.value.code == "EMBEDDING_MODEL_UNAVAILABLE"
    assert "sensitive" not in failure.value.safe_message

    invalid = LocalE5EmbeddingProvider(
        Path("/approved/model"),
        revision="approved-revision",
        encoder_factory=lambda path: FakeEncoder([1.0] * 383),
    )
    with pytest.raises(KnowledgeContractError) as vector:
        invalid.embed_passage("근거")
    assert vector.value.code == "EMBEDDING_VECTOR_INVALID"


def test_embedding_config_defaults_to_hash() -> None:
    config = EmbeddingConfig.from_environment({})

    provider = create_embedding_provider(config)

    assert isinstance(provider, LocalHashEmbeddingProvider)
    assert provider.descriptor.model_version == "local-hash-ngram-ko-v1"


def test_embedding_config_loads_only_hash_verified_local_e5(
    tmp_path: Path,
) -> None:
    model_root, model_hash = _model_package(tmp_path)
    config = EmbeddingConfig.from_environment(
        _environment(model_root, model_hash)
    )
    calls: list[tuple[Path, str]] = []

    def factory(path: Path, revision: str) -> StubProvider:
        calls.append((path, revision))
        return StubProvider()

    provider = create_embedding_provider(config, e5_factory=factory)

    assert isinstance(provider, StubProvider)
    assert calls == [(model_root / "multilingual-e5-small", "approved-revision")]


def test_embedding_config_falls_back_only_when_model_runtime_is_unavailable(
    tmp_path: Path,
) -> None:
    model_root, model_hash = _model_package(tmp_path)
    config = EmbeddingConfig.from_environment(_environment(model_root, model_hash))

    def unavailable(path: Path, revision: str) -> StubProvider:
        del path, revision
        raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE")

    provider = create_embedding_provider(config, e5_factory=unavailable)

    assert isinstance(provider, LocalHashEmbeddingProvider)


def test_embedding_config_rejects_hash_mismatch_and_can_disable_fallback(
    tmp_path: Path,
) -> None:
    model_root, model_hash = _model_package(tmp_path)
    invalid = EmbeddingConfig.from_environment(
        _environment(model_root, "sha256:" + "0" * 64)
    )
    with pytest.raises(KnowledgeContractError) as mismatch:
        create_embedding_provider(invalid, e5_factory=lambda path, revision: StubProvider())
    assert mismatch.value.code == "EMBEDDING_CONFIGURATION_INVALID"

    strict_environment = _environment(model_root, model_hash)
    strict_environment["ALZS_EMBEDDING_ALLOW_HASH_FALLBACK"] = "false"
    strict = EmbeddingConfig.from_environment(strict_environment)

    def unavailable(path: Path, revision: str) -> StubProvider:
        del path, revision
        raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE")

    with pytest.raises(KnowledgeContractError) as failure:
        create_embedding_provider(strict, e5_factory=unavailable)
    assert failure.value.code == "EMBEDDING_MODEL_UNAVAILABLE"


@pytest.mark.parametrize(
    "environment",
    [
        {"ALZS_EMBEDDING_BACKEND": "remote-api"},
        {
            "ALZS_EMBEDDING_BACKEND": "local-e5",
            "ALZS_EMBEDDING_MODEL_ROOT": "relative/models",
        },
        {"ALZS_EMBEDDING_ALLOW_HASH_FALLBACK": "yes"},
    ],
)
def test_embedding_config_rejects_unsafe_or_unknown_values(
    environment: dict[str, str],
) -> None:
    with pytest.raises(KnowledgeContractError) as caught:
        EmbeddingConfig.from_environment(environment)
    assert caught.value.code == "EMBEDDING_CONFIGURATION_INVALID"


def _model_package(tmp_path: Path) -> tuple[Path, str]:
    root = tmp_path / "models"
    model = root / "multilingual-e5-small"
    model.mkdir(parents=True)
    payload = b"approved synthetic safetensors fixture"
    (model / "model.safetensors").write_bytes(payload)
    return root, "sha256:" + sha256(payload).hexdigest()


def _environment(model_root: Path, model_hash: str) -> dict[str, str]:
    return {
        "ALZS_EMBEDDING_BACKEND": "local-e5",
        "ALZS_EMBEDDING_MODEL_ROOT": str(model_root),
        "ALZS_EMBEDDING_MODEL_PATH": "multilingual-e5-small",
        "ALZS_EMBEDDING_MODEL_REVISION": "approved-revision",
        "ALZS_EMBEDDING_MODEL_SHA256": model_hash,
        "ALZS_EMBEDDING_ALLOW_HASH_FALLBACK": "true",
    }
