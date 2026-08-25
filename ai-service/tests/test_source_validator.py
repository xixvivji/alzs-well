from __future__ import annotations

import hashlib
from pathlib import Path

import pytest

from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.source_validator import MAX_HTML_BYTES, _read_without_following_symlink, validate_source


def _manifest(source_path: str, raw: bytes) -> KnowledgeManifest:
    return KnowledgeManifest(
        {
            "sourcePath": source_path,
            "sourceHash": "sha256:" + hashlib.sha256(raw).hexdigest(),
            "sourceTransformations": [],
            "approvalStatus": "APPROVED",
            "lifecycleStatus": "ACTIVE",
            "effectiveFrom": "2026-08-21",
            "effectiveTo": None,
        }
    )


def _write_source(root: Path, name: str, raw: bytes) -> str:
    relative = Path("contracts/knowledge/fixtures") / name
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return relative.as_posix()


def test_validates_synthetic_html(repo_root: Path) -> None:
    raw = (repo_root / "contracts/knowledge/fixtures/synthetic-source.html").read_bytes()
    source = validate_source(
        repo_root,
        _manifest("contracts/knowledge/fixtures/synthetic-source.html", raw),
    )
    assert source.source_hash == "sha256:232e71a1e03d58e8afd24e291ea341e67b7b6c302263f88a9ec06504dec3d653"
    assert source.encoding == "UTF-8"
    assert "합성" in source.text


def test_validates_utf8_bom(tmp_path: Path) -> None:
    raw = b"\xef\xbb\xbf<!doctype html><html><meta charset='UTF-8'><body>ok</body></html>"
    source_path = _write_source(tmp_path, "bom.html", raw)
    source = validate_source(tmp_path, _manifest(source_path, raw))
    assert source.text.startswith("<!doctype html>")


def test_rejects_hash_mismatch(tmp_path: Path) -> None:
    raw = b"<!doctype html><html></html>"
    source_path = _write_source(tmp_path, "hash.html", raw)
    manifest = _manifest(source_path, b"different")
    with pytest.raises(KnowledgeContractError) as raised:
        validate_source(tmp_path, manifest)
    assert raised.value.code == "SOURCE_HASH_MISMATCH"


def test_rejects_oversized_html_before_reading_all_content(tmp_path: Path) -> None:
    raw = b"<!doctype html>" + b"x" * MAX_HTML_BYTES
    source_path = _write_source(tmp_path, "large.html", raw)
    with pytest.raises(KnowledgeContractError) as raised:
        validate_source(tmp_path, _manifest(source_path, raw))
    assert raised.value.code == "SOURCE_TOO_LARGE"


def test_rejects_non_utf8_html(tmp_path: Path) -> None:
    raw = b"<!doctype html><html><body>\xff</body></html>"
    source_path = _write_source(tmp_path, "encoding.html", raw)
    with pytest.raises(KnowledgeContractError) as raised:
        validate_source(tmp_path, _manifest(source_path, raw))
    assert raised.value.code == "SOURCE_ENCODING_INVALID"


def test_rejects_non_utf8_charset_declaration(tmp_path: Path) -> None:
    raw = b"<!doctype html><html><meta charset='euc-kr'><body>ascii</body></html>"
    source_path = _write_source(tmp_path, "charset.html", raw)
    with pytest.raises(KnowledgeContractError) as raised:
        validate_source(tmp_path, _manifest(source_path, raw))
    assert raised.value.code == "SOURCE_ENCODING_INVALID"


def test_rejects_conflicting_charset_declarations(tmp_path: Path) -> None:
    raw = (
        b"<!doctype html><html><head><meta charset='UTF-8'>"
        b"<meta http-equiv='Content-Type' content='text/html; charset=euc-kr'>"
        b"</head><body>ascii</body></html>"
    )
    source_path = _write_source(tmp_path, "conflicting-charset.html", raw)
    with pytest.raises(KnowledgeContractError) as raised:
        validate_source(tmp_path, _manifest(source_path, raw))
    assert raised.value.code == "SOURCE_ENCODING_INVALID"


@pytest.mark.parametrize(
    ("name", "raw"),
    [
        ("source.txt", b"<!doctype html><html></html>"),
        ("source.html", b"plain text"),
    ],
)
def test_rejects_unsupported_extension_or_signature(tmp_path: Path, name: str, raw: bytes) -> None:
    source_path = _write_source(tmp_path, name, raw)
    with pytest.raises(KnowledgeContractError) as raised:
        validate_source(tmp_path, _manifest(source_path, raw))
    assert raised.value.code == "SOURCE_TYPE_UNSUPPORTED"


def test_low_level_reader_rejects_missing_and_non_regular_paths(tmp_path: Path) -> None:
    with pytest.raises(KnowledgeContractError) as missing:
        _read_without_following_symlink(tmp_path / "missing.html")
    assert missing.value.code == "SOURCE_NOT_FOUND"

    directory = tmp_path / "directory.html"
    directory.mkdir()
    with pytest.raises(KnowledgeContractError) as non_regular:
        _read_without_following_symlink(directory)
    assert non_regular.value.code == "SOURCE_NOT_FOUND"
