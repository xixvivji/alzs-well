import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

from app.evaluation.models import load_cases
from app.evaluation.review import load_review_candidates


def test_expanded_rag_pack_stays_pending_and_traceable(repo_root: Path) -> None:
    candidates = load_review_candidates(
        repo_root
        / "ai-service/evaluation/reviews/independent-review-candidates-v1.jsonl"
    )
    official = load_cases(
        repo_root
        / "ai-service/evaluation/datasets/official-operational-golden-v1.jsonl"
    )
    official_answers = {
        case.query_id: case for case in official if not case.expect_no_results
    }
    official_queries = {case.query for case in official}
    answer = tuple(
        candidate for candidate in candidates if candidate.expected_action == "ANSWER"
    )
    abstain = tuple(
        candidate for candidate in candidates if candidate.expected_action == "ABSTAIN"
    )

    assert len(candidates) == 150
    assert len(answer) == 105
    assert len(abstain) == 45
    assert [candidate.candidate_id for candidate in candidates] == [
        f"IRC-{index:03d}" for index in range(1, 151)
    ]
    assert all(candidate.review_decision == "PENDING" for candidate in candidates)
    assert all(
        candidate.source_kind == "MACHINE_AUTHORED_REVIEW_CANDIDATE"
        for candidate in candidates
    )
    assert not ({candidate.query for candidate in candidates} & official_queries)
    assert len({candidate.query for candidate in candidates}) == 150

    style_counts: Counter[str] = Counter()
    seed_counts: Counter[str] = Counter()
    for candidate in answer:
        style = next(
            tag
            for tag in candidate.tags
            if tag in {"고객안내", "행원검토", "규정확인", "쉬운말", "핵심조건"}
        )
        seed_id = next(
            tag.removeprefix("seed:")
            for tag in candidate.tags
            if tag.startswith("seed:")
        )
        seed = official_answers[seed_id]
        assert frozenset(candidate.relevant_chunk_ids) == seed.relevant_chunk_ids
        assert candidate.principal_roles == seed.principal_roles
        assert candidate.requester_audiences == seed.requester_audiences
        style_counts[style] += 1
        seed_counts[seed_id] += 1

    assert set(style_counts.values()) == {21}
    assert set(seed_counts) == set(official_answers)
    assert set(seed_counts.values()) == {5}
    assert Counter(
        next(
            tag
            for tag in candidate.tags
            if tag
            in {
                "acl_customer",
                "stale_future",
                "unapproved",
                "out_of_domain",
                "missing_evidence",
            }
        )
        for candidate in abstain
    ) == {
        "acl_customer": 10,
        "stale_future": 10,
        "unapproved": 5,
        "out_of_domain": 10,
        "missing_evidence": 10,
    }
    assert all(not candidate.relevant_chunk_ids for candidate in abstain)


def test_expanded_rag_review_csv_matches_candidate_contract(repo_root: Path) -> None:
    candidates = load_review_candidates(
        repo_root
        / "ai-service/evaluation/reviews/independent-review-candidates-v1.jsonl"
    )
    csv_path = (
        repo_root / "ai-service/evaluation/reviews/independent-review-v1.csv"
    )
    with csv_path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))

    assert len(rows) == len(candidates) == 150
    assert [row["candidateId"] for row in rows] == [
        candidate.candidate_id for candidate in candidates
    ]
    assert [row["query"] for row in rows] == [
        candidate.query for candidate in candidates
    ]
    assert all(row["reviewDecision"] == "PENDING" for row in rows)
    assert all(
        row["sourceKind"] == "MACHINE_AUTHORED_REVIEW_CANDIDATE" for row in rows
    )
    assert all(row["evidenceExcerpt"] for row in rows[:105])
    assert all(not row["evidenceExcerpt"] for row in rows[105:])


def test_provisional_benchmark_keeps_human_review_boundary(repo_root: Path) -> None:
    candidate_path = (
        repo_root
        / "ai-service/evaluation/reviews/independent-review-candidates-v1.jsonl"
    )
    report = json.loads(
        (
            repo_root
            / "ai-service/evaluation/independent-review-provisional-benchmark-v1.json"
        ).read_text(encoding="utf-8")
    )

    assert report["officialPerformanceClaim"] is False
    assert report["humanReviewCompleted"] is False
    assert report["inputs"]["candidateSha256"] == (
        "sha256:" + hashlib.sha256(candidate_path.read_bytes()).hexdigest()
    )
    assert report["summary"] == {
        "caseCount": 150,
        "answerableCount": 105,
        "noAnswerCount": 45,
        "top1PassCount": 69,
        "top3PassCount": 95,
        "top1Rate": 0.6571428571,
        "top3Rate": 0.9047619048,
        "noAnswerPassCount": 42,
        "noAnswerPassRate": 0.9333333333,
        "reviewTop2Or3Count": 26,
        "failureCount": 13,
    }
    assert len(report["answerFailures"]) == 10
    assert len(report["noAnswerFalsePositives"]) == 3


def test_policy_hardening_report_improves_abstention_without_ranking_tuning(
    repo_root: Path,
) -> None:
    report = json.loads(
        (
            repo_root
            / "ai-service/evaluation/independent-review-provisional-benchmark-v2.json"
        ).read_text(encoding="utf-8")
    )

    assert report["officialPerformanceClaim"] is False
    assert report["humanReviewCompleted"] is False
    assert report["sourceCommit"] == "468b064"
    assert report["summary"] == {
        "caseCount": 150,
        "answerableCount": 105,
        "noAnswerCount": 45,
        "top1PassCount": 69,
        "top3PassCount": 95,
        "noAnswerPassCount": 45,
        "reviewTop2Or3Count": 26,
        "failureCount": 10,
    }
    assert report["regressionComparison"] == {
        "top1Delta": 0,
        "top3Delta": 0,
        "noAnswerPassDelta": 3,
        "failureDelta": -3,
    }
    assert len(report["remainingAnswerFailures"]) == 10
    assert report["remainingNoAnswerFalsePositives"] == []
