from __future__ import annotations

import os
import re
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

from app.embedding.base import EmbeddingProvider
from app.embedding.local_arctic import (
    ARCTIC_DIMENSIONS,
    ARCTIC_MODEL_ID,
    ARCTIC_MODEL_REVISION,
    ARCTIC_MODEL_SHA256,
    ARCTIC_MODEL_VERSION_NAME,
    LocalArcticKoEmbeddingProvider,
)
from app.embedding.local_e5 import (
    E5_DIMENSIONS,
    E5_MODEL_ID,
    LocalE5EmbeddingProvider,
)
from app.embedding.local_hash import LocalHashEmbeddingProvider
from app.embedding.model_package import ModelPackageFile, verify_model_package
from app.errors import KnowledgeContractError
from app.evaluation.model_catalog import load_model_catalog


HASH_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
REVISION_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._-]{0,79}$")
E5Factory = Callable[[Path, str], EmbeddingProvider]
ArcticFactory = Callable[[Path, str], EmbeddingProvider]
DEFAULT_MODEL_CATALOG = (
    Path(__file__).resolve().parents[2] / "evaluation/model-artifacts-v1.json"
)


@dataclass(frozen=True, slots=True)
class EmbeddingConfig:
    backend: str = "hash"
    model_root: Path | None = None
    model_path: str | None = None
    model_revision: str | None = None
    model_sha256: str | None = None
    allow_hash_fallback: bool = False
    model_status: str | None = None
    model_files: tuple[ModelPackageFile, ...] = ()
    execution_context: str = "PRODUCTION"
    allow_evaluation_model: bool = False

    @classmethod
    def from_environment(
        cls,
        environment: Mapping[str, str] | None = None,
        *,
        catalog_path: Path | None = None,
    ) -> EmbeddingConfig:
        values = os.environ if environment is None else environment
        backend = values.get("ALZS_EMBEDDING_BACKEND", "hash").strip().lower()
        if backend not in {"hash", "local-e5", "local-arctic-ko"}:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        execution_context = values.get(
            "ALZS_EMBEDDING_EXECUTION_CONTEXT", "PRODUCTION"
        ).strip().upper()
        if execution_context not in {"PRODUCTION", "SYNTHETIC_TEST"}:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        fallback_default = "true" if execution_context == "SYNTHETIC_TEST" else "false"
        fallback = _boolean(
            values.get("ALZS_EMBEDDING_ALLOW_HASH_FALLBACK", fallback_default)
        )
        allow_evaluation_model = _boolean(
            values.get("ALZS_EMBEDDING_ALLOW_EVALUATION_MODEL", "false")
        )
        if backend != "hash" and execution_context == "PRODUCTION" and fallback:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        if backend == "hash":
            return cls(
                backend=backend,
                allow_hash_fallback=fallback,
                execution_context=execution_context,
                allow_evaluation_model=allow_evaluation_model,
            )
        root_value = values.get("ALZS_EMBEDDING_MODEL_ROOT", "").strip()
        path_value = values.get("ALZS_EMBEDDING_MODEL_PATH", "").strip()
        revision = values.get("ALZS_EMBEDDING_MODEL_REVISION", "").strip()
        digest = values.get("ALZS_EMBEDDING_MODEL_SHA256", "").strip()
        root = Path(root_value)
        relative = PurePosixPath(path_value)
        if (
            not root.is_absolute()
            or not path_value
            or relative.is_absolute()
            or ".." in relative.parts
            or not REVISION_PATTERN.fullmatch(revision)
            or not HASH_PATTERN.fullmatch(digest)
        ):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        if backend == "local-arctic-ko" and (
            revision != ARCTIC_MODEL_REVISION or digest != ARCTIC_MODEL_SHA256
        ):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        models = load_model_catalog(catalog_path or DEFAULT_MODEL_CATALOG)
        if backend == "local-arctic-ko":
            expected = (
                ARCTIC_MODEL_VERSION_NAME,
                ARCTIC_MODEL_ID,
                ARCTIC_DIMENSIONS,
                "query: ",
                "",
            )
        else:
            expected = (
                "multilingual-e5-small",
                E5_MODEL_ID,
                E5_DIMENSIONS,
                "query: ",
                "passage: ",
            )
        (
            expected_name,
            expected_model_id,
            dimensions,
            query_prefix,
            passage_prefix,
        ) = expected
        selected = next(
            (
                model
                for model in models
                if model.name == expected_name
                and model.local_path == path_value
                and model.model_id == expected_model_id
                and model.revision == revision
                and model.dimensions == dimensions
                and model.query_prefix == query_prefix
                and model.passage_prefix == passage_prefix
            ),
            None,
        )
        if selected is None or selected.file("model.safetensors").sha256 != digest:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        config = cls(
            backend=backend,
            model_root=root,
            model_path=path_value,
            model_revision=revision,
            model_sha256=digest,
            allow_hash_fallback=fallback,
            model_status=selected.status,
            model_files=selected.files,
            execution_context=execution_context,
            allow_evaluation_model=allow_evaluation_model,
        )
        _validate_promotion(config)
        return config

    def resolve_model_directory(self) -> Path:
        if (
            self.backend not in {"local-e5", "local-arctic-ko"}
            or self.model_root is None
            or self.model_path is None
        ):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        try:
            current = self.model_root
            if current.is_symlink():
                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
            for part in PurePosixPath(self.model_path).parts:
                current = current / part
                if current.is_symlink():
                    raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
            root = self.model_root.resolve(strict=True)
            resolved = current.resolve(strict=True)
            resolved.relative_to(root)
        except (FileNotFoundError, NotADirectoryError, ValueError):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
        if not resolved.is_dir():
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        return resolved


def create_embedding_provider(
    config: EmbeddingConfig,
    *,
    e5_factory: E5Factory | None = None,
    arctic_factory: ArcticFactory | None = None,
) -> EmbeddingProvider:
    if config.backend == "hash":
        return LocalHashEmbeddingProvider()
    _validate_promotion(config)
    model_directory = config.resolve_model_directory()
    verify_model_package(model_directory, config.model_files)
    if config.backend == "local-arctic-ko":
        factory = _arctic_provider if arctic_factory is None else arctic_factory
    else:
        factory = _e5_provider if e5_factory is None else e5_factory
    try:
        return factory(model_directory, str(config.model_revision))
    except KnowledgeContractError as error:
        if error.code == "EMBEDDING_MODEL_UNAVAILABLE" and config.allow_hash_fallback:
            return LocalHashEmbeddingProvider()
        raise


def _e5_provider(model_path: Path, revision: str) -> EmbeddingProvider:
    return LocalE5EmbeddingProvider(model_path, revision=revision)


def _arctic_provider(model_path: Path, revision: str) -> EmbeddingProvider:
    return LocalArcticKoEmbeddingProvider(model_path, revision=revision)


def _validate_promotion(config: EmbeddingConfig) -> None:
    if (
        config.backend != "hash"
        and config.execution_context == "PRODUCTION"
        and config.allow_hash_fallback
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if config.model_status not in {"APPROVED", "EVALUATION_ONLY"}:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if config.model_status == "EVALUATION_ONLY" and not (
        config.execution_context == "SYNTHETIC_TEST"
        and config.allow_evaluation_model
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if config.execution_context == "PRODUCTION" and config.allow_evaluation_model:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")


def _boolean(value: str) -> bool:
    normalized = value.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
