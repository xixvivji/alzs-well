from __future__ import annotations

import hashlib
import hmac
import logging
import os
import stat
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO

from pypdf import PdfReader

from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.pdf_security import ensure_no_active_content
from app.ingestion.repository import resolve_source_file


MAX_PDF_BYTES = 104_857_600
MAX_PDF_PAGES = 500
EOF_SEARCH_WINDOW_BYTES = 2_048
PDF_HEADER = b"%PDF-"
PDF_EOF = b"%%EOF"

# Parser 내부 복구 경고가 원문 구조나 객체 번호를 stderr에 노출하지 않게 한다.
logging.getLogger("pypdf").setLevel(logging.ERROR)


@dataclass(frozen=True, slots=True)
class ValidatedPdfSource:
    path: Path
    size_bytes: int
    source_hash: str
    page_count: int
    encrypted: bool
    active_content: bool


def validate_pdf_source(repository_root: Path, manifest: KnowledgeManifest) -> ValidatedPdfSource:
    path = resolve_source_file(repository_root, manifest.source_path)
    if path.suffix.lower() != ".pdf":
        raise KnowledgeContractError("SOURCE_TYPE_UNSUPPORTED")

    with _open_without_following_symlink(path) as stream:
        size = os.fstat(stream.fileno()).st_size
        if size > MAX_PDF_BYTES:
            raise KnowledgeContractError("SOURCE_TOO_LARGE")
        _validate_markers(stream, size)
        digest = _stream_sha256(stream)
        if not hmac.compare_digest(digest, manifest.source_hash):
            raise KnowledgeContractError("SOURCE_HASH_MISMATCH")

        reader = _parse_pdf(stream)
        if reader.is_encrypted:
            raise KnowledgeContractError("SOURCE_ENCRYPTED_UNSUPPORTED")
        page_count = _page_count(reader)
        if page_count < 1 or page_count > MAX_PDF_PAGES:
            raise KnowledgeContractError("SOURCE_PAGE_LIMIT_EXCEEDED")
        ensure_no_active_content(reader)

    return ValidatedPdfSource(
        path=path,
        size_bytes=size,
        source_hash=digest,
        page_count=page_count,
        encrypted=False,
        active_content=False,
    )


def _open_without_following_symlink(path: Path) -> BinaryIO:
    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            os.close(descriptor)
            raise KnowledgeContractError("SOURCE_NOT_FOUND")
        return os.fdopen(descriptor, "rb")
    except KnowledgeContractError:
        raise
    except OSError:
        if path.is_symlink():
            raise KnowledgeContractError("SOURCE_SYMLINK_FORBIDDEN") from None
        raise KnowledgeContractError("SOURCE_NOT_FOUND") from None


def _validate_markers(stream: BinaryIO, size: int) -> None:
    try:
        stream.seek(0)
        if stream.read(len(PDF_HEADER)) != PDF_HEADER:
            raise KnowledgeContractError("SOURCE_TYPE_UNSUPPORTED")
        stream.seek(max(0, size - EOF_SEARCH_WINDOW_BYTES))
        if PDF_EOF not in stream.read(EOF_SEARCH_WINDOW_BYTES):
            raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID")
    except KnowledgeContractError:
        raise
    except OSError:
        raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID") from None


def _stream_sha256(stream: BinaryIO) -> str:
    digest = hashlib.sha256()
    try:
        stream.seek(0)
        while chunk := stream.read(65_536):
            digest.update(chunk)
        stream.seek(0)
    except OSError:
        raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID") from None
    return "sha256:" + digest.hexdigest()


def _parse_pdf(stream: BinaryIO) -> PdfReader:
    try:
        stream.seek(0)
        return PdfReader(stream, strict=True)
    except Exception:
        raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID") from None


def _page_count(reader: PdfReader) -> int:
    try:
        return len(reader.pages)
    except Exception:
        raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID") from None
