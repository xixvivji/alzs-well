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
        if arctic_rollout and backend != "local-arctic-ko":
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        if backend == "hash":
            return cls(
                backend=backend,
                allow_hash_fallback=fallback,
                arctic_rollout_enabled=arctic_rollout,
            )
        if backend == "local-arctic-ko" and not arctic_rollout:
            return cls(
                backend=backend,
                allow_hash_fallback=fallback,
                arctic_rollout_enabled=False,
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
        return cls(
            backend=backend,
            model_root=root,
            model_path=path_value,
            model_revision=revision,
            model_sha256=digest,
            allow_hash_fallback=fallback,
            arctic_rollout_enabled=arctic_rollout,
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


def _boolean(value: str) -> bool:
    normalized = value.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
