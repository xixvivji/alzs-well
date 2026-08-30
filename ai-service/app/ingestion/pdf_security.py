from __future__ import annotations

from collections.abc import Iterable

from pypdf import PdfReader
from pypdf.generic import ArrayObject, DictionaryObject, IndirectObject, NameObject, PdfObject

from app.errors import KnowledgeContractError


MAX_SCANNED_OBJECTS = 250_000
FORBIDDEN_KEYS = {
    "/AA",
    "/EmbeddedFiles",
    "/JavaScript",
    "/JS",
    "/Launch",
    "/RichMedia",
}
SKIPPED_RENDERING_KEYS = {"/Contents", "/Resources"}
FORBIDDEN_NAMES_BY_KEY = {
    "/S": {
        "/ImportData",
        "/JavaScript",
        "/Launch",
        "/Rendition",
        "/SubmitForm",
    },
    "/Subtype": {
        "/3D",
        "/FileAttachment",
        "/Movie",
        "/RichMedia",
        "/Screen",
        "/Sound",
    },
    "/Type": {"/EmbeddedFile"},
}


def ensure_no_active_content(reader: PdfReader) -> None:
    try:
        root = reader.trailer["/Root"]
        if _contains_active_content((root,)):
            raise KnowledgeContractError("SOURCE_ACTIVE_CONTENT_FORBIDDEN")
    except KnowledgeContractError:
        raise
    except Exception:
        raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID") from None


def _contains_active_content(initial: Iterable[PdfObject]) -> bool:
    stack = list(initial)
    visited_indirect: set[tuple[int, int]] = set()
    visited_direct: set[int] = set()
    scanned = 0

    while stack:
        current = stack.pop()
        if isinstance(current, IndirectObject):
            reference = (current.idnum, current.generation)
            if reference in visited_indirect:
                continue
            visited_indirect.add(reference)
            current = current.get_object()
        elif isinstance(current, (DictionaryObject, ArrayObject)):
            identity = id(current)
            if identity in visited_direct:
                continue
            visited_direct.add(identity)

        scanned += 1
        if scanned > MAX_SCANNED_OBJECTS:
            raise KnowledgeContractError("SOURCE_STRUCTURE_INVALID")

        if isinstance(current, DictionaryObject):
            for key, value in current.items():
                key_name = str(key)
                if key_name in FORBIDDEN_KEYS or _is_forbidden_name(key_name, value):
                    return True
                if key_name not in SKIPPED_RENDERING_KEYS:
                    stack.append(value)
        elif isinstance(current, ArrayObject):
            stack.extend(current)
    return False


def _is_forbidden_name(key: str, value: PdfObject) -> bool:
    return isinstance(value, NameObject) and str(value) in FORBIDDEN_NAMES_BY_KEY.get(key, set())
