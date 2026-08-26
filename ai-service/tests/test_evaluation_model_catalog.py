from __future__ import annotations

import json
from copy import deepcopy
from hashlib import sha256
from pathlib import Path

import pytest

from app.embedding.base import EmbeddingDescriptor
from app.embedding.local_sentence_transformer import LocalSentenceTransformerSpec
from app.errors import KnowledgeContractError
from app.evaluation.model_catalog import (
    create_evaluation_embedding_provider,
    load_model_catalog,
)


EVALUATION = Path(__file__).parents[1] / "evaluation"


class StubProvider:
    descriptor = EmbeddingDescriptor(
        backend="local-sentence-transformer",
        model_id="owner/model",
        model_version="model@" + "a" * 40,
        dimensions=1024,
    )

    def embed_query(self, value: str) -> tuple[float, ...]:
        del value
        return (1.0,) + (0.0,) * 1023

    def embed_passage(self, value: str) -> tuple[float, ...]:
        del value
        return (1.0,) + (0.0,) * 1023


def _payload(content: bytes) -> dict[str, object]:
    return {
        "catalogVersion": "1.0.0",
        "automaticDownloadAllowed": False,
        "models": [
            {
                "name": "arctic-ko",
                "status": "EVALUATION_ONLY",
                "modelId": "owner/model",
                "sourceUrl": "https://huggingface.co/owner/model",
                "revision": "a" * 40,
                "license": "Apache-2.0",
                "localPath": "arctic-ko",
                "dimensions": 1024,
                "queryPrefix": "query: ",
                "passagePrefix": "",
                "artifact": {
                    "file": "model.safetensors",
                    "sizeBytes": len(content),
                    "sha256": "sha256:" + sha256(content).hexdigest(),
                },
            }
        ],
    }


def _package(tmp_path: Path) -> tuple[Path, Path, bytes]:
    content = b"synthetic model artifact"
    root = tmp_path / "models"
    model = root / "arctic-ko"
    model.mkdir(parents=True)
    (model / "model.safetensors").write_bytes(content)
    catalog = tmp_path / "catalog.json"
    catalog.write_text(json.dumps(_payload(content)), encoding="utf-8")
    return root, catalog, content


def test_catalog_creates_hash_verified_evaluation_provider(tmp_path: Path) -> None:
    root, catalog, _ = _package(tmp_path)
    calls: list[tuple[Path, LocalSentenceTransformerSpec]] = []

    def factory(path: Path, spec: LocalSentenceTransformerSpec) -> StubProvider:
        calls.append((path, spec))
        return StubProvider()

    provider = create_evaluation_embedding_provider(
        catalog, "arctic-ko", root, provider_factory=factory
    )
    models = load_model_catalog(catalog)

    assert isinstance(provider, StubProvider)
    assert calls[0][0] == root / "arctic-ko"
    assert calls[0][1] == models[0].provider_spec()
    assert calls[0][1].query_prefix == "query: "
    assert calls[0][1].passage_prefix == ""
    assert calls[0][1].dimensions == 1024


def test_committed_catalog_keeps_models_evaluation_only() -> None:
    models = load_model_catalog(EVALUATION / "model-artifacts-v1.json")

    assert [model.name for model in models] == [
        "multilingual-e5-small",
        "snowflake-arctic-embed-l-v2.0-ko",
    ]
    assert [model.dimensions for model in models] == [384, 1024]
    assert [model.passage_prefix for model in models] == ["passage: ", ""]


@pytest.mark.parametrize(
    "mutate",
    [
        lambda payload: payload.update(automaticDownloadAllowed=True),
        lambda payload: payload.update(catalogVersion="2.0.0"),
        lambda payload: payload.update(extra=True),
        lambda payload: payload["models"][0].update(status="APPROVED"),  # type: ignore[index,union-attr]
        lambda payload: payload["models"][0].update(revision="main"),  # type: ignore[index,union-attr]
        lambda payload: payload["models"][0].update(localPath="../escape"),  # type: ignore[index,union-attr]
        lambda payload: payload["models"][0].update(dimensions=True),  # type: ignore[index,union-attr]
        lambda payload: payload["models"][0].update(queryPrefix="bad\n"),  # type: ignore[index,union-attr]
        lambda payload: payload["models"][0]["artifact"].update(file="model.bin"),  # type: ignore[index,union-attr]
    ],
)
def test_catalog_rejects_unsafe_or_unknown_contract_fields(
    tmp_path: Path, mutate: object
) -> None:
    content = b"fixture"
    payload = deepcopy(_payload(content))
    mutate(payload)  # type: ignore[operator]
    catalog = tmp_path / "catalog.json"
    catalog.write_text(json.dumps(payload), encoding="utf-8")

    with pytest.raises(KnowledgeContractError) as failure:
        load_model_catalog(catalog)
    assert failure.value.code == "EMBEDDING_CONFIGURATION_INVALID"


def test_catalog_rejects_invalid_json_duplicates_and_unknown_model(
    tmp_path: Path,
) -> None:
    invalid = tmp_path / "invalid.json"
    invalid.write_text("{", encoding="utf-8")
    with pytest.raises(KnowledgeContractError):
        load_model_catalog(invalid)

    root, catalog, content = _package(tmp_path)
    payload = _payload(content)
    payload["models"].append(deepcopy(payload["models"][0]))  # type: ignore[union-attr,index]
    catalog.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(KnowledgeContractError):
        load_model_catalog(catalog)

    catalog.write_text(json.dumps(_payload(content)), encoding="utf-8")
    with pytest.raises(KnowledgeContractError) as unknown:
        create_evaluation_embedding_provider(catalog, "missing", root)
    assert unknown.value.code == "EMBEDDING_CONFIGURATION_INVALID"


def test_catalog_rejects_relative_root_symlinks_and_artifact_mismatch(
    tmp_path: Path,
) -> None:
    root, catalog, content = _package(tmp_path)
    with pytest.raises(KnowledgeContractError):
        create_evaluation_embedding_provider(catalog, "arctic-ko", Path("models"))

    symlink_root = tmp_path / "linked-models"
    symlink_root.symlink_to(root, target_is_directory=True)
    with pytest.raises(KnowledgeContractError):
        create_evaluation_embedding_provider(catalog, "arctic-ko", symlink_root)

    linked_tokenizer = root / "arctic-ko" / "tokenizer.json"
    linked_tokenizer.symlink_to(tmp_path / "outside-tokenizer.json")
    with pytest.raises(KnowledgeContractError):
        create_evaluation_embedding_provider(catalog, "arctic-ko", root)
    linked_tokenizer.unlink()

    payload = _payload(content)
    payload["models"][0]["artifact"]["sizeBytes"] += 1  # type: ignore[index,union-attr,operator]
    catalog.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(KnowledgeContractError):
        create_evaluation_embedding_provider(catalog, "arctic-ko", root)


def test_catalog_sanitizes_provider_factory_failure(tmp_path: Path) -> None:
    root, catalog, _ = _package(tmp_path)

    def broken(path: Path, spec: LocalSentenceTransformerSpec) -> StubProvider:
        del path, spec
        raise RuntimeError("private runtime detail")

    with pytest.raises(KnowledgeContractError) as failure:
        create_evaluation_embedding_provider(
            catalog, "arctic-ko", root, provider_factory=broken
        )
    assert failure.value.code == "EMBEDDING_MODEL_UNAVAILABLE"
    assert "private" not in failure.value.safe_message
