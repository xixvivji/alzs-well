from __future__ import annotations

import os
from pathlib import Path, PurePosixPath
from typing import Mapping

from app.errors import KnowledgeContractError


CONTRACT_SCHEMA_PATH = Path("contracts/knowledge/manifest.schema.json")


def resolve_repository_root(
    explicit_root: str | os.PathLike[str] | None,
    environ: Mapping[str, str] | None = None,
) -> Path:
    environment = os.environ if environ is None else environ
    configured = explicit_root or environment.get("ALZS_REPO_ROOT")
    if configured is None or not str(configured).strip():
        raise KnowledgeContractError("REPOSITORY_ROOT_REQUIRED")

    candidate = Path(configured).expanduser()
    if candidate.is_symlink():
        raise KnowledgeContractError("SOURCE_SYMLINK_FORBIDDEN")
    try:
        root = candidate.resolve(strict=True)
    except (FileNotFoundError, OSError):
        raise KnowledgeContractError("REPOSITORY_ROOT_REQUIRED") from None
    if not root.is_dir() or not (root / CONTRACT_SCHEMA_PATH).is_file():
        raise KnowledgeContractError("REPOSITORY_ROOT_REQUIRED")
    return root


def resolve_source_file(repository_root: Path, source_path: str) -> Path:
    if "\\" in source_path:
        raise KnowledgeContractError("SOURCE_PATH_OUTSIDE_CORPUS")
    pure_path = PurePosixPath(source_path)
    if pure_path.is_absolute() or any(part in {"", ".", ".."} for part in pure_path.parts):
        raise KnowledgeContractError("SOURCE_PATH_OUTSIDE_CORPUS")

    current = repository_root
    for part in pure_path.parts:
        current = current / part
        if current.is_symlink():
            raise KnowledgeContractError("SOURCE_SYMLINK_FORBIDDEN")
        if not current.exists():
            raise KnowledgeContractError("SOURCE_NOT_FOUND")

    try:
        resolved = current.resolve(strict=True)
        resolved.relative_to(repository_root)
    except ValueError:
        raise KnowledgeContractError("SOURCE_PATH_OUTSIDE_CORPUS") from None
    except (FileNotFoundError, OSError):
        raise KnowledgeContractError("SOURCE_NOT_FOUND") from None
    if not resolved.is_file():
        raise KnowledgeContractError("SOURCE_NOT_FOUND")
    return resolved


def resolve_manifest_file(repository_root: Path, manifest_path: str | os.PathLike[str]) -> Path:
    candidate = Path(manifest_path)
    if not candidate.is_absolute():
        candidate = repository_root / candidate
    if candidate.is_symlink():
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID")
    try:
        resolved = candidate.resolve(strict=True)
        resolved.relative_to(repository_root)
    except (FileNotFoundError, OSError, ValueError):
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID") from None
    if not resolved.is_file():
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID")
    return resolved
