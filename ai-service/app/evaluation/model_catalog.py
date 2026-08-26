from __future__ import annotations

import json
import re
from collections.abc import Callable
from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path, PurePosixPath
from typing import Any

from app.embedding.base import EmbeddingProvider
from app.embedding.local_sentence_transformer import (
    LocalSentenceTransformerEmbeddingProvider,
    LocalSentenceTransformerSpec,
)
from app.errors import KnowledgeContractError


CATALOG_KEYS = {"catalogVersion", "automaticDownloadAllowed", "models"}
MODEL_KEYS = {
    "name",
    "status",
    "modelId",
    "sourceUrl",
    "revision",
    "license",
    "localPath",
    "dimensions",
    "queryPrefix",
    "passagePrefix",
    "artifact",
}
ARTIFACT_KEYS = {"file", "sizeBytes", "sha256"}
NAME_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._-]{0,127}$")
MODEL_ID_PATTERN = re.compile(
    r"^[0-9A-Za-z][0-9A-Za-z._-]{0,63}/[0-9A-Za-z][0-9A-Za-z._-]{0,127}$"
)
REVISION_PATTERN = re.compile(r"^[0-9a-f]{40}$")
HASH_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
ProviderFactory = Callable[[Path, LocalSentenceTransformerSpec], EmbeddingProvider]


@dataclass(frozen=True, slots=True)
class EvaluationModelArtifact:
    name: str
    model_id: str
    source_url: str
    revision: str
    license: str
    local_path: str
    dimensions: int
    query_prefix: str
    passage_prefix: str
    artifact_file: str
    artifact_size_bytes: int
    artifact_sha256: str

    def provider_spec(self) -> LocalSentenceTransformerSpec:
        return LocalSentenceTransformerSpec(
            backend="local-sentence-transformer",
            model_id=self.model_id,
            model_version_name=self.name,
            revision=self.revision,
            dimensions=self.dimensions,
            query_prefix=self.query_prefix,
            passage_prefix=self.passage_prefix,
        )


def load_model_catalog(path: Path) -> tuple[EvaluationModelArtifact, ...]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    if not isinstance(payload, dict) or set(payload) != CATALOG_KEYS:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if (
        payload["catalogVersion"] != "1.0.0"
        or payload["automaticDownloadAllowed"] is not False
        or not isinstance(payload["models"], list)
        or not payload["models"]
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    models = tuple(_model(value) for value in payload["models"])
    if len({model.name for model in models}) != len(models):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    return models


def create_evaluation_embedding_provider(
    catalog_path: Path,
    model_name: str,
    model_root: Path,
    *,
    provider_factory: ProviderFactory | None = None,
) -> EmbeddingProvider:
    models = load_model_catalog(catalog_path)
    selected = next((model for model in models if model.name == model_name), None)
    if selected is None:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    model_directory = _resolve_model_directory(model_root, selected.local_path)
    _verify_artifact(model_directory, selected)
    factory = _provider if provider_factory is None else provider_factory
    try:
        return factory(model_directory, selected.provider_spec())
    except KnowledgeContractError:
        raise
    except Exception:
        raise KnowledgeContractError("EMBEDDING_MODEL_UNAVAILABLE") from None


def _model(payload: Any) -> EvaluationModelArtifact:
    if not isinstance(payload, dict) or set(payload) != MODEL_KEYS:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    artifact = payload["artifact"]
    if not isinstance(artifact, dict) or set(artifact) != ARTIFACT_KEYS:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    name = payload["name"]
    model_id = payload["modelId"]
    source_url = payload["sourceUrl"]
    revision = payload["revision"]
    license_name = payload["license"]
    local_path = payload["localPath"]
    dimensions = payload["dimensions"]
    query_prefix = payload["queryPrefix"]
    passage_prefix = payload["passagePrefix"]
    artifact_file = artifact["file"]
    artifact_size = artifact["sizeBytes"]
    artifact_hash = artifact["sha256"]
    relative = PurePosixPath(local_path) if isinstance(local_path, str) else None
    if (
        payload["status"] != "EVALUATION_ONLY"
        or not isinstance(name, str)
        or not NAME_PATTERN.fullmatch(name)
        or not isinstance(model_id, str)
        or not MODEL_ID_PATTERN.fullmatch(model_id)
        or not isinstance(source_url, str)
        or not source_url.startswith("https://huggingface.co/")
        or not isinstance(revision, str)
        or not REVISION_PATTERN.fullmatch(revision)
        or not isinstance(license_name, str)
        or not 1 <= len(license_name) <= 64
        or not license_name.isprintable()
        or relative is None
        or relative.is_absolute()
        or not relative.parts
        or ".." in relative.parts
        or not isinstance(dimensions, int)
        or isinstance(dimensions, bool)
        or not 1 <= dimensions <= 4096
        or not _valid_prefix(query_prefix)
        or not _valid_prefix(passage_prefix)
        or artifact_file != "model.safetensors"
        or not isinstance(artifact_size, int)
        or isinstance(artifact_size, bool)
        or artifact_size <= 0
        or not isinstance(artifact_hash, str)
        or not HASH_PATTERN.fullmatch(artifact_hash)
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    return EvaluationModelArtifact(
        name=name,
        model_id=model_id,
        source_url=source_url,
        revision=revision,
        license=license_name,
        local_path=local_path,
        dimensions=dimensions,
        query_prefix=query_prefix,
        passage_prefix=passage_prefix,
        artifact_file=artifact_file,
        artifact_size_bytes=artifact_size,
        artifact_sha256=artifact_hash,
    )


def _valid_prefix(value: Any) -> bool:
    return (
        isinstance(value, str)
        and len(value) <= 32
        and (not value or value.isprintable())
    )


def _resolve_model_directory(model_root: Path, local_path: str) -> Path:
    if not model_root.is_absolute():
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    try:
        current = model_root
        if current.is_symlink():
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        for part in PurePosixPath(local_path).parts:
            current = current / part
            if current.is_symlink():
                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        root = model_root.resolve(strict=True)
        resolved = current.resolve(strict=True)
        resolved.relative_to(root)
    except (FileNotFoundError, NotADirectoryError, ValueError):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    try:
        if not resolved.is_dir() or any(
            candidate.is_symlink() for candidate in resolved.rglob("*")
        ):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    except OSError:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    return resolved


def _verify_artifact(
    model_directory: Path, selected: EvaluationModelArtifact
) -> None:
    artifact = model_directory / selected.artifact_file
    try:
        if (
            not artifact.is_file()
            or artifact.is_symlink()
            or artifact.stat().st_size != selected.artifact_size_bytes
        ):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        digest = sha256()
        with artifact.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
    except OSError:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    if "sha256:" + digest.hexdigest() != selected.artifact_sha256:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")


def _provider(
    model_path: Path, spec: LocalSentenceTransformerSpec
) -> EmbeddingProvider:
    return LocalSentenceTransformerEmbeddingProvider(model_path, spec=spec)
