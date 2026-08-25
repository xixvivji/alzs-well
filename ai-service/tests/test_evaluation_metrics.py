from __future__ import annotations

import json
from pathlib import Path

from app.evaluation.metrics import EvaluationMetrics, evaluate
from app.evaluation.models import load_cases, load_corpus
from app.evaluation.ranker import SearchConfiguration
from app.evaluation.runner import QualityGate, tune, write_tuning_report


DATASETS = Path(__file__).parents[1] / "evaluation" / "datasets"


def test_default_configuration_passes_retrieval_quality_gate() -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    cases = load_cases(DATASETS / "retrieval-v1.jsonl")

    metrics, results = evaluate(corpus, cases, SearchConfiguration())

    assert metrics.recall_at_3 == 1.0
    assert metrics.recall_at_5 == 1.0
    assert metrics.mrr == 1.0
    assert metrics.no_answer_false_positive_rate == 0.0
    assert metrics.policy_violation_count == 0
    assert QualityGate().failures(metrics) == ()
    assert len(results) == 15


def test_quality_gate_reports_every_failed_dimension() -> None:
    metrics = EvaluationMetrics(
        answerable_count=1,
        no_answer_count=1,
        recall_at_1=0.0,
        recall_at_3=0.0,
        recall_at_5=0.0,
        mrr=0.0,
        no_answer_false_positive_rate=1.0,
        policy_violation_count=1,
    )

    assert QualityGate().failures(metrics) == (
        "RECALL_AT_3_BELOW_GATE",
        "RECALL_AT_5_BELOW_GATE",
        "MRR_BELOW_GATE",
        "NO_ANSWER_FALSE_POSITIVE_RATE_ABOVE_GATE",
        "POLICY_VIOLATION_DETECTED",
    )


def test_tuning_ranks_gate_passing_candidate_first_and_writes_all_candidates(
    tmp_path: Path,
) -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    cases = load_cases(DATASETS / "retrieval-v1.jsonl")

    candidates = tune(corpus, cases)
    output = tmp_path / "tuning.json"
    write_tuning_report(output, candidates)
    payload = json.loads(output.read_text(encoding="utf-8"))

    assert len(candidates) == 125
    assert QualityGate().failures(candidates[0][1]) == ()
    assert payload["best"]["qualityGatePassed"] is True
    assert len(payload["candidates"]) == 125
