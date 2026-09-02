from __future__ import annotations

import csv
import json
from datetime import date
from pathlib import Path

import pytest

from app.evaluation.models import EvaluationChunk
from app.evaluation.review import ReviewCandidate, validate_review_candidates
from app.evaluation.triage import triage_ranking_review, write_triage_outputs
from app.evaluation.triage_cli import main


def _chunk(
    chunk_id: str,
    document_id: str,
    document_type: str,
    heading: str,
) -> EvaluationChunk:
    return EvaluationChunk(
        chunk_id=chunk_id,
        document_id=document_id,
        document_type=document_type,
        heading=heading,
        section_path=(heading,),
        content="검수 근거",
        allowed_roles=("CUSTOMER",),
        audience="CUSTOMER",
        approval_status="APPROVED",
        lifecycle_status="ACTIVE",
        effective_from=date(2026, 1, 1),
        effective_to=None,
    )


def _candidate(candidate_id: str, chunk_id: str, seed_id: str) -> ReviewCandidate:
    return ReviewCandidate(
        candidate_id=candidate_id,
        query="검수 질문",
        principal_roles=("CUSTOMER",),
        requester_audiences=("CUSTOMER",),
        as_of=date(2026, 9, 1),
        relevant_chunk_ids=(chunk_id,),
        expected_action="ANSWER",
        tags=(f"seed:{seed_id}",),
        source_kind="MACHINE_AUTHORED_REVIEW_CANDIDATE",
        review_decision="PENDING",
        review_comment="",
    )


def _review(
    candidate_id: str,
    verdict: str,
    document_id: str,
    heading: str,
    rank: int | None,
) -> dict[str, object]:
    return {
        "query_id": candidate_id,
        "verdict": verdict,
        "first_relevant_rank": rank,
        "results": [{"document_id": document_id, "heading": heading}],
    }


def test_triage_classifies_all_non_pass_patterns() -> None:
    corpus = (
        _chunk("C1", "DOC-A", "GUIDE", "같은 절"),
        _chunk("C2", "DOC-B", "GUIDE", "다른 절"),
        _chunk("C3", "DOC-C", "GUIDE", "용어의 정의"),
        _chunk("C4", "DOC-REG-1", "REGULATION", "신청 절차"),
        _chunk("C5", "DOC-D", "GUIDE", "맥락"),
    )
    candidates = tuple(
        _candidate(f"IRC-{index}", chunk.chunk_id, f"ORC-{index}")
        for index, chunk in enumerate(corpus, start=1)
    )
    ranking = {
        "cases": [
            _review("IRC-1", "REVIEW_TOP_2_OR_3", "DOC-A", "같은 절", 2),
            _review("IRC-2", "REVIEW_TOP_2_OR_3", "DOC-X", "다른 근거", 3),
            _review("IRC-3", "FAIL_BELOW_TOP_3", "DOC-X", "적용 범위", None),
            _review("IRC-4", "FAIL_BELOW_TOP_3", "DOC-LAW-1", "법률", None),
            _review("IRC-5", "FAIL_BELOW_TOP_3", "DOC-X", "기타", None),
            _review("IRC-pass", "PASS_TOP_1", "DOC-X", "기타", 1),
        ]
    }

    rows = triage_ranking_review(candidates, corpus, ranking)

    assert [row["classification"] for row in rows] == [
        "DUPLICATE_CHUNK_NEAR_MATCH",
        "TOP3_RELEVANT_REVIEW",
        "SECTION_INTENT_MISMATCH",
        "AUTHORITY_OVER_SPECIFIC_REGULATION",
        "CONTEXT_SENSITIVITY",
    ]
    assert all(row["reviewDecision"] == "PENDING" for row in rows)
    assert all("독립 사람 검수" in row["reviewComment"] for row in rows)


def test_writes_machine_triage_outputs_without_approval_claim(tmp_path: Path) -> None:
    rows = (
        {
            "candidateId": "IRC-1",
            "seedId": "ORC-1",
            "verdict": "FAIL_BELOW_TOP_3",
            "firstRelevantRank": "",
            "expectedDocument": "DOC-A",
            "expectedHeading": "근거",
            "topDocument": "DOC-B",
            "topHeading": "다른 근거",
            "classification": "CONTEXT_SENSITIVITY",
            "recommendedAction": "HUMAN_CONFIRM_BEFORE_RANKING_CHANGE",
            "reviewDecision": "PENDING",
            "reviewComment": "독립 사람 검수 필요; AI 기술 분류는 승인 판단이 아님",
        },
    )
    csv_path = tmp_path / "triage.csv"
    json_path = tmp_path / "triage.json"
    markdown_path = tmp_path / "triage.md"

    write_triage_outputs(csv_path, json_path, markdown_path, rows)

    with csv_path.open(encoding="utf-8-sig", newline="") as stream:
        assert list(csv.DictReader(stream))[0]["reviewDecision"] == "PENDING"
    payload = json.loads(json_path.read_text(encoding="utf-8"))
    assert payload["humanReviewCompleted"] is False
    assert payload["officialPerformanceClaim"] is False
    assert payload["classificationCounts"] == {"CONTEXT_SENSITIVITY": 1}
    assert "승인 결과가 아니다" in markdown_path.read_text(encoding="utf-8")

    with pytest.raises(ValueError, match="at least one non-pass case"):
        write_triage_outputs(csv_path, json_path, markdown_path, ())


def test_cli_writes_outputs(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    corpus = (_chunk("C1", "DOC-A", "GUIDE", "근거"),)
    candidates = (_candidate("IRC-1", "C1", "ORC-1"),)
    ranking_path = tmp_path / "ranking.json"
    ranking_path.write_text(
        json.dumps(
            {
                "cases": [
                    _review(
                        "IRC-1", "REVIEW_TOP_2_OR_3", "DOC-A", "근거", 2
                    )
                ]
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr("app.evaluation.triage_cli.load_corpus", lambda _: corpus)
    monkeypatch.setattr(
        "app.evaluation.triage_cli.load_review_candidates", lambda _: candidates
    )
    csv_path = tmp_path / "out.csv"
    json_path = tmp_path / "out.json"
    markdown_path = tmp_path / "out.md"

    result = main(
        [
            "--corpus",
            str(tmp_path / "corpus.jsonl"),
            "--candidates",
            str(tmp_path / "candidates.jsonl"),
            "--ranking-json",
            str(ranking_path),
            "--output-csv",
            str(csv_path),
            "--output-json",
            str(json_path),
            "--output-markdown",
            str(markdown_path),
        ]
    )

    assert result == 0
    assert csv_path.exists() and json_path.exists() and markdown_path.exists()


@pytest.mark.parametrize(
    ("expected_action", "relevant_chunk_ids", "review_decision", "message"),
    [
        ("EXECUTE", ("C1",), "PENDING", "unsupported expected action"),
        ("ANSWER", ("C1",), "DONE", "unsupported review decision"),
        ("ANSWER", (), "PENDING", "answer requires relevant chunks"),
        ("ABSTAIN", ("C1",), "PENDING", "abstain cannot have relevant chunks"),
        ("ANSWER", ("UNKNOWN",), "PENDING", "unknown relevant chunk"),
    ],
)
def test_triage_input_validation_fails_closed(
    expected_action: str,
    relevant_chunk_ids: tuple[str, ...],
    review_decision: str,
    message: str,
) -> None:
    corpus = (_chunk("C1", "DOC-A", "GUIDE", "근거"),)
    candidate = _candidate("IRC-1", "C1", "ORC-1")
    invalid = ReviewCandidate(
        candidate_id=candidate.candidate_id,
        query=candidate.query,
        principal_roles=candidate.principal_roles,
        requester_audiences=candidate.requester_audiences,
        as_of=candidate.as_of,
        relevant_chunk_ids=relevant_chunk_ids,
        expected_action=expected_action,
        tags=candidate.tags,
        source_kind=candidate.source_kind,
        review_decision=review_decision,
        review_comment=candidate.review_comment,
    )

    with pytest.raises(ValueError, match=message):
        validate_review_candidates((invalid,), corpus)


def test_committed_triage_keeps_review_boundary(repo_root: Path) -> None:
    service_root = repo_root / "ai-service"
    report = json.loads(
        (service_root / "evaluation/independent-review-ai-triage-v1.json").read_text(
            encoding="utf-8"
        )
    )
    with (
        service_root / "evaluation/reviews/independent-review-ai-triage-v1.csv"
    ).open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))

    assert report["caseCount"] == 36
    assert report["candidateIds"] == [row["candidateId"] for row in rows]
    assert all(row["reviewDecision"] == "PENDING" for row in rows)
    assert all("독립 사람 검수" in row["reviewComment"] for row in rows)
    assert report["classificationCounts"] == {
        "AUTHORITY_OVER_SPECIFIC_REGULATION": 5,
        "CONTEXT_SENSITIVITY": 1,
        "DUPLICATE_CHUNK_NEAR_MATCH": 12,
        "SECTION_INTENT_MISMATCH": 4,
        "TOP3_RELEVANT_REVIEW": 14,
    }
    assert report["humanReviewCompleted"] is False
    assert report["officialPerformanceClaim"] is False
