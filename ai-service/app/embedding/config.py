from __future__ import annotations

import os
import re
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path, PurePosixPath

from app.embedding.base import EmbeddingProvider
from app.embedding.local_arctic import (
    ARCTIC_MODEL_REVISION,
    ARCTIC_MODEL_SHA256,
    LocalArcticKoEmbeddingProvider,
)
from app.embedding.local_e5 import LocalE5EmbeddingProvider
from app.embedding.local_hash import LocalHashEmbeddingProvider
from app.errors import KnowledgeContractError
from app.evaluation.model_catalog import EvaluationModelArtifact, load_model_catalog


HASH_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
REVISION_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._-]{0,79}$")
E5Factory = Callable[[Path, str], EmbeddingProvider]
ArcticFactory = Callable[[Path, str], EmbeddingProvider]


@dataclass(frozen=True, slots=True)
class EmbeddingConfig:
    backend: str = "hash"
    model_root: Path | None = None
    model_path: str | None = None
    model_revision: str | None = None
    model_sha256: str | None = None
    allow_hash_fallback: bool = True
    arctic_rollout_enabled: bool = False
    deployment_environment: str = "LOCAL"
    staged_approval_enabled: bool = False
    model_catalog_path: Path | None = None
    golden_set_path: Path | None = None

    @classmethod
    def from_environment(
        cls, environment: Mapping[str, str] | None = None
    ) -> EmbeddingConfig:
        values = os.environ if environment is None else environment
        backend = values.get("ALZS_EMBEDDING_BACKEND", "hash").strip().lower()
        if backend not in {"hash", "local-e5", "local-arctic-ko"}:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        fallback = _boolean(values.get("ALZS_EMBEDDING_ALLOW_HASH_FALLBACK", "true"))
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
        if backend == "hash":
            return cls(
                backend=backend,
                allow_hash_fallback=fallback,
                arctic_rollout_enabled=arctic_rollout,
                deployment_environment=deployment_environment,
                staged_approval_enabled=staged_approval,
            )
        if backend == "local-arctic-ko" and not arctic_rollout:
            return cls(
                backend=backend,
                allow_hash_fallback=fallback,
                arctic_rollout_enabled=False,
                deployment_environment=deployment_environment,
                staged_approval_enabled=False,
            )
        root_value = values.get("ALZS_EMBEDDING_MODEL_ROOT", "").strip()
        path_value = values.get("ALZS_EMBEDDING_MODEL_PATH", "").strip()
        revision = values.get("ALZS_EMBEDDING_MODEL_REVISION", "").strip()
        digest = values.get("ALZS_EMBEDDING_MODEL_SHA256", "").strip()
        catalog_value = values.get("ALZS_MODEL_CATALOG_PATH", "").strip()
        golden_set_value = values.get("ALZS_MODEL_GOLDEN_SET_PATH", "").strip()
        root = Path(root_value)
        catalog_path = Path(catalog_value)
        golden_set_path = Path(golden_set_value)
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
        if backend == "local-arctic-ko":
            if (
                deployment_environment != "AWS_STAGING"
                or not staged_approval
                or not catalog_path.is_absolute()
                or not golden_set_path.is_absolute()
            ):
                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
            selected = _staged_arctic_model(catalog_path)
            if (
                selected.revision != revision
                or selected.artifact_sha256 != digest
                or selected.model_id != "dragonkue/snowflake-arctic-embed-l-v2.0-ko"
                or selected.dimensions != 1024
            ):
                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
            _verify_golden_set(catalog_path, golden_set_path, selected)
        return cls(
            backend=backend,
            model_root=root,
            model_path=path_value,
            model_revision=revision,
            model_sha256=digest,
            allow_hash_fallback=fallback,
            arctic_rollout_enabled=arctic_rollout,
            deployment_environment=deployment_environment,
            staged_approval_enabled=staged_approval,
            model_catalog_path=catalog_path if catalog_value else None,
            golden_set_path=golden_set_path if golden_set_value else None,
        )

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
    model_directory = config.resolve_model_directory()
    _verify_safetensors(model_directory, config.model_sha256)
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


def _verify_safetensors(model_directory: Path, expected_hash: str | None) -> None:
    artifact = model_directory / "model.safetensors"
    if expected_hash is None or not artifact.is_file() or artifact.is_symlink():
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    digest = sha256()
    try:
        with artifact.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
    except OSError:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    if "sha256:" + digest.hexdigest() != expected_hash:
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
            if model.name == "snowflake-arctic-embed-l-v2.0-ko"
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
