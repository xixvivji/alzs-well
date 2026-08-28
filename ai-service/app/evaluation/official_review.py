from __future__ import annotations

import json
import os
import tempfile
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from app.domain.chunk import KnowledgeChunk
from app.domain.document import ExtractedDocument
from app.domain.manifest import KnowledgeManifest, is_effective
from app.errors import KnowledgeContractError
from app.ingestion.chunker import chunk_document
from app.ingestion.html_extractor import extract_html_document
from app.ingestion.manifest_loader import load_and_validate_manifest
from app.ingestion.pdf_extractor import extract_pdf_document
from app.ingestion.pdf_validator import validate_pdf_source
from app.ingestion.source_validator import validate_source


@dataclass(frozen=True, slots=True)
class ReviewDocumentSummary:
    document_id: str
    version_label: str
    chunk_count: int
    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class OfficialReviewCorpusResult:
    output_path: Path
    document_count: int
    chunk_count: int
    documents: tuple[ReviewDocumentSummary, ...]


def build_official_review_corpus(
    repository_root: Path,
    manifest_paths: tuple[str, ...],
    *,
    as_of: date,
) -> OfficialReviewCorpusResult:
    """Build a non-searchable corpus from reviewable official documents."""
    if not manifest_paths or len(manifest_paths) != len(set(manifest_paths)):
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID")

    rows: list[dict[str, object]] = []
    chunk_ids: set[str] = set()
    summaries: list[ReviewDocumentSummary] = []
    for manifest_path in manifest_paths:
        manifest = load_and_validate_manifest(repository_root, manifest_path)
        _ensure_review_corpus_manifest(manifest)
        document = _extract_document(repository_root, manifest)
        chunks = chunk_document(document)
        for chunk in chunks:
            if chunk.chunk_id in chunk_ids:
                raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")
            chunk_ids.add(chunk.chunk_id)
            rows.append(_review_row(chunk, manifest, as_of))
        summaries.append(
            ReviewDocumentSummary(
                document_id=manifest.document_id,
                version_label=manifest.version_label,
                chunk_count=len(chunks),
                warnings=document.warnings,
            )
        )

    output_path = (
        repository_root
        / "ai-service"
        / "data"
        / "derived"
        / "evaluation"
        / f"retrieval-official-review-corpus-{as_of.isoformat()}.jsonl"
    )
    _write_atomic(output_path, rows)
    return OfficialReviewCorpusResult(
        output_path=output_path,
        document_count=len(summaries),
        chunk_count=len(rows),
        documents=tuple(summaries),
    )


def _ensure_review_corpus_manifest(manifest: KnowledgeManifest) -> None:
    governance = (
        manifest.payload["usageRights"],
        manifest.approval_status,
        manifest.lifecycle_status,
    )
    allowed_governance = {
        ("REVIEW_REQUIRED", "IN_REVIEW", "PENDING_ACTIVATION"),
        ("INTERNAL_USE_APPROVED", "APPROVED", "ACTIVE"),
    }
    if (
        manifest.payload["sourceType"] != "OFFICIAL_EXTERNAL"
        or manifest.classification != "PUBLIC_OFFICIAL"
        or governance not in allowed_governance
    ):
        raise KnowledgeContractError(
            "MANIFEST_SCHEMA_INVALID", {"schemaPath": "officialReviewGovernance"}
        )


def _extract_document(
    repository_root: Path, manifest: KnowledgeManifest
) -> ExtractedDocument:
    if Path(manifest.source_path).suffix.lower() == ".pdf":
        return extract_pdf_document(manifest, validate_pdf_source(repository_root, manifest))
    return extract_html_document(manifest, validate_source(repository_root, manifest))


def _review_row(
    chunk: KnowledgeChunk, manifest: KnowledgeManifest, as_of: date
) -> dict[str, object]:
    return {
        "chunkId": chunk.chunk_id,
        "documentId": manifest.document_id,
        "documentType": manifest.document_type,
        "versionLabel": manifest.version_label,
        "title": manifest.title,
        "issuer": manifest.issuer,
        "sourceUrl": manifest.source_url,
        "sourceHash": manifest.source_hash,
        "textHash": chunk.text_hash,
        "heading": chunk.heading,
        "sectionPath": list(chunk.section_path),
        "page": chunk.page,
        "pageStart": chunk.page_start,
        "pageEnd": chunk.page_end,
        "chunkOrder": chunk.chunk_order,
        "content": chunk.text,
        "extractorVersion": chunk.extractor_version,
        "chunkerVersion": chunk.chunker_version,
        "allowedRoles": list(manifest.allowed_roles),
        "audience": manifest.audience,
        "approvalStatus": manifest.approval_status,
        "lifecycleStatus": manifest.lifecycle_status,
        "effectiveFrom": manifest.effective_from.isoformat(),
        "effectiveTo": (
            None if manifest.effective_to is None else manifest.effective_to.isoformat()
        ),
        "reviewAsOf": as_of.isoformat(),
        "effectiveOnReviewDate": is_effective(manifest, as_of),
        "reviewOnly": True,
    }


def _write_atomic(path: Path, rows: list[dict[str, object]]) -> None:
    temporary_path: Path | None = None
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
        )
        temporary_path = Path(temporary_name)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            for row in rows:
                json.dump(row, stream, ensure_ascii=False, separators=(",", ":"))
                stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, path)
        temporary_path = None
    except (OSError, TypeError, ValueError):
        raise KnowledgeContractError("OUTPUT_WRITE_FAILED") from None
    finally:
        if temporary_path is not None:
            try:
                temporary_path.unlink(missing_ok=True)
            except OSError:
                pass
