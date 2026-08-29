from __future__ import annotations

import os
import re
import stat
from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path, PurePosixPath

from app.errors import KnowledgeContractError


HASH_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
MAX_PACKAGE_FILES = 64
MAX_PACKAGE_ENTRIES = 128
MAX_PACKAGE_DEPTH = 8
MAX_RELATIVE_PATH_BYTES = 512


@dataclass(frozen=True, slots=True)
class ModelPackageFile:
    path: str
    size_bytes: int
    sha256: str


def validate_package_file(
    path: object, size_bytes: object, digest: object
) -> ModelPackageFile:
    relative = PurePosixPath(path) if isinstance(path, str) else None
    if (
        relative is None
        or relative.is_absolute()
        or not relative.parts
        or any(part in {"", ".", ".."} for part in relative.parts)
        or not isinstance(size_bytes, int)
        or isinstance(size_bytes, bool)
        or size_bytes <= 0
        or not isinstance(digest, str)
        or not HASH_PATTERN.fullmatch(digest)
    ):
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    return ModelPackageFile(
        path=relative.as_posix(), size_bytes=size_bytes, sha256=digest
    )


def verify_model_package(
    model_directory: Path, files: tuple[ModelPackageFile, ...]
) -> None:
    if not files or len(files) > MAX_PACKAGE_FILES:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
    expected = {entry.path: entry for entry in files}
    if len(expected) != len(files) or "model.safetensors" not in expected:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")

    expected_directories = {
        parent.as_posix()
        for entry in files
        for parent in PurePosixPath(entry.path).parents
        if parent != PurePosixPath(".")
    }
    actual_files, actual_directories = _scan_and_verify_package(
        model_directory, expected, expected_directories
    )
    if actual_files != set(expected) or actual_directories != expected_directories:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")


def _scan_and_verify_package(
    model_directory: Path,
    expected: dict[str, ModelPackageFile],
    expected_directories: set[str],
) -> tuple[set[str], set[str]]:
    discovered: set[str] = set()
    directories: set[str] = set()
    entry_count = 0
    file_flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    directory_flags = file_flags | getattr(os, "O_DIRECTORY", 0)
    pending: list[tuple[int, PurePosixPath, int]] = []
    try:
        root_descriptor = os.open(model_directory, directory_flags)
        pending.append((root_descriptor, PurePosixPath(), 0))
        while pending:
            directory_descriptor, relative_parent, depth = pending.pop()
            try:
                with os.scandir(directory_descriptor) as entries:
                    for entry in entries:
                        entry_count += 1
                        if entry_count > MAX_PACKAGE_ENTRIES or entry.is_symlink():
                            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
                        relative = relative_parent / entry.name
                        relative_path = relative.as_posix()
                        if len(relative_path.encode("utf-8")) > MAX_RELATIVE_PATH_BYTES:
                            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
                        if entry.is_dir(follow_symlinks=False):
                            if depth + 1 > MAX_PACKAGE_DEPTH or relative_path not in expected_directories:
                                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
                            child_descriptor = os.open(
                                entry.name, directory_flags, dir_fd=directory_descriptor
                            )
                            if not stat.S_ISDIR(os.fstat(child_descriptor).st_mode):
                                os.close(child_descriptor)
                                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
                            directories.add(relative_path)
                            pending.append((child_descriptor, relative, depth + 1))
                        elif entry.is_file(follow_symlinks=False):
                            expected_file = expected.get(relative_path)
                            if expected_file is None or len(discovered) >= MAX_PACKAGE_FILES:
                                raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
                            _verify_file_at(directory_descriptor, entry.name, expected_file, file_flags)
                            discovered.add(relative_path)
                        else:
                            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
            finally:
                os.close(directory_descriptor)
    except KnowledgeContractError:
        raise
    except OSError:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID") from None
    finally:
        for descriptor, _, _ in pending:
            try:
                os.close(descriptor)
            except OSError:
                pass
    return discovered, directories


def _verify_file_at(
    directory_descriptor: int,
    name: str,
    expected: ModelPackageFile,
    flags: int,
) -> None:
    descriptor = os.open(name, flags, dir_fd=directory_descriptor)
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_size != expected.size_bytes or metadata.st_nlink != 1:
            raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
        digest = sha256()
        while block := os.read(descriptor, 1024 * 1024):
            digest.update(block)
    finally:
        os.close(descriptor)
    if "sha256:" + digest.hexdigest() != expected.sha256:
        raise KnowledgeContractError("EMBEDDING_CONFIGURATION_INVALID")
