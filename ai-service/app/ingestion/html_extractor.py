from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable

from bs4 import BeautifulSoup, Comment, Tag

from app.domain.document import ExtractedBlock, ExtractedDocument
from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.source_validator import ValidatedSource


EXTRACTOR_VERSION = "html-structure-v1"
REDACTED_CREDENTIAL = "REDACTED_SOURCE_CREDENTIAL"
WHITESPACE = re.compile(r"\s+")
HEADING_NAMES = {f"h{level}": level for level in range(1, 7)}
BLOCK_NAMES = {*HEADING_NAMES, "p", "li", "dt", "dd", "blockquote", "pre"}

BODY_SELECTORS = (
    ".board-view-wrap .body .cont",
    "article",
    "main",
    "[role='main']",
    ".content-body",
    "#content",
    "#container",
    "body",
)
TITLE_SELECTORS = (
    ".board-view-wrap > .header > .subject",
    ".board-view-wrap .subject",
    "article h1",
    "main h1",
    "h1",
    "title",
)
REMOVED_TAGS = {
    "script",
    "style",
    "noscript",
    "template",
    "svg",
    "canvas",
    "iframe",
    "object",
    "embed",
    "form",
    "nav",
    "header",
    "footer",
}
NOISE_SELECTORS = (
    ".file",
    ".btn-board",
    ".foot",
    ".content-foot",
    ".photo-slide-control",
    ".photo-thumb-slide-wrap",
    ".hd-element",
    ".day",
    "[aria-hidden='true']",
)


def extract_html_document(manifest: KnowledgeManifest, source: ValidatedSource) -> ExtractedDocument:
    soup = BeautifulSoup(source.text, "html.parser")
    warnings: list[str] = []
    if REDACTED_CREDENTIAL in source.text:
        warnings.append("SOURCE_CREDENTIAL_REDACTION_PRESENT")

    title = _find_title(soup)
    if not title:
        raise KnowledgeContractError("NO_EXTRACTABLE_CONTENT")

    body, selector = _find_body(soup)
    if body is None:
        raise KnowledgeContractError("NO_EXTRACTABLE_CONTENT")
    if selector == "body":
        warnings.append("GENERIC_BODY_FALLBACK_USED")

    _remove_noise(body)
    blocks = _extract_blocks(body, title, warnings)
    if not blocks:
        raise KnowledgeContractError("NO_EXTRACTABLE_CONTENT")
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


def _find_title(soup: BeautifulSoup) -> str | None:
    for selector in TITLE_SELECTORS:
        element = soup.select_one(selector)
        if element is None:
            continue
        text = _normalized_text(element)
        if text:
            return text
    return None


def _find_body(soup: BeautifulSoup) -> tuple[Tag | None, str | None]:
    for selector in BODY_SELECTORS:
        element = soup.select_one(selector)
        if element is not None and _normalized_text(element):
            return element, selector
    return None, None


def _remove_noise(body: Tag) -> None:
    for comment in body.find_all(string=lambda item: isinstance(item, Comment)):
        comment.extract()
    for tag in body.find_all(REMOVED_TAGS):
        tag.decompose()
    for selector in NOISE_SELECTORS:
        for element in body.select(selector):
            element.decompose()


def _extract_blocks(body: Tag, title: str, warnings: list[str]) -> list[ExtractedBlock]:
    blocks: list[ExtractedBlock] = []
    heading_stack: dict[int, str] = {1: title}

    for element in body.find_all(BLOCK_NAMES):
        if _has_block_ancestor(element, body):
            continue
        text = _normalized_text(element)
        if not text or text == title:
            continue
        if REDACTED_CREDENTIAL in text:
            warnings.append("REDACTED_CREDENTIAL_BLOCK_SKIPPED")
            continue

        heading_level = HEADING_NAMES.get(element.name)
        if heading_level is not None:
            for level in tuple(heading_stack):
                if level >= heading_level:
                    heading_stack.pop(level)
            heading_stack[heading_level] = text
            section_path = _section_path(heading_stack, title)
            block_type = "HEADING"
        else:
            section_path = _section_path(heading_stack, title)
            block_type = _block_type(element.name)

        blocks.append(
            ExtractedBlock(
                block_order=len(blocks) + 1,
                block_type=block_type,
                text=text,
                heading_level=heading_level,
                section_path=section_path,
            )
        )
    return blocks


def _has_block_ancestor(element: Tag, body: Tag) -> bool:
    parent = element.parent
    while isinstance(parent, Tag) and parent is not body:
        if parent.name in BLOCK_NAMES:
            return True
        parent = parent.parent
    return False


def _section_path(heading_stack: dict[int, str], title: str) -> tuple[str, ...]:
    path = [heading_stack[level] for level in sorted(heading_stack)]
    if not path or path[0] != title:
        path.insert(0, title)
    return tuple(dict.fromkeys(path))


def _block_type(tag_name: str) -> str:
    return {
        "li": "LIST_ITEM",
        "dt": "TERM",
        "dd": "DEFINITION",
        "blockquote": "QUOTE",
        "pre": "PREFORMATTED",
    }.get(tag_name, "PARAGRAPH")


def _normalized_text(element: Tag) -> str:
    return _normalize_parts(element.stripped_strings)


def _normalize_parts(parts: Iterable[str]) -> str:
    joined = " ".join(parts).replace("\u200b", "").replace("\ufeff", "")
    return unicodedata.normalize("NFC", WHITESPACE.sub(" ", joined).strip())
