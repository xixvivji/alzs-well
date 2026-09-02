from __future__ import annotations

import csv
import json
from collections import Counter
from pathlib import Path
from typing import Any

from app.evaluation.models import EvaluationChunk
from app.evaluation.review import ReviewCandidate


PASS_VERDICTS = {"PASS_TOP_1", "PASS_NO_RESULTS"}


def triage_ranking_review(
    candidates: tuple[ReviewCandidate, ...],
    corpus: tuple[EvaluationChunk, ...],
    ranking_payload: dict[str, Any],
) -> tuple[dict[str, str], ...]:
    candidate_by_id = {candidate.candidate_id: candidate for candidate in candidates}
    chunk_by_id = {chunk.chunk_id: chunk for chunk in corpus}
    rows: list[dict[str, str]] = []
    for review in ranking_payload["cases"]:
        verdict = str(review["verdict"])
        if verdict in PASS_VERDICTS:
            continue
        candidate_id = str(review["query_id"])
        candidate = candidate_by_id[candidate_id]
        expected = [chunk_by_id[chunk_id] for chunk_id in candidate.relevant_chunk_ids]
        results = review["results"]
        top = results[0] if results else None
        classification = _classification(verdict, expected, top)
        seed_id = next(
            tag.removeprefix("seed:")
            for tag in candidate.tags
            if tag.startswith("seed:")
        )
        rows.append(
            {
                "candidateId": candidate_id,
                "seedId": seed_id,
                "verdict": verdict,
                "firstRelevantRank": str(review["first_relevant_rank"] or ""),
                "expectedChunkIds": "|".join(candidate.relevant_chunk_ids),
                "expectedDocument": "|".join(chunk.document_id for chunk in expected),
                "expectedHeading": "|".join(chunk.heading for chunk in expected),
                "topChunkId": "" if top is None else str(top["chunk_id"]),
                "topDocument": "" if top is None else str(top["document_id"]),
                "topHeading": "" if top is None else str(top["heading"]),
                "classification": classification,
                "recommendedAction": _recommended_action(classification),
                "reviewDecision": "PENDING",
                "reviewComment": "독립 사람 검수 필요; AI 기술 분류는 승인 판단이 아님",
            }
        )
    return tuple(rows)


def write_triage_outputs(
    output_csv: Path,
    output_json: Path,
    output_markdown: Path,
    rows: tuple[dict[str, str], ...],
) -> None:
    if not rows:
        raise ValueError("triage output requires at least one non-pass case")
    output_csv.parent.mkdir(parents=True, exist_ok=True)
    with output_csv.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=tuple(rows[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    counts = dict(sorted(Counter(row["classification"] for row in rows).items()))
    payload = {
        "triageVersion": "independent-review-ai-triage-v1",
        "humanReviewCompleted": False,
        "officialPerformanceClaim": False,
        "caseCount": len(rows),
        "classificationCounts": counts,
        "candidateIds": [row["candidateId"] for row in rows],
    }
    output_json.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    lines = [
        "# RAG 우선 검수 36건 AI 기술 분류 v1",
        "",
        "이 보고서는 독립 사람 검수를 돕는 기술 분류이며 승인 결과가 아니다.",
        "원본 후보와 이 표의 `reviewDecision`은 모두 `PENDING`이다.",
        "",
        "## 분류 요약",
        "",
        *(f"- `{name}`: {count}건" for name, count in counts.items()),
        "",
        "## 검수 대상",
        "",
        "| ID | Seed | Verdict | Rank | Classification | Recommended action |",
        "|---|---|---|---:|---|---|",
    ]
    lines.extend(
        f"| {row['candidateId']} | {row['seedId']} | {row['verdict']} | "
        f"{row['firstRelevantRank'] or '-'} | {row['classification']} | "
        f"{row['recommendedAction']} |"
        for row in rows
    )
    output_markdown.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _classification(
    verdict: str,
    expected: list[EvaluationChunk],
    top: dict[str, Any] | None,
) -> str:
    if verdict == "REVIEW_TOP_2_OR_3":
        if top is not None and any(
            top["document_id"] == chunk.document_id
            and top["heading"] == chunk.heading
            for chunk in expected
        ):
            return "SAME_DOCUMENT_HEADING_REVIEW_REQUIRED"
        return "TOP3_RELEVANT_REVIEW"
    if top is not None and any(chunk.document_type == "REGULATION" for chunk in expected):
        if str(top["document_id"]).startswith("DOC-LAW-"):
            return "AUTHORITY_OVER_SPECIFIC_REGULATION"
    if top is not None and any("정의" in chunk.heading for chunk in expected):
        if "정의" not in str(top["heading"]):
            return "SECTION_INTENT_MISMATCH"
    return "CONTEXT_SENSITIVITY"


def _recommended_action(classification: str) -> str:
    if classification == "SAME_DOCUMENT_HEADING_REVIEW_REQUIRED":
        return "HUMAN_COMPARE_CHUNK_CONTENT"
    if classification == "TOP3_RELEVANT_REVIEW":
        return "HUMAN_REVIEW_KEEP_TOP3"
    return "HUMAN_CONFIRM_BEFORE_RANKING_CHANGE"
