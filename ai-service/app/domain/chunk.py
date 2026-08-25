from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any


@dataclass(frozen=True, slots=True)
class KnowledgeChunk:
    chunk_id: str
    document_id: str
    version_label: str
    heading: str
    section_path: tuple[str, ...]
    page: int | None
    chunk_order: int
    text: str
    text_hash: str
    source_hash: str
    extractor_version: str
    chunker_version: str

    def as_json_object(self) -> dict[str, Any]:
        payload = asdict(self)
        return {
            "chunkId": payload["chunk_id"],
            "documentId": payload["document_id"],
            "versionLabel": payload["version_label"],
            "heading": payload["heading"],
            "sectionPath": list(payload["section_path"]),
            "page": payload["page"],
            "chunkOrder": payload["chunk_order"],
            "text": payload["text"],
            "textHash": payload["text_hash"],
            "sourceHash": payload["source_hash"],
            "extractorVersion": payload["extractor_version"],
            "chunkerVersion": payload["chunker_version"],
        }
