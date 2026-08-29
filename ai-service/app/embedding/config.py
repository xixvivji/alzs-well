from __future__ import annotations

import os
import re
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from hashlib import sha256
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
from app.evaluation.model_catalog import EvaluationModelArtifact, load_model_catalog


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
    arctic_rollout_enabled: bool = False
    deployment_environment: str = "LOCAL"
    staged_approval_enabled: bool = False
    staged_approval_verified: bool = False
    model_catalog_path: Path | None = None
    golden_set_path: Path | None = None

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
        if execution_context not in {"PRODUCTION", "STAGING", "SYNTHETIC_TEST"}:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        fallback_default = "true" if execution_context == "SYNTHETIC_TEST" else "false"
        fallback = _boolean(
            values.get("ALZS_EMBEDDING_ALLOW_HASH_FALLBACK", fallback_default)
        )
        allow_evaluation_model = _boolean(
            values.get("ALZS_EMBEDDING_ALLOW_EVALUATION_MODEL", "false")
        )
        arctic_rollout = _boolean(
            values.get("ALZS_ARCTIC_ROLLOUT_ENABLED", "false")
        )
        staged_approval = _boolean(
            values.get("ALZS_MODEL_STAGED_APPROVAL_ENABLED", "false")
        )
        deployment_environment = values.get(
            "ALZS_DEPLOYMENT_ENVIRONMENT", "LOCAL"
        ).strip().upper()
        if arctic_rollout and backend != "local-arctic-ko":
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        if staged_approval and (backend != "local-arctic-ko" or not arctic_rollout):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        if backend != "hash" and execution_context != "SYNTHETIC_TEST" and fallback:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        if execution_context != "SYNTHETIC_TEST" and allow_evaluation_model:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        if backend == "hash":
            return cls(
                backend=backend,
                allow_hash_fallback=fallback,
                execution_context=execution_context,
                allow_evaluation_model=allow_evaluation_model,
                arctic_rollout_enabled=arctic_rollout,
                deployment_environment=deployment_environment,
                staged_approval_enabled=staged_approval,
            )
        if backend == "local-arctic-ko" and not arctic_rollout:
            return cls(
                backend=backend,
                allow_hash_fallback=fallback,
                execution_context=execution_context,
                allow_evaluation_model=allow_evaluation_model,
                arctic_rollout_enabled=False,
                deployment_environment=deployment_environment,
            )

        root_value = values.get("ALZS_EMBEDDING_MODEL_ROOT", "").strip()
        path_value = values.get("ALZS_EMBEDDING_MODEL_PATH", "").strip()
        revision = values.get("ALZS_EMBEDDING_MODEL_REVISION", "").strip()
        digest = values.get("ALZS_EMBEDDING_MODEL_SHA256", "").strip()
        catalog_value = values.get("ALZS_MODEL_CATALOG_PATH", "").strip()
        golden_set_value = values.get("ALZS_MODEL_GOLDEN_SET_PATH", "").strip()
        root = Path(root_value)
        relative = PurePosixPath(path_value)
        selected_catalog_path = catalog_path or (
            Path(catalog_value) if catalog_value else DEFAULT_MODEL_CATALOG
        )
        golden_set_path = Path(golden_set_value) if golden_set_value else None
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

        models = load_model_catalog(selected_catalog_path)
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
        expected_name, expected_model_id, dimensions, query_prefix, passage_prefix = expected
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

        staged_verified = False
        if selected.status == "STAGED_APPROVED":
            if (
                backend != "local-arctic-ko"
                or execution_context != "STAGING"
                or deployment_environment != "AWS_STAGING"
                or not staged_approval
                or not selected_catalog_path.is_absolute()
                or golden_set_path is None
                or not golden_set_path.is_absolute()
            ):
                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
            staged_model = _staged_arctic_model(selected_catalog_path)
            if staged_model != selected:
                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
            _verify_golden_set(selected_catalog_path, golden_set_path, selected)
            staged_verified = True

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
            arctic_rollout_enabled=arctic_rollout,
            deployment_environment=deployment_environment,
            staged_approval_enabled=staged_approval,
            staged_approval_verified=staged_verified,
            model_catalog_path=selected_catalog_path,
            golden_set_path=golden_set_path,
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
    if config.backend == "local-arctic-ko" and not config.arctic_rollout_enabled:
        return LocalHashEmbeddingProvider()
    _validate_promotion(config)
    model_directory = config.resolve_model_directory()
    verify_model_package(model_directory, config.model_files)
    factory = (
        _arctic_provider if config.backend == "local-arctic-ko" else _e5_provider
    )
    if config.backend == "local-arctic-ko" and arctic_factory is not None:
        factory = arctic_factory
    if config.backend == "local-e5" and e5_factory is not None:
        factory = e5_factory
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
        and config.execution_context != "SYNTHETIC_TEST"
        and config.allow_hash_fallback
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if config.model_status not in {"APPROVED", "STAGED_APPROVED", "EVALUATION_ONLY"}:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if config.model_status == "EVALUATION_ONLY" and not (
        config.execution_context == "SYNTHETIC_TEST"
        and config.allow_evaluation_model
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if config.model_status == "STAGED_APPROVED" and not (
        config.backend == "local-arctic-ko"
        and config.execution_context == "STAGING"
        and config.deployment_environment == "AWS_STAGING"
        and config.staged_approval_enabled
        and config.staged_approval_verified
        and not config.allow_evaluation_model
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if config.execution_context != "SYNTHETIC_TEST" and config.allow_evaluation_model:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")


def _staged_arctic_model(catalog_path: Path) -> EvaluationModelArtifact:
    try:
        if not catalog_path.is_file() or catalog_path.is_symlink():
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    except OSError:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    selected = next(
        (
            model
            for model in load_model_catalog(catalog_path)
            if model.name == ARCTIC_MODEL_VERSION_NAME
        ),
        None,
    )
    if (
        selected is None
        or selected.status != "STAGED_APPROVED"
        or selected.approval is None
        or selected.approval.deployment_environment != "AWS_STAGING"
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    return selected


def _verify_golden_set(
    catalog_path: Path,
    golden_set_path: Path,
    selected: EvaluationModelArtifact,
) -> None:
    approval = selected.approval
    if approval is None:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    expected_path = catalog_path.parent.parent / approval.golden_set.file
    try:
        if (
            golden_set_path.is_symlink()
            or not golden_set_path.is_file()
            or golden_set_path.resolve(strict=True) != expected_path.resolve(strict=True)
        ):
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        payload = golden_set_path.read_bytes()
    except OSError:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    if "sha256:" + sha256(payload).hexdigest() != approval.golden_set.sha256:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    if sum(bool(line.strip()) for line in payload.splitlines()) != approval.golden_set.case_count:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")


def _boolean(value: str) -> bool:
    normalized = value.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
