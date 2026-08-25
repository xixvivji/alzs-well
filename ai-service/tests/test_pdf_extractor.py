from __future__ import annotations

import hashlib
from pathlib import Path

import pytest
from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject

from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.chunker import chunk_document
from app.ingestion.pdf_extractor import (
    EXTRACTOR_VERSION,
    _heading_level,
    _join_wrapped_marked_headings,
    _metadata_title,
    _contains_invalid_unicode,
    _normalize_page_lines,
    _remove_repeated_margins,
    _requires_ocr,
    extract_pdf_document,
)
from app.ingestion.pdf_validator import validate_pdf_source


def test_extracts_pages_removes_margins_and_builds_page_range(tmp_path: Path) -> None:
    source_path, raw = _write_text_pdf(
        tmp_path,
        (
            ("Shared Header", "1. Introduction", "First page body.", "1"),
            ("Shared Header", "Second page body.", "2"),
        ),
        title="Document Title",
    )
    manifest = _manifest(source_path, raw)
    source = validate_pdf_source(tmp_path, manifest)

    document = extract_pdf_document(manifest, source)
    chunks = chunk_document(document)

    assert document.title == "Document Title"
    assert document.extractor_version == EXTRACTOR_VERSION
    assert "REPEATED_HEADER_REMOVED" in document.warnings
    assert [block.text for block in document.blocks] == [
        "1. Introduction",
        "First page body.",
        "Second page body.",
    ]
    assert document.blocks[0].block_type == "HEADING"
    assert document.blocks[0].section_path == ("Document Title", "1. Introduction")
    assert len(chunks) == 1
    assert chunks[0].page == 1
    assert chunks[0].page_start == 1
    assert chunks[0].page_end == 2
    assert chunks[0].chunker_version == "pdf-structure-ko-v1"
    assert chunks[0].as_json_object()["pageStart"] == 1
    assert chunks[0].as_json_object()["pageEnd"] == 2


def test_uses_first_source_line_as_title_when_metadata_is_absent(tmp_path: Path) -> None:
    source_path, raw = _write_text_pdf(
        tmp_path,
        (("Fallback Title", "Body content remains searchable."),),
    )
    manifest = _manifest(source_path, raw)

    document = extract_pdf_document(manifest, validate_pdf_source(tmp_path, manifest))

    assert document.title == "Fallback Title"
    assert [block.text for block in document.blocks] == ["Body content remains searchable."]
    assert document.warnings == ("NO_STRUCTURAL_HEADING",)


def test_rejects_pdf_without_searchable_text_as_ocr_required(tmp_path: Path) -> None:
    relative = Path("knowledge/test-fixtures/scan.pdf")
    path = tmp_path / relative
    path.parent.mkdir(parents=True)
    writer = PdfWriter()
    writer.add_blank_page(width=612, height=792)
    writer.write(path)
    raw = path.read_bytes()
    manifest = _manifest(relative.as_posix(), raw)

    with pytest.raises(KnowledgeContractError) as caught:
        extract_pdf_document(manifest, validate_pdf_source(tmp_path, manifest))

    assert caught.value.code == "OCR_REQUIRED"


def test_rejects_large_pdf_with_text_on_less_than_ten_percent_of_pages(tmp_path: Path) -> None:
    source_path, raw = _write_text_pdf(
        tmp_path,
        (("Cover", "Only searchable page"), *(() for _ in range(19))),
        title="Mostly scanned",
    )
    manifest = _manifest(source_path, raw)

    with pytest.raises(KnowledgeContractError) as caught:
        extract_pdf_document(manifest, validate_pdf_source(tmp_path, manifest))

    assert caught.value.code == "OCR_REQUIRED"


def test_rechecks_hash_before_extraction(tmp_path: Path) -> None:
    source_path, raw = _write_text_pdf(tmp_path, (("Title", "Body"),))
    manifest = _manifest(source_path, raw)
    source = validate_pdf_source(tmp_path, manifest)
    (tmp_path / source_path).write_bytes(raw + b"changed")

    with pytest.raises(KnowledgeContractError) as caught:
        extract_pdf_document(manifest, source)

    assert caught.value.code == "SOURCE_HASH_MISMATCH"


def test_converts_parser_text_failure_to_safe_error(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    source_path, raw = _write_text_pdf(tmp_path, (("Title", "Body"),))
    manifest = _manifest(source_path, raw)
    source = validate_pdf_source(tmp_path, manifest)

    def fail_extract_text(*args: object, **kwargs: object) -> str:
        raise RuntimeError("must not escape")

    monkeypatch.setattr("pypdf._page.PageObject.extract_text", fail_extract_text)
    with pytest.raises(KnowledgeContractError) as caught:
        extract_pdf_document(manifest, source)

    assert caught.value.code == "EXTRACTION_FAILED"


def test_normalizes_lines_and_collapses_repeated_blank_lines() -> None:
    assert _normalize_page_lines("  A\t B \r\n\r\n\r\n C\u200b\udf2b\x00 \n") == (
        "A B",
        "",
        "C",
    )
    assert _contains_invalid_unicode("valid\n") is False
    assert _contains_invalid_unicode("invalid\udf2b") is True
    assert _requires_ocr((("searchable",), *(() for _ in range(4)))) is False
    assert _requires_ocr((("searchable",), *(() for _ in range(10)))) is True


def test_sanitizes_invalid_unicode_in_metadata_title() -> None:
    class Metadata:
        title = "Safe\udf2b Title"

    class Reader:
        metadata = Metadata()

    title, warnings = _metadata_title(Reader())  # type: ignore[arg-type]

    assert title == "Safe Title"
    assert warnings == ("INVALID_UNICODE_REMOVED",)


def test_removes_repeated_footer_and_page_numbers() -> None:
    pages, warnings = _remove_repeated_margins(
        (("Title A", "Body A", "Common Footer"), ("Title B", "Body B", "Common Footer"))
    )

    assert pages == (("Title A", "Body A"), ("Title B", "Body B"))
    assert warnings == ("REPEATED_FOOTER_REMOVED",)


def test_joins_only_wrapped_marked_question_headings() -> None:
    assert _join_wrapped_marked_headings(
        ("◈ 계좌개설이 필요한 경우 어떻게", "해야 하나요?", "답변입니다.")
    ) == ("◈ 계좌개설이 필요한 경우 어떻게 해야 하나요?", "답변입니다.")
    assert _join_wrapped_marked_headings(("1. 일반 절", "질문인가요?")) == (
        "1. 일반 절",
        "질문인가요?",
    )


@pytest.mark.parametrize(
    ("line", "expected"),
    [
        ("제1장 총칙", 1),
        ("제2절 신청", 2),
        ("제3조 정의", 3),
        ("1.2 Details", 3),
        ("2) Scope", 2),
        ("IV. Appendix", 2),
        ("가. 대상", 3),
        ("◈ 어디에서 신청할 수 있나요?", 2),
        ("Q1. 신청 대상은 누구인가요?", 2),
        ("이 문장은 제목이 아닙니다.", None),
        ("x" * 121, None),
    ],
)
def test_heading_levels(line: str, expected: int | None) -> None:
    assert _heading_level(line) == expected


def _write_text_pdf(
    root: Path,
    pages: tuple[tuple[str, ...], ...],
    *,
    title: str | None = None,
) -> tuple[str, bytes]:
    relative = Path("knowledge/test-fixtures/text.pdf")
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    writer = PdfWriter()
    if title is not None:
        writer.add_metadata({"/Title": title})
    for lines in pages:
        page = writer.add_blank_page(width=612, height=792)
        font = DictionaryObject(
            {
                NameObject("/Type"): NameObject("/Font"),
                NameObject("/Subtype"): NameObject("/Type1"),
                NameObject("/BaseFont"): NameObject("/Helvetica"),
            }
        )
        resources = DictionaryObject(
            {
                NameObject("/Font"): DictionaryObject(
                    {NameObject("/F1"): writer._add_object(font)}
                )
            }
        )
        page[NameObject("/Resources")] = resources
        commands = ["BT", "/F1 12 Tf", "40 740 Td"]
        for index, line in enumerate(lines):
            if index:
                commands.append("0 -24 Td")
            commands.append(f"({_escape_pdf_text(line)}) Tj")
        commands.append("ET")
        content = DecodedStreamObject()
        content.set_data("\n".join(commands).encode("ascii"))
        page[NameObject("/Contents")] = writer._add_object(content)
    writer.write(path)
    return relative.as_posix(), path.read_bytes()


def _escape_pdf_text(value: str) -> str:
    return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def _manifest(source_path: str, raw: bytes) -> KnowledgeManifest:
    return KnowledgeManifest(
        {
            "documentId": "DOC-SYN-PDF-001",
            "versionLabel": "1.0.0",
            "sourcePath": source_path,
            "sourceHash": "sha256:" + hashlib.sha256(raw).hexdigest(),
        }
    )
