from __future__ import annotations

import hashlib
import hmac
import os
import re
import stat
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path

from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.repository import resolve_source_file


MAX_HTML_BYTES = 5_242_880
UTF8_BOM = b"\xef\xbb\xbf"
HTML_EXTENSIONS = {".html", ".htm"}
HTML_SIGNATURES = ("<!doctype html", "<html")
CHARSET_PATTERN = re.compile(r"charset\s*=\s*['\"]?\s*([^\s;'\"/>]+)", re.IGNORECASE)


@dataclass(frozen=True, slots=True)
class ValidatedSource:
    path: Path
    size_bytes: int
    source_hash: str
    encoding: str
    text: str


class _MetaCharsetCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.charsets: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() != "meta":
            return
        attributes = {key.lower(): value for key, value in attrs}
        direct = attributes.get("charset")
        if direct:
            self.charsets.append(direct.strip())
        content = attributes.get("content")
        if content:
            match = CHARSET_PATTERN.search(content)
            if match:
                self.charsets.append(match.group(1).strip())


def validate_source(repository_root: Path, manifest: KnowledgeManifest) -> ValidatedSource:
    path = resolve_source_file(repository_root, manifest.source_path)
    if path.suffix.lower() not in HTML_EXTENSIONS:
        raise KnowledgeContractError("SOURCE_TYPE_UNSUPPORTED")

    try:
        size = path.stat().st_size
    except OSError:
        raise KnowledgeContractError("SOURCE_NOT_FOUND") from None
    if size > MAX_HTML_BYTES:
        raise KnowledgeContractError("SOURCE_TOO_LARGE")

    raw = _read_without_following_symlink(path)
    if len(raw) > MAX_HTML_BYTES:
        raise KnowledgeContractError("SOURCE_TOO_LARGE")

    digest = "sha256:" + hashlib.sha256(raw).hexdigest()
    if not hmac.compare_digest(digest, manifest.source_hash):
        raise KnowledgeContractError("SOURCE_HASH_MISMATCH")

    text = _decode_utf8(raw)
    _validate_html_signature(text)
    _validate_html_charset(text)
    return ValidatedSource(path=path, size_bytes=len(raw), source_hash=digest, encoding="UTF-8", text=text)


def _read_without_following_symlink(path: Path) -> bytes:
    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
    except OSError:
        if path.is_symlink():
            raise KnowledgeContractError("SOURCE_SYMLINK_FORBIDDEN") from None
        raise KnowledgeContractError("SOURCE_NOT_FOUND") from None
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise KnowledgeContractError("SOURCE_NOT_FOUND")
        chunks: list[bytes] = []
        remaining = MAX_HTML_BYTES + 1
        while remaining > 0:
            chunk = os.read(descriptor, min(65_536, remaining))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def _decode_utf8(raw: bytes) -> str:
    try:
        if raw.startswith(UTF8_BOM):
            return raw.decode("utf-8-sig", errors="strict")
        return raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise KnowledgeContractError("SOURCE_ENCODING_INVALID") from None


def _validate_html_signature(text: str) -> None:
    prefix = text.lstrip().lower()
    if not prefix.startswith(HTML_SIGNATURES):
        raise KnowledgeContractError("SOURCE_TYPE_UNSUPPORTED")


def _validate_html_charset(text: str) -> None:
    parser = _MetaCharsetCollector()
    try:
        parser.feed(text)
        parser.close()
    except Exception:
        raise KnowledgeContractError("SOURCE_ENCODING_INVALID") from None
    normalized = {value.lower().replace("_", "-") for value in parser.charsets}
    if normalized and not normalized.issubset({"utf-8", "utf8"}):
        raise KnowledgeContractError("SOURCE_ENCODING_INVALID")
