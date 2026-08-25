from __future__ import annotations

import hashlib
import json
from collections.abc import Callable
from pathlib import Path

import pytest
from pypdf import PdfWriter
from pypdf.generic import DictionaryObject, NameObject

from app.domain.manifest import KnowledgeManifest
from app.errors import ERROR_EXIT_CODES, KnowledgeContractError
from app.ingestion.pdf_validator import (
    EOF_SEARCH_WINDOW_BYTES,
    MAX_PDF_BYTES,
    MAX_PDF_PAGES,
    ValidatedPdfSource,
    validate_pdf_source,
)
from app.ingestion.pdf_security import _contains_active_content


WriterConfigurator = Callable[[PdfWriter], None]


def test_validates_single_page_pdf(tmp_path: Path) -> None:
    source_path, raw = _write_pdf(tmp_path, "valid.pdf")

    source = validate_pdf_source(tmp_path, _manifest(source_path, raw))

    assert source.page_count == 1
    assert source.encrypted is False
    assert source.active_content is False
    assert source.source_hash == _sha256(raw)


def test_accepts_uppercase_pdf_extension(tmp_path: Path) -> None:
    source_path, raw = _write_pdf(tmp_path, "valid.PDF")

    assert validate_pdf_source(tmp_path, _manifest(source_path, raw)).page_count == 1


def test_contract_vectors_match_runtime_limits_and_error_catalog(repo_root: Path) -> None:
    vectors = json.loads(
        (repo_root / "contracts/knowledge/pdf-source-validation-vectors.json").read_text(encoding="utf-8")
    )

    assert vectors["limits"] == {
        "maxBytes": MAX_PDF_BYTES,
        "maxPages": MAX_PDF_PAGES,
        "eofSearchWindowBytes": EOF_SEARCH_WINDOW_BYTES,
    }
    expected_codes = {
        vector["expectedErrorCode"] for vector in vectors["vectors"] if vector["expectedErrorCode"] is not None
    }
    assert expected_codes.issubset(ERROR_EXIT_CODES)


def test_rejects_wrong_extension(tmp_path: Path) -> None:
    source_path, raw = _write_pdf(tmp_path, "document.bin")

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_TYPE_UNSUPPORTED")


def test_rejects_one_byte_over_configured_size_limit(tmp_path: Path, monkeypatch: object) -> None:
    source_path, raw = _write_pdf(tmp_path, "large.pdf")
    monkeypatch.setattr("app.ingestion.pdf_validator.MAX_PDF_BYTES", len(raw) - 1)  # type: ignore[attr-defined]

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_TOO_LARGE")


def test_rejects_header_not_at_byte_zero(tmp_path: Path) -> None:
    _, valid = _write_pdf(tmp_path, "original.pdf")
    raw = b" " + valid
    source_path = _write_raw(tmp_path, "bad-header.pdf", raw)

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_TYPE_UNSUPPORTED")


def test_rejects_missing_eof_marker(tmp_path: Path) -> None:
    _, valid = _write_pdf(tmp_path, "original.pdf")
    raw = valid.rsplit(b"%%EOF", 1)[0]
    source_path = _write_raw(tmp_path, "missing-eof.pdf", raw)

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_STRUCTURE_INVALID")


def test_rejects_invalid_pdf_structure(tmp_path: Path) -> None:
    raw = b"%PDF-1.7\nbroken cross reference\n%%EOF\n"
    source_path = _write_raw(tmp_path, "broken.pdf", raw)

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_STRUCTURE_INVALID")


def test_rejects_hash_mismatch_before_parsing(tmp_path: Path) -> None:
    source_path, raw = _write_pdf(tmp_path, "hash.pdf")

    _assert_error(tmp_path, _manifest(source_path, raw + b"different"), "SOURCE_HASH_MISMATCH")


def test_rejects_encrypted_pdf_without_decryption_attempt(tmp_path: Path) -> None:
    source_path, raw = _write_pdf(tmp_path, "encrypted.pdf", lambda writer: writer.encrypt("test-password"))

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_ENCRYPTED_UNSUPPORTED")


def test_rejects_zero_page_pdf(tmp_path: Path) -> None:
    source_path, raw = _write_pdf(tmp_path, "zero-pages.pdf", pages=0)

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_PAGE_LIMIT_EXCEEDED")


def test_rejects_page_count_over_configured_limit(tmp_path: Path, monkeypatch: object) -> None:
    source_path, raw = _write_pdf(tmp_path, "pages.pdf")
    monkeypatch.setattr("app.ingestion.pdf_validator.MAX_PDF_PAGES", 0)  # type: ignore[attr-defined]

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_PAGE_LIMIT_EXCEEDED")


@pytest.mark.parametrize(
    "configure",
    [
        lambda writer: writer.add_js("app.alert('synthetic')"),
        lambda writer: writer.add_attachment("synthetic.txt", b"fixture"),
    ],
)
def test_rejects_active_or_embedded_content(tmp_path: Path, configure: WriterConfigurator) -> None:
    source_path, raw = _write_pdf(tmp_path, "active.pdf", configure)

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_ACTIVE_CONTENT_FORBIDDEN")


def test_rejects_object_graph_over_scan_limit(
    tmp_path: Path, monkeypatch: object
) -> None:
    source_path, raw = _write_pdf(tmp_path, "object-limit.pdf")
    monkeypatch.setattr("app.ingestion.pdf_security.MAX_SCANNED_OBJECTS", 0)  # type: ignore[attr-defined]

    _assert_error(tmp_path, _manifest(source_path, raw), "SOURCE_STRUCTURE_INVALID")


def test_screen_blend_mode_is_not_misclassified_as_active_content() -> None:
    graphics_state = DictionaryObject(
        {NameObject("/Type"): NameObject("/ExtGState"), NameObject("/BM"): NameObject("/Screen")}
    )

    assert _contains_active_content((graphics_state,)) is False


def test_screen_annotation_subtype_is_active_content() -> None:
    annotation = DictionaryObject(
        {NameObject("/Type"): NameObject("/Annot"), NameObject("/Subtype"): NameObject("/Screen")}
    )

    assert _contains_active_content((annotation,)) is True


def test_low_level_open_rejects_missing_and_non_regular_paths(tmp_path: Path) -> None:
    from app.ingestion.pdf_validator import _open_without_following_symlink

    with pytest.raises(KnowledgeContractError) as missing:
        _open_without_following_symlink(tmp_path / "missing.pdf")
    assert missing.value.code == "SOURCE_NOT_FOUND"

    directory = tmp_path / "directory.pdf"
    directory.mkdir()
    with pytest.raises(KnowledgeContractError) as non_regular:
        _open_without_following_symlink(directory)
    assert non_regular.value.code == "SOURCE_NOT_FOUND"


def _write_pdf(
    root: Path,
    name: str,
    configure: WriterConfigurator | None = None,
    *,
    pages: int = 1,
) -> tuple[str, bytes]:
    relative = Path("knowledge/test-fixtures") / name
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    writer = PdfWriter()
    for _ in range(pages):
        writer.add_blank_page(width=72, height=72)
    if configure is not None:
        configure(writer)
    writer.write(path)
    return relative.as_posix(), path.read_bytes()


def _write_raw(root: Path, name: str, raw: bytes) -> str:
    relative = Path("knowledge/test-fixtures") / name
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return relative.as_posix()


def _manifest(source_path: str, raw: bytes) -> KnowledgeManifest:
    return KnowledgeManifest({"sourcePath": source_path, "sourceHash": _sha256(raw)})


def _sha256(raw: bytes) -> str:
    return "sha256:" + hashlib.sha256(raw).hexdigest()


def _assert_error(root: Path, manifest: KnowledgeManifest, expected_code: str) -> None:
    with pytest.raises(KnowledgeContractError) as caught:
        validate_pdf_source(root, manifest)
    assert caught.value.code == expected_code
