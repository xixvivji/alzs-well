from __future__ import annotations

from pathlib import Path

import pytest

from app.errors import KnowledgeContractError
from app.ingestion.repository import resolve_manifest_file, resolve_repository_root, resolve_source_file


def test_explicit_repository_root_has_priority(repo_root: Path) -> None:
    resolved = resolve_repository_root(repo_root, {"ALZS_REPO_ROOT": "/does/not/exist"})
    assert resolved == repo_root


def test_uses_repository_root_environment(repo_root: Path) -> None:
    resolved = resolve_repository_root(None, {"ALZS_REPO_ROOT": str(repo_root)})
    assert resolved == repo_root


def test_never_infers_repository_root_from_cwd(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    monkeypatch.chdir(tmp_path)
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_repository_root(None, {})
    assert raised.value.code == "REPOSITORY_ROOT_REQUIRED"


def test_rejects_directory_that_is_not_repository_root(tmp_path: Path) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_repository_root(tmp_path, {})
    assert raised.value.code == "REPOSITORY_ROOT_REQUIRED"


def test_rejects_missing_repository_root(tmp_path: Path) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_repository_root(tmp_path / "missing", {})
    assert raised.value.code == "REPOSITORY_ROOT_REQUIRED"


def test_rejects_symlink_repository_root(repo_root: Path, tmp_path: Path) -> None:
    linked_root = tmp_path / "linked-repository"
    linked_root.symlink_to(repo_root, target_is_directory=True)
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_repository_root(linked_root, {})
    assert raised.value.code == "SOURCE_SYMLINK_FORBIDDEN"


@pytest.mark.parametrize("source_path", ["/tmp/source.html", "../source.html", "dir/../source.html", "dir\\source.html"])
def test_rejects_unsafe_source_paths(repo_root: Path, source_path: str) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_source_file(repo_root, source_path)
    assert raised.value.code == "SOURCE_PATH_OUTSIDE_CORPUS"


def test_rejects_symlink_source_component(tmp_path: Path) -> None:
    corpus = tmp_path / "knowledge" / "official-source"
    corpus.mkdir(parents=True)
    target = tmp_path / "target.html"
    target.write_text("<!doctype html><html></html>", encoding="utf-8")
    (corpus / "linked.html").symlink_to(target)

    with pytest.raises(KnowledgeContractError) as raised:
        resolve_source_file(tmp_path, "knowledge/official-source/linked.html")
    assert raised.value.code == "SOURCE_SYMLINK_FORBIDDEN"


def test_rejects_missing_source(repo_root: Path) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_source_file(repo_root, "knowledge/official-source/missing.html")
    assert raised.value.code == "SOURCE_NOT_FOUND"


def test_rejects_directory_as_source(tmp_path: Path) -> None:
    directory = tmp_path / "contracts" / "knowledge" / "fixtures" / "directory.html"
    directory.mkdir(parents=True)
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_source_file(tmp_path, "contracts/knowledge/fixtures/directory.html")
    assert raised.value.code == "SOURCE_NOT_FOUND"


def test_resolves_absolute_manifest_inside_repository(repo_root: Path) -> None:
    expected = repo_root / "contracts/knowledge/fixtures/synthetic-approved-active.yaml"
    assert resolve_manifest_file(repo_root, expected) == expected


def test_rejects_manifest_outside_repository(repo_root: Path, tmp_path: Path) -> None:
    outside = tmp_path / "outside.yaml"
    outside.write_text("value: true", encoding="utf-8")
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_manifest_file(repo_root, outside)
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"


def test_rejects_symlink_manifest(repo_root: Path, tmp_path: Path) -> None:
    linked = tmp_path / "manifest.yaml"
    linked.symlink_to(repo_root / "contracts/knowledge/fixtures/synthetic-approved-active.yaml")
    with pytest.raises(KnowledgeContractError) as raised:
        resolve_manifest_file(repo_root, linked)
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"
