from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any


@dataclass(frozen=True, slots=True)
class EvaluationChunk:
    chunk_id: str
    document_id: str
    heading: str
    section_path: tuple[str, ...]
    content: str
    allowed_roles: tuple[str, ...]
    audience: str
    approval_status: str
    lifecycle_status: str
    effective_from: date
    effective_to: date | None

    def searchable_text(self) -> str:
        return " ".join((*self.section_path, self.heading, self.content))


@dataclass(frozen=True, slots=True)
class EvaluationCase:
    query_id: str
    query: str
    principal_roles: tuple[str, ...]
    requester_audiences: tuple[str, ...]
    as_of: date
    relevant_chunk_ids: frozenset[str]
    expect_no_results: bool
    tags: tuple[str, ...]


def load_corpus(path: Path) -> tuple[EvaluationChunk, ...]:
    return tuple(_chunk(payload) for payload in _read_jsonl(path))


def load_cases(path: Path) -> tuple[EvaluationCase, ...]:
    return tuple(_case(payload) for payload in _read_jsonl(path))


def validate_dataset(
    corpus: tuple[EvaluationChunk, ...], cases: tuple[EvaluationCase, ...]
) -> None:
    chunk_ids = [chunk.chunk_id for chunk in corpus]
    if not corpus or len(chunk_ids) != len(set(chunk_ids)):
        raise ValueError("corpus must contain unique chunks")
    query_ids = [case.query_id for case in cases]
    if not cases or len(query_ids) != len(set(query_ids)):
        raise ValueError("dataset must contain unique queries")
    known = set(chunk_ids)
    for case in cases:
        if case.expect_no_results != (not case.relevant_chunk_ids):
            raise ValueError(f"{case.query_id}: no-answer contract is inconsistent")
        if not case.relevant_chunk_ids <= known:
            raise ValueError(f"{case.query_id}: unknown relevant chunk")


def _read_jsonl(path: Path) -> tuple[dict[str, Any], ...]:
    payloads: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        payload = json.loads(line)
        if not isinstance(payload, dict):
            raise ValueError(f"{path}:{line_number}: object required")
        payloads.append(payload)
    return tuple(payloads)


def _chunk(payload: dict[str, Any]) -> EvaluationChunk:
    return EvaluationChunk(
        chunk_id=str(payload["chunkId"]),
        document_id=str(payload["documentId"]),
        heading=str(payload["heading"]),
        section_path=tuple(str(value) for value in payload["sectionPath"]),
        content=str(payload["content"]),
        allowed_roles=tuple(str(value) for value in payload["allowedRoles"]),
        audience=str(payload["audience"]),
        approval_status=str(payload["approvalStatus"]),
        lifecycle_status=str(payload["lifecycleStatus"]),
        effective_from=date.fromisoformat(str(payload["effectiveFrom"])),
        effective_to=(
            None if payload["effectiveTo"] is None else date.fromisoformat(payload["effectiveTo"])
        ),
    )


def _case(payload: dict[str, Any]) -> EvaluationCase:
    return EvaluationCase(
        query_id=str(payload["queryId"]),
        query=str(payload["query"]),
        principal_roles=tuple(str(value) for value in payload["principalRoles"]),
        requester_audiences=tuple(str(value) for value in payload["requesterAudiences"]),
        as_of=date.fromisoformat(str(payload["asOf"])),
        relevant_chunk_ids=frozenset(str(value) for value in payload["relevantChunkIds"]),
        expect_no_results=bool(payload["expectNoResults"]),
        tags=tuple(str(value) for value in payload["tags"]),
    )
