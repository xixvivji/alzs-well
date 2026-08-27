from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unicodedata
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any

from app.domain.manifest import KnowledgeManifest, ensure_ingestion_eligible
from app.errors import KnowledgeContractError
from app.ingestion.chunker import (
    CHUNKER_VERSION,
    DEFAULT_MAX_CHARS,
    PDF_CHUNKER_VERSION,
    canonical_chunk_id,
)
from app.ingestion.manifest_loader import load_and_validate_manifest


@dataclass(frozen=True, slots=True)
class CorpusBuildResult:
    output_path: Path
    document_count: int
    chunk_count: int


def build_evaluation_corpus(
    repository_root: Path,
    manifest_paths: tuple[str, ...],
    *,
    as_of: date,
) -> CorpusBuildResult:
    if not manifest_paths or len(manifest_paths) != len(set(manifest_paths)):
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID")

    rows: list[dict[str, object]] = []
    chunk_ids: set[str] = set()
    for manifest_path in manifest_paths:
        manifest = load_and_validate_manifest(repository_root, manifest_path)
        ensure_ingestion_eligible(manifest, as_of=as_of)
        document_rows = _load_document_chunks(repository_root, manifest)
        for row in document_rows:
            chunk_id = str(row["chunkId"])
            if chunk_id in chunk_ids:
                raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")
            chunk_ids.add(chunk_id)
            rows.append(_evaluation_row(row, manifest))

    output_path = (
        repository_root
        / "ai-service"
        / "data"
        / "derived"
        / "evaluation"
        / f"retrieval-official-corpus-{as_of.isoformat()}.jsonl"
    )
    _write_atomic(output_path, rows)
    return CorpusBuildResult(output_path, len(manifest_paths), len(rows))


def _load_document_chunks(
    repository_root: Path, manifest: KnowledgeManifest
) -> tuple[dict[str, Any], ...]:
    chunk_path = (
        repository_root
        / "ai-service"
        / "data"
        / "derived"
        / "chunks"
        / f"{manifest.document_id}-{manifest.version_label}.jsonl"
    )
    try:
        lines = chunk_path.read_text(encoding="utf-8").splitlines()
        rows = tuple(json.loads(line) for line in lines if line.strip())
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED") from None
    if not rows or not all(isinstance(row, dict) for row in rows):
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")
    for expected_order, row in enumerate(rows, start=1):
        _validate_chunk(row, manifest, expected_order)
    return rows


def _validate_chunk(
    row: dict[str, Any], manifest: KnowledgeManifest, expected_order: int
) -> None:
    required = {
        "chunkId", "documentId", "versionLabel", "heading", "sectionPath",
        "chunkOrder", "text", "textHash", "sourceHash", "chunkerVersion",
    }
    if not required <= row.keys():
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")
    section_path = row["sectionPath"]
    if (
        row["documentId"] != manifest.document_id
        or row["versionLabel"] != manifest.version_label
        or row["sourceHash"] != manifest.source_hash
        or row["chunkOrder"] != expected_order
        or not isinstance(section_path, list)
        or not all(isinstance(value, str) for value in section_path)
        or not isinstance(row["heading"], str)
        or not row["heading"]
        or not isinstance(row["text"], str)
        or not row["text"]
        or len(row["text"]) > DEFAULT_MAX_CHARS
        or row["chunkerVersion"] not in {CHUNKER_VERSION, PDF_CHUNKER_VERSION}
    ):
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")
    normalized_text = unicodedata.normalize("NFC", row["text"])
    text_hash = "sha256:" + hashlib.sha256(normalized_text.encode("utf-8")).hexdigest()
    expected_chunk_id, _ = canonical_chunk_id(
        manifest.document_id,
        manifest.version_label,
        section_path,
        expected_order,
        text_hash,
        str(row["chunkerVersion"]),
    )
    if (
        row["text"] != normalized_text
        or row["textHash"] != text_hash
        or row["chunkId"] != expected_chunk_id
    ):
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")


def _evaluation_row(row: dict[str, Any], manifest: KnowledgeManifest) -> dict[str, object]:
    return {
        "chunkId": row["chunkId"],
        "documentId": manifest.document_id,
        "documentType": manifest.document_type,
        "heading": str(row["heading"]),
        "sectionPath": list(row["sectionPath"]),
        "content": row["text"],
        "allowedRoles": list(manifest.allowed_roles),
        "audience": manifest.audience,
        "approvalStatus": manifest.approval_status,
        "lifecycleStatus": manifest.lifecycle_status,
        "effectiveFrom": manifest.effective_from.isoformat(),
        "effectiveTo": (
            None if manifest.effective_to is None else manifest.effective_to.isoformat()
        ),
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
