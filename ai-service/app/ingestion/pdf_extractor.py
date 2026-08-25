from __future__ import annotations

import hmac
import math
import os
import re
import unicodedata
from collections import Counter

from pypdf import PdfReader

from app.domain.document import ExtractedBlock, ExtractedDocument
from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.pdf_validator import (
    ValidatedPdfSource,
    _open_without_following_symlink,
    _parse_pdf,
    _stream_sha256,
)


EXTRACTOR_VERSION = "pypdf-text-v1"
MIN_PAGES_FOR_COVERAGE_CHECK = 5
MIN_SEARCHABLE_PAGE_RATIO = 0.10
WHITESPACE = re.compile(r"[\t\x0b\x0c\r ]+")
PAGE_NUMBER = re.compile(r"^(?:page\s*)?\d+(?:\s*/\s*\d+)?$", re.IGNORECASE)
MARKED_HEADING = re.compile(r"^(?:[◆◇◈■□▶▷●○▪▫※]|Q\d*[.)]?)\s*\S", re.IGNORECASE)
HEADING_PATTERNS = (
    (MARKED_HEADING, 2),
    (re.compile(r"^제\s*\d+\s*장(?:\s|$)"), 1),
    (re.compile(r"^제\s*\d+\s*절(?:\s|$)"), 2),
    (re.compile(r"^제\s*\d+\s*조(?:\s|$)"), 3),
    (re.compile(r"^\d+(?:\.\d+){1,3}(?:[.)]|\s)"), 3),
    (re.compile(r"^\d+[.)]\s*\S"), 2),
    (re.compile(r"^[IVX]+[.)]\s*\S", re.IGNORECASE), 2),
    (re.compile(r"^[가-힣][.)]\s*\S"), 3),
)


def extract_pdf_document(
    manifest: KnowledgeManifest, source: ValidatedPdfSource
) -> ExtractedDocument:
    with _open_without_following_symlink(source.path) as stream:
        metadata = os.fstat(stream.fileno())
        digest = _stream_sha256(stream)
        if metadata.st_size != source.size_bytes or not hmac.compare_digest(
            digest, source.source_hash
        ):
            raise KnowledgeContractError("SOURCE_HASH_MISMATCH")
        reader = _parse_pdf(stream)
        if len(reader.pages) != source.page_count:
            raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID")
        page_lines, extraction_warnings = _extract_page_lines(reader)
        metadata_title, metadata_warning = _metadata_title(reader)

    if _requires_ocr(page_lines):
        raise KnowledgeContractError("OCR_REQUIRED")

    title = metadata_title or _first_content_line(page_lines)
    if title is None:
        raise KnowledgeContractError("OCR_REQUIRED")
    cleaned_pages, margin_warnings = _remove_repeated_margins(page_lines)

    blocks = _build_blocks(cleaned_pages, title)
    if not any(block.block_type != "HEADING" for block in blocks):
        raise KnowledgeContractError("NO_EXTRACTABLE_CONTENT")

    warnings = [*extraction_warnings, *metadata_warning, *margin_warnings]
    if any(not any(line for line in page) for page in cleaned_pages):
        warnings.append("EMPTY_TEXT_PAGE")
    if not any(block.block_type == "HEADING" for block in blocks):
        warnings.append("NO_STRUCTURAL_HEADING")

    return ExtractedDocument(
        document_id=manifest.document_id,
        version_label=manifest.version_label,
        title=title,
        source_hash=source.source_hash,
        extractor_version=EXTRACTOR_VERSION,
        blocks=tuple(blocks),
        warnings=tuple(dict.fromkeys(warnings)),
    )


def _extract_page_lines(
    reader: PdfReader,
) -> tuple[tuple[tuple[str, ...], ...], tuple[str, ...]]:
    pages: list[tuple[str, ...]] = []
    invalid_unicode_removed = False
    try:
        for page in reader.pages:
            text = page.extract_text() or ""
            invalid_unicode_removed = invalid_unicode_removed or _contains_invalid_unicode(text)
            pages.append(_normalize_page_lines(text))
    except Exception:
        raise KnowledgeContractError("EXTRACTION_FAILED") from None
    warnings = ("INVALID_UNICODE_REMOVED",) if invalid_unicode_removed else ()
    return tuple(pages), warnings


def _normalize_page_lines(text: str) -> tuple[str, ...]:
    sanitized = _sanitize_unicode(text)
    normalized = unicodedata.normalize(
        "NFC", sanitized.replace("\u200b", "").replace("\ufeff", "")
    )
    lines: list[str] = []
    previous_blank = False
    for raw_line in normalized.splitlines():
        line = WHITESPACE.sub(" ", raw_line).strip()
        if not line:
            if lines and not previous_blank:
                lines.append("")
            previous_blank = True
            continue
        lines.append(line)
        previous_blank = False
    while lines and not lines[-1]:
        lines.pop()
    return tuple(lines)


def _sanitize_unicode(text: str) -> str:
    return "".join(
        character
        for character in text
        if character in "\n\r\t" or unicodedata.category(character) not in {"Cc", "Cs"}
    )


def _contains_invalid_unicode(text: str) -> bool:
    return any(
        character not in "\n\r\t" and unicodedata.category(character) in {"Cc", "Cs"}
        for character in text
    )


def _metadata_title(reader: PdfReader) -> tuple[str | None, tuple[str, ...]]:
    try:
        value = reader.metadata.title if reader.metadata is not None else None
    except Exception:
        return None, ()
    if not isinstance(value, str):
        return None, ()
    warning = ("INVALID_UNICODE_REMOVED",) if _contains_invalid_unicode(value) else ()
    title = WHITESPACE.sub(
        " ", unicodedata.normalize("NFC", _sanitize_unicode(value))
    ).strip()
    return title[:200] or None, warning


def _requires_ocr(pages: tuple[tuple[str, ...], ...]) -> bool:
    searchable_pages = sum(
        any(character.isalnum() for line in page for character in line) for page in pages
    )
    if searchable_pages == 0:
        return True
    return (
        len(pages) >= MIN_PAGES_FOR_COVERAGE_CHECK
        and searchable_pages / len(pages) < MIN_SEARCHABLE_PAGE_RATIO
    )


def _remove_repeated_margins(
    pages: tuple[tuple[str, ...], ...],
) -> tuple[tuple[tuple[str, ...], ...], tuple[str, ...]]:
    nonempty_pages = [tuple(line for line in page if line) for page in pages]
    threshold = max(2, math.ceil(len(pages) * 0.6))
    first_counts = Counter(page[0] for page in nonempty_pages if page)
    last_counts = Counter(page[-1] for page in nonempty_pages if page)
    repeated_headers = {line for line, count in first_counts.items() if count >= threshold}
    repeated_footers = {line for line, count in last_counts.items() if count >= threshold}

    cleaned: list[tuple[str, ...]] = []
    header_removed = False
    footer_removed = False
    for page in pages:
        lines = list(page)
        first = next((index for index, line in enumerate(lines) if line), None)
        last = next((index for index in range(len(lines) - 1, -1, -1) if lines[index]), None)
        if first is not None and (lines[first] in repeated_headers or PAGE_NUMBER.fullmatch(lines[first])):
            header_removed = header_removed or lines[first] in repeated_headers
            lines[first] = ""
        if last is not None and last != first and (
            lines[last] in repeated_footers or PAGE_NUMBER.fullmatch(lines[last])
        ):
            footer_removed = footer_removed or lines[last] in repeated_footers
            lines[last] = ""
        cleaned.append(_trim_blank_lines(lines))

    warnings: list[str] = []
    if header_removed:
        warnings.append("REPEATED_HEADER_REMOVED")
    if footer_removed:
        warnings.append("REPEATED_FOOTER_REMOVED")
    return tuple(cleaned), tuple(warnings)


def _trim_blank_lines(lines: list[str]) -> tuple[str, ...]:
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return tuple(lines)


def _first_content_line(pages: tuple[tuple[str, ...], ...]) -> str | None:
    for page in pages:
        for line in page:
            if line:
                return line[:200]
    return None


def _build_blocks(
    pages: tuple[tuple[str, ...], ...], title: str
) -> list[ExtractedBlock]:
    blocks: list[ExtractedBlock] = []
    heading_stack: dict[int, str] = {1: title}

    for page_number, raw_lines in enumerate(pages, start=1):
        lines = _join_wrapped_marked_headings(raw_lines)
        paragraph: list[str] = []

        def flush_paragraph() -> None:
            if not paragraph:
                return
            text = " ".join(paragraph)
            paragraph.clear()
            blocks.append(
                ExtractedBlock(
                    block_order=len(blocks) + 1,
                    block_type="PARAGRAPH",
                    text=text,
                    heading_level=None,
                    section_path=_section_path(heading_stack, title),
                    page_start=page_number,
                    page_end=page_number,
                )
            )

        for line in lines:
            if not line:
                flush_paragraph()
                continue
            if page_number == 1 and not blocks and not paragraph and line == title:
                continue
            heading_level = _heading_level(line)
            if heading_level is None:
                paragraph.append(line)
                continue
            flush_paragraph()
            for level in tuple(heading_stack):
                if level >= heading_level:
                    heading_stack.pop(level)
            heading_stack[heading_level] = line
            blocks.append(
                ExtractedBlock(
                    block_order=len(blocks) + 1,
                    block_type="HEADING",
                    text=line,
                    heading_level=heading_level,
                    section_path=_section_path(heading_stack, title),
                    page_start=page_number,
                    page_end=page_number,
                )
            )
        flush_paragraph()
    return blocks


def _join_wrapped_marked_headings(lines: tuple[str, ...]) -> tuple[str, ...]:
    joined: list[str] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        if (
            MARKED_HEADING.match(line)
            and not line.endswith(("?", "？"))
            and index + 1 < len(lines)
            and lines[index + 1].endswith(("?", "？"))
            and len(line) + len(lines[index + 1]) + 1 <= 120
        ):
            joined.append(f"{line} {lines[index + 1]}")
            index += 2
            continue
        joined.append(line)
        index += 1
    return tuple(joined)


def _heading_level(line: str) -> int | None:
    if len(line) > 120:
        return None
    for pattern, level in HEADING_PATTERNS:
        if pattern.match(line):
            return level
    return None


def _section_path(heading_stack: dict[int, str], title: str) -> tuple[str, ...]:
    path = [heading_stack[level] for level in sorted(heading_stack)]
    if not path or path[0] != title:
        path.insert(0, title)
    return tuple(dict.fromkeys(path))
