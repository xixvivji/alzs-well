from __future__ import annotations

import csv
import json
import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any

from app.evaluation.models import EvaluationChunk


REVIEW_DECISIONS = {"PENDING", "ACCEPTED", "REJECTED", "AMBIGUOUS"}


@dataclass(frozen=True, slots=True)
class ReviewCandidate:
    candidate_id: str
    query: str
    principal_roles: tuple[str, ...]
    requester_audiences: tuple[str, ...]
    as_of: date
    relevant_chunk_ids: tuple[str, ...]
    expected_action: str
    tags: tuple[str, ...]
    source_kind: str
    review_decision: str
    review_comment: str


def load_review_candidates(path: Path) -> tuple[ReviewCandidate, ...]:
    candidates = tuple(_candidate(payload) for payload in _read_jsonl(path))
    identifiers = [candidate.candidate_id for candidate in candidates]
    if not candidates or len(identifiers) != len(set(identifiers)):
        raise ValueError("review candidates must have unique identifiers")
    return candidates


def validate_review_candidates(
    candidates: tuple[ReviewCandidate, ...], corpus: tuple[EvaluationChunk, ...]
) -> None:
    known_chunk_ids = {chunk.chunk_id for chunk in corpus}
    for candidate in candidates:
        if candidate.expected_action not in {"ANSWER", "ABSTAIN"}:
            raise ValueError(f"{candidate.candidate_id}: unsupported expected action")
        if candidate.review_decision not in REVIEW_DECISIONS:
            raise ValueError(f"{candidate.candidate_id}: unsupported review decision")
        if candidate.expected_action == "ANSWER" and not candidate.relevant_chunk_ids:
            raise ValueError(f"{candidate.candidate_id}: answer requires relevant chunks")
        if candidate.expected_action == "ABSTAIN" and candidate.relevant_chunk_ids:
            raise ValueError(f"{candidate.candidate_id}: abstain cannot have relevant chunks")
        if not set(candidate.relevant_chunk_ids) <= known_chunk_ids:
            raise ValueError(f"{candidate.candidate_id}: unknown relevant chunk")


def write_review_csv(
    path: Path,
    candidates: tuple[ReviewCandidate, ...],
    corpus: tuple[EvaluationChunk, ...],
) -> None:
    chunks = {chunk.chunk_id: chunk for chunk in corpus}
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(
            stream, fieldnames=_review_fields(), lineterminator="\n"
        )
        writer.writeheader()
        for candidate in candidates:
            writer.writerow(_review_row(candidate, chunks))


def finalize_review_csv(
    input_csv: Path,
    output_jsonl: Path,
    candidates: tuple[ReviewCandidate, ...],
    corpus: tuple[EvaluationChunk, ...],
) -> int:
    validate_review_candidates(candidates, corpus)
    chunks = {chunk.chunk_id: chunk for chunk in corpus}
    expected_rows = {
        candidate.candidate_id: _review_row(candidate, chunks) for candidate in candidates
    }
    accepted: list[dict[str, object]] = []
    with input_csv.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if tuple(reader.fieldnames or ()) != _review_fields():
            raise ValueError("review CSV columns do not match the contract")
        rows = list(reader)
        identifiers = [row["candidateId"] for row in rows]
        if len(identifiers) != len(set(identifiers)) or set(identifiers) != set(expected_rows):
            raise ValueError("review CSV candidate set does not match the source")
        for row in rows:
            expected = expected_rows[row["candidateId"]]
            for field in _review_fields():
                if field not in {"reviewDecision", "reviewComment"} and row[field] != expected[field]:
                    raise ValueError(f"{row['candidateId']}: immutable review field changed")
            decision = row["reviewDecision"].strip().upper()
            if decision not in REVIEW_DECISIONS:
                raise ValueError(f"{row['candidateId']}: unsupported review decision")
            if decision != "ACCEPTED":
                continue
            relevant = _split(expected["relevantChunkIds"])
            expected_action = expected["expectedAction"]
            accepted.append({
                "queryId": expected["candidateId"],
                "query": expected["query"],
                "principalRoles": list(_split(expected["principalRoles"])),
                "requesterAudiences": list(_split(expected["requesterAudiences"])),
                "asOf": expected["asOf"],
                "relevantChunkIds": list(relevant),
                "expectNoResults": expected_action == "ABSTAIN",
                "tags": list(_split(row["tags"])),
            })
    if not accepted:
        raise ValueError("at least one accepted review candidate is required")
    output_jsonl.parent.mkdir(parents=True, exist_ok=True)
    output_jsonl.write_text(
        "".join(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n"
                for payload in accepted),
        encoding="utf-8",
    )
    return len(accepted)


def _candidate(payload: dict[str, Any]) -> ReviewCandidate:
    return ReviewCandidate(
        candidate_id=str(payload["candidateId"]),
        query=str(payload["query"]),
        principal_roles=tuple(str(value) for value in payload["principalRoles"]),
        requester_audiences=tuple(str(value) for value in payload["requesterAudiences"]),
        as_of=date.fromisoformat(str(payload["asOf"])),
        relevant_chunk_ids=tuple(str(value) for value in payload["relevantChunkIds"]),
        expected_action=str(payload["expectedAction"]),
        tags=tuple(str(value) for value in payload["tags"]),
        source_kind=str(payload["sourceKind"]),
        review_decision=str(payload.get("reviewDecision", "PENDING")),
        review_comment=str(payload.get("reviewComment", "")),
    )


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


def _split(value: str) -> tuple[str, ...]:
    return tuple(part.strip() for part in value.split("|") if part.strip())


def _review_row(
    candidate: ReviewCandidate, chunks: dict[str, EvaluationChunk]
) -> dict[str, str]:
    evidence = [chunks[chunk_id] for chunk_id in candidate.relevant_chunk_ids]
    return {
        "candidateId": candidate.candidate_id,
        "query": candidate.query,
        "expectedAction": candidate.expected_action,
        "relevantChunkIds": "|".join(candidate.relevant_chunk_ids),
        "evidenceHeading": " | ".join(chunk.heading for chunk in evidence),
        "evidenceExcerpt": " | ".join(
            _evidence_excerpt(
                chunk.content, f"{candidate.query} {' '.join(candidate.tags)}"
            )
            for chunk in evidence
        ),
        "principalRoles": "|".join(candidate.principal_roles),
        "requesterAudiences": "|".join(candidate.requester_audiences),
        "asOf": candidate.as_of.isoformat(),
        "tags": "|".join(candidate.tags),
        "sourceKind": candidate.source_kind,
        "reviewDecision": candidate.review_decision,
        "reviewComment": candidate.review_comment,
    }


def _evidence_excerpt(content: str, query: str, *, limit: int = 360) -> str:
    """Return a deterministic, query-focused review excerpt.

    Prefix-only excerpts hid the actual answer when a long chunk began with a table,
    navigation, or the preceding provision. Paragraph scoring keeps the review file
    compact while putting the most query-relevant evidence in front of the reviewer.
    """
    normalized = " ".join(content.split())
    if len(normalized) <= limit:
        return normalized

    paragraphs = [" ".join(value.split()) for value in re.split(r"\n\s*\n", content)]
    paragraphs = [value for value in paragraphs if value]
    query_grams = _search_grams(query)
    ranked = sorted(
        enumerate(paragraphs),
        key=lambda item: (
            len(query_grams & _search_grams(item[1])),
            -item[0],
            -abs(len(item[1]) - limit),
        ),
        reverse=True,
    )
    best_index = ranked[0][0]
    selected = paragraphs[best_index]
    for distance in range(1, len(paragraphs)):
        for index in (best_index + distance, best_index - distance):
            if index < 0 or index >= len(paragraphs):
                continue
            candidate = f"{selected}\n\n{paragraphs[index]}"
            if len(candidate) > limit:
                continue
            selected = candidate
        if len(selected) >= limit * 0.75:
            break
    return selected[:limit].rstrip()


def _search_grams(value: str) -> set[str]:
    compact = re.sub(r"[^0-9A-Za-z가-힣]+", "", value).lower()
    return {compact[index:index + 2] for index in range(max(0, len(compact) - 1))}


def _review_fields() -> tuple[str, ...]:
    return (
        "candidateId", "query", "expectedAction", "relevantChunkIds",
        "evidenceHeading", "evidenceExcerpt", "principalRoles", "requesterAudiences",
        "asOf", "tags", "sourceKind", "reviewDecision", "reviewComment",
    )
