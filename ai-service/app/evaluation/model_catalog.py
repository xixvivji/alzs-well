from __future__ import annotations

import json
import re
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

from app.embedding.base import EmbeddingProvider
from app.embedding.local_sentence_transformer import (
    LocalSentenceTransformerEmbeddingProvider,
    LocalSentenceTransformerSpec,
)
from app.embedding.model_package import (
    ModelPackageFile,
    validate_package_file,
    verify_model_package,
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
    "files",
}
FILE_KEYS = {"path", "sizeBytes", "sha256"}
NAME_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._-]{0,127}$")
MODEL_ID_PATTERN = re.compile(
    r"^[0-9A-Za-z][0-9A-Za-z._-]{0,63}/[0-9A-Za-z][0-9A-Za-z._-]{0,127}$"
)
REVISION_PATTERN = re.compile(r"^[0-9a-f]{40}$")
ProviderFactory = Callable[[Path, LocalSentenceTransformerSpec], EmbeddingProvider]


@dataclass(frozen=True, slots=True)
class EvaluationModelArtifact:
    name: str
    status: str
    model_id: str
    source_url: str
    revision: str
    license: str
    local_path: str
    dimensions: int
    query_prefix: str
    passage_prefix: str
    files: tuple[ModelPackageFile, ...]

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

    def file(self, path: str) -> ModelPackageFile:
        selected = next((entry for entry in self.files if entry.path == path), None)
        if selected is None:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        return selected


def load_model_catalog(path: Path) -> tuple[EvaluationModelArtifact, ...]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    if not isinstance(payload, dict) or set(payload) != CATALOG_KEYS:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if (
        payload["catalogVersion"] != "2.0.0"
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
    model_directory = resolve_model_directory(model_root, selected.local_path)
    verify_model_package(model_directory, selected.files)
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
    raw_files = payload["files"]
    if not isinstance(raw_files, list) or not raw_files:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    package_files = tuple(_package_file(value) for value in raw_files)
    name = payload["name"]
    status = payload["status"]
    model_id = payload["modelId"]
    source_url = payload["sourceUrl"]
    revision = payload["revision"]
    license_name = payload["license"]
    local_path = payload["localPath"]
    dimensions = payload["dimensions"]
    query_prefix = payload["queryPrefix"]
    passage_prefix = payload["passagePrefix"]
    relative = PurePosixPath(local_path) if isinstance(local_path, str) else None
    if (
        status not in {"EVALUATION_ONLY", "APPROVED"}
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
        or len({entry.path for entry in package_files}) != len(package_files)
        or "model.safetensors" not in {entry.path for entry in package_files}
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    return EvaluationModelArtifact(
        name=name,
        status=status,
        model_id=model_id,
        source_url=source_url,
        revision=revision,
        license=license_name,
        local_path=local_path,
        dimensions=dimensions,
        query_prefix=query_prefix,
        passage_prefix=passage_prefix,
        files=package_files,
    )


def _package_file(payload: Any) -> ModelPackageFile:
    if not isinstance(payload, dict) or set(payload) != FILE_KEYS:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    return validate_package_file(
        payload["path"], payload["sizeBytes"], payload["sha256"]
    )


def _valid_prefix(value: Any) -> bool:
    return (
        isinstance(value, str)
        and len(value) <= 32
        and (not value or value.isprintable())
    )


def resolve_model_directory(model_root: Path, local_path: str) -> Path:
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
    if not resolved.is_dir():
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    return resolved


def _provider(
    model_path: Path, spec: LocalSentenceTransformerSpec
) -> EmbeddingProvider:
    return LocalSentenceTransformerEmbeddingProvider(model_path, spec=spec)
