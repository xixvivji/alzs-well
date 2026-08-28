from __future__ import annotations

import csv
import json
from pathlib import Path

import pytest

from app.evaluation.models import load_corpus
from app.evaluation.review import (
    _evidence_excerpt,
    finalize_review_csv,
    load_review_candidates,
    validate_review_candidates,
    write_provisional_benchmark_dataset,
    write_review_csv,
)
from app.evaluation.review_cli import main


EVALUATION = Path(__file__).parents[1] / "evaluation"
CORPUS = EVALUATION / "datasets" / "retrieval-corpus-v1.jsonl"
CANDIDATES = EVALUATION / "reviews" / "retrieval-review-candidates-v1.jsonl"
REVIEW_CSV = EVALUATION / "reviews" / "retrieval-review-v1.csv"


def test_evidence_excerpt_focuses_on_query_relevant_paragraph() -> None:
    content = (
        "메뉴와 상품 목록입니다. " * 30
        + "\n\n서비스 절차는 본인 의사 확인, 지정인 동의, 안내 메시지 전송 순서입니다."
    )

    excerpt = _evidence_excerpt(content, "지정인 동의 후 메시지는 언제 전송하나요?")

    assert "지정인 동의" in excerpt
    assert "안내 메시지 전송" in excerpt
    assert len(excerpt) <= 720


def test_committed_review_pack_contains_fifty_pending_candidates(tmp_path: Path) -> None:
    corpus = load_corpus(CORPUS)
    candidates = load_review_candidates(CANDIDATES)
    output = tmp_path / "review.csv"

    validate_review_candidates(candidates, corpus)
    write_review_csv(output, candidates, corpus)
    rows = _rows(output)

    assert len(candidates) == 50
    assert len(rows) == 50
    assert {row["reviewDecision"] for row in rows} == {"PENDING"}
    assert rows[0]["evidenceExcerpt"]
    assert rows[-1]["expectedAction"] == "ABSTAIN"
    assert rows[-1]["evidenceExcerpt"] == ""


def test_finalize_promotes_only_accepted_rows(tmp_path: Path) -> None:
    corpus = load_corpus(CORPUS)
    candidates = load_review_candidates(CANDIDATES)
    review_csv = tmp_path / "review.csv"
    output = tmp_path / "accepted.jsonl"
    write_review_csv(review_csv, candidates, corpus)
    rows = _rows(review_csv)
    rows[0]["reviewDecision"] = "ACCEPTED"
    rows[1]["reviewDecision"] = "REJECTED"
    _write_rows(review_csv, rows)

    accepted_count = finalize_review_csv(review_csv, output, candidates, corpus)
    payload = json.loads(output.read_text(encoding="utf-8").strip())

    assert accepted_count == 1
    assert payload["queryId"] == "RC-001"
    assert payload["expectNoResults"] is False


def test_committed_review_csv_finalizes_second_pass_decisions(tmp_path: Path) -> None:
    corpus = load_corpus(CORPUS)
    candidates = load_review_candidates(CANDIDATES)
    output = tmp_path / "reviewed.jsonl"
    rows = _rows(REVIEW_CSV)

    decisions = [row["reviewDecision"] for row in rows]
    assert decisions.count("ACCEPTED") == 46
    assert decisions.count("AMBIGUOUS") == 4
    assert decisions.count("REJECTED") == 0
    assert decisions.count("PENDING") == 0

    accepted_count = finalize_review_csv(REVIEW_CSV, output, candidates, corpus)
    payloads = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]

    assert accepted_count == 46
    assert len(payloads) == 46
    assert sum(not payload["expectNoResults"] for payload in payloads) == 39
    assert sum(payload["expectNoResults"] for payload in payloads) == 7
    assert {"RC-013", "RC-044", "RC-047", "RC-048"}.isdisjoint(
        payload["queryId"] for payload in payloads
    )


def test_finalize_requires_an_accepted_candidate(tmp_path: Path) -> None:
    review_csv = tmp_path / "review.csv"
    write_review_csv(review_csv, load_review_candidates(CANDIDATES), load_corpus(CORPUS))

    with pytest.raises(ValueError, match="at least one accepted"):
        finalize_review_csv(
            review_csv, tmp_path / "accepted.jsonl",
            load_review_candidates(CANDIDATES), load_corpus(CORPUS),
        )


def test_finalize_rejects_changes_outside_review_columns(tmp_path: Path) -> None:
    corpus = load_corpus(CORPUS)
    candidates = load_review_candidates(CANDIDATES)
    review_csv = tmp_path / "review.csv"
    write_review_csv(review_csv, candidates, corpus)
    rows = _rows(review_csv)
    rows[0]["query"] = "변조된 질문"
    rows[0]["reviewDecision"] = "ACCEPTED"
    _write_rows(review_csv, rows)

    with pytest.raises(ValueError, match="immutable review field changed"):
        finalize_review_csv(review_csv, tmp_path / "accepted.jsonl", candidates, corpus)


def test_review_cli_prepares_csv_and_finalizes_accepted_case(tmp_path: Path) -> None:
    review_csv = tmp_path / "review.csv"
    assert main([
        "prepare", "--corpus", str(CORPUS), "--candidates", str(CANDIDATES),
        "--output-csv", str(review_csv),
    ]) == 0
    rows = _rows(review_csv)
    rows[40]["reviewDecision"] = "ACCEPTED"
    _write_rows(review_csv, rows)
    output = tmp_path / "accepted.jsonl"

    assert main([
        "finalize", "--corpus", str(CORPUS), "--candidates", str(CANDIDATES),
        "--input-csv", str(review_csv), "--output-jsonl", str(output),
    ]) == 0
    assert '"expectNoResults":true' in output.read_text(encoding="utf-8")


def test_provisional_benchmark_selects_cases_supported_by_corpus(
    tmp_path: Path,
) -> None:
    corpus = load_corpus(CORPUS)
    candidates = load_review_candidates(CANDIDATES)
    supported_ids = {chunk.chunk_id for chunk in corpus[:2]}
    reduced_corpus = tuple(chunk for chunk in corpus if chunk.chunk_id in supported_ids)
    output = tmp_path / "benchmark.jsonl"

    case_count = write_provisional_benchmark_dataset(output, candidates, reduced_corpus)
    payloads = [json.loads(line) for line in output.read_text().splitlines()]

    assert case_count == len(payloads)
    assert any(payload["relevantChunkIds"] for payload in payloads)
    assert all(
        set(payload["relevantChunkIds"]) <= supported_ids for payload in payloads
    )
    assert any(payload["expectNoResults"] for payload in payloads)


def test_review_cli_builds_provisional_benchmark(tmp_path: Path) -> None:
    output = tmp_path / "benchmark.jsonl"

    assert main([
        "benchmark", "--corpus", str(CORPUS), "--candidates", str(CANDIDATES),
        "--output-jsonl", str(output),
    ]) == 0
    assert output.is_file()


def _rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def _write_rows(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=tuple(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
