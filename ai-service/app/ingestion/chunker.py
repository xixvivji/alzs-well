from __future__ import annotations

import hashlib
import json
import unicodedata

from app.domain.chunk import KnowledgeChunk
from app.domain.document import ExtractedDocument
from app.errors import KnowledgeContractError


CHUNKER_VERSION = "structure-ko-v1"
PDF_CHUNKER_VERSION = "pdf-structure-ko-v1"
DEFAULT_MAX_CHARS = 1_200


def chunk_document(
    document: ExtractedDocument, *, max_chars: int = DEFAULT_MAX_CHARS
) -> tuple[KnowledgeChunk, ...]:
    if max_chars < 1:
        raise ValueError("max_chars must be positive")

    candidates: list[tuple[tuple[str, ...], str, int | None, int | None]] = []
    pending_path: tuple[str, ...] | None = None
    pending_parts: list[str] = []
    pending_page_start: int | None = None
    pending_page_end: int | None = None

    def flush() -> None:
        nonlocal pending_path, pending_parts, pending_page_start, pending_page_end
        if pending_path is not None and pending_parts:
            candidates.append(
                (pending_path, "\n\n".join(pending_parts), pending_page_start, pending_page_end)
            )
        pending_path = None
        pending_parts = []
        pending_page_start = None
        pending_page_end = None

    for block in document.blocks:
        if block.block_type == "HEADING":
            flush()
            continue
        for segment in _split_text(block.text, max_chars):
            if pending_path != block.section_path:
                flush()
                pending_path = block.section_path
            proposed = "\n\n".join([*pending_parts, segment])
            if pending_parts and len(proposed) > max_chars:
                flush()
                pending_path = block.section_path
            pending_parts.append(segment)
            pending_page_start = _minimum_page(pending_page_start, block.page_start)
            pending_page_end = _maximum_page(pending_page_end, block.page_end)
    flush()

    if not candidates:
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")

    chunks = tuple(
        _build_chunk(document, section_path, text, page_start, page_end, order)
        for order, (section_path, text, page_start, page_end) in enumerate(candidates, start=1)
    )
    _validate_chunks(chunks, max_chars)
    return chunks


def canonical_chunk_id(
    document_id: str,
    version_label: str,
    section_path: tuple[str, ...] | list[str],
    chunk_order: int,
    text_hash: str,
    chunker_version: str,
) -> tuple[str, str]:
    values = [
        _nfc(document_id),
        _nfc(version_label),
        [_nfc(element) for element in section_path],
        chunk_order,
        _nfc(text_hash),
        _nfc(chunker_version),
    ]
    canonical_json = json.dumps(values, ensure_ascii=False, separators=(",", ":"))
    digest = hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()
    return "chk_" + digest, canonical_json


def _build_chunk(
    document: ExtractedDocument,
    section_path: tuple[str, ...],
    text: str,
    page_start: int | None,
    page_end: int | None,
    chunk_order: int,
) -> KnowledgeChunk:
    normalized_text = _nfc(text)
    text_hash = "sha256:" + hashlib.sha256(normalized_text.encode("utf-8")).hexdigest()
    chunker_version = (
        PDF_CHUNKER_VERSION
        if document.extractor_version.startswith("pypdf-")
        else CHUNKER_VERSION
    )
    chunk_id, _ = canonical_chunk_id(
        document.document_id,
        document.version_label,
        section_path,
        chunk_order,
        text_hash,
        chunker_version,
    )
    return KnowledgeChunk(
        chunk_id=chunk_id,
        document_id=document.document_id,
        version_label=document.version_label,
        heading=section_path[-1] if section_path else document.title,
        section_path=section_path,
        page=page_start,
        chunk_order=chunk_order,
        text=normalized_text,
        text_hash=text_hash,
        source_hash=document.source_hash,
        extractor_version=document.extractor_version,
        chunker_version=chunker_version,
        page_start=page_start,
        page_end=page_end,
    )


def _split_text(text: str, max_chars: int) -> tuple[str, ...]:
    remaining = text.strip()
    segments: list[str] = []
    while len(remaining) > max_chars:
        split_at = remaining.rfind(" ", 0, max_chars + 1)
        if split_at < 1:
            split_at = max_chars
        segments.append(remaining[:split_at].rstrip())
        remaining = remaining[split_at:].lstrip()
    if remaining:
        segments.append(remaining)
    return tuple(segments)


def _validate_chunks(chunks: tuple[KnowledgeChunk, ...], max_chars: int) -> None:
    for expected_order, chunk in enumerate(chunks, start=1):
        if (
            chunk.chunk_order != expected_order
            or not chunk.text
            or len(chunk.text) > max_chars
            or not chunk.chunk_id.startswith("chk_")
            or (chunk.page_start is None) != (chunk.page_end is None)
            or (
                chunk.page_start is not None
                and chunk.page_end is not None
                and (chunk.page_start < 1 or chunk.page_end < chunk.page_start)
            )
        ):
            raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")


def _minimum_page(current: int | None, candidate: int | None) -> int | None:
    if candidate is None:
        return current
    return candidate if current is None else min(current, candidate)


def _maximum_page(current: int | None, candidate: int | None) -> int | None:
    if candidate is None:
        return current
    return candidate if current is None else max(current, candidate)


def _nfc(value: str) -> str:
    return unicodedata.normalize("NFC", value)
