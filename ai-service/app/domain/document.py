from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class ExtractedBlock:
    block_order: int
    block_type: str
    text: str
    heading_level: int | None
    section_path: tuple[str, ...]
    page_start: int | None = None
    page_end: int | None = None


@dataclass(frozen=True, slots=True)
class ExtractedDocument:
    document_id: str
    version_label: str
    title: str
    source_hash: str
    extractor_version: str
    blocks: tuple[ExtractedBlock, ...]
    warnings: tuple[str, ...]

    @property
    def section_paths(self) -> tuple[tuple[str, ...], ...]:
        return tuple(dict.fromkeys(block.section_path for block in self.blocks))
