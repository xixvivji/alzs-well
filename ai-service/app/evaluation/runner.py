from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path

from app.embedding.base import EmbeddingProvider
from app.embedding.local_hash import EMBEDDING_MODEL_VERSION
from app.evaluation.metrics import CaseResult, EvaluationMetrics, evaluate
from app.evaluation.models import EvaluationCase, EvaluationChunk
from app.evaluation.ranker import SearchConfiguration


@dataclass(frozen=True, slots=True)
class QualityGate:
    recall_at_3: float = 0.80
    recall_at_5: float = 0.90
    mrr: float = 0.70
    no_answer_false_positive_rate: float = 0.10

    def failures(self, metrics: EvaluationMetrics) -> tuple[str, ...]:
        failures: list[str] = []
        if metrics.recall_at_3 < self.recall_at_3:
            failures.append("RECALL_AT_3_BELOW_GATE")
        if metrics.recall_at_5 < self.recall_at_5:
            failures.append("RECALL_AT_5_BELOW_GATE")
        if metrics.mrr < self.mrr:
            failures.append("MRR_BELOW_GATE")
        if metrics.no_answer_false_positive_rate > self.no_answer_false_positive_rate:
            failures.append("NO_ANSWER_FALSE_POSITIVE_RATE_ABOVE_GATE")
        if metrics.policy_violation_count:
            failures.append("POLICY_VIOLATION_DETECTED")
        return tuple(failures)


def tune(
    corpus: tuple[EvaluationChunk, ...],
    cases: tuple[EvaluationCase, ...],
    embedding_provider: EmbeddingProvider | None = None,
) -> tuple[tuple[SearchConfiguration, EvaluationMetrics], ...]:
    candidates: list[tuple[SearchConfiguration, EvaluationMetrics]] = []
    for keyword_percent in (20, 30, 35, 40, 50):
        for threshold_percent in (10, 15, 20, 25, 30):
            for result_threshold_percent in (20, 25, 30, 35, 40):
                configuration = SearchConfiguration(
                    keyword_weight=keyword_percent / 100,
                    vector_weight=(100 - keyword_percent) / 100,
                    vector_threshold=threshold_percent / 100,
                    result_threshold=result_threshold_percent / 100,
                )
                metrics, _ = evaluate(
                    corpus, cases, configuration, embedding_provider=embedding_provider
                )
                candidates.append((configuration, metrics))
    candidates.sort(
        key=lambda item: (
            len(QualityGate().failures(item[1])),
            item[1].policy_violation_count,
            item[1].no_answer_false_positive_rate,
            -item[1].recall_at_3,
            -item[1].recall_at_5,
            -item[1].mrr,
            abs(item[0].keyword_weight - 0.35),
            abs(item[0].vector_threshold - 0.15),
            abs(item[0].result_threshold - 0.35),
        )
    )
    return tuple(candidates)


def write_evaluation_report(
    output_json: Path,
    output_markdown: Path,
    configuration: SearchConfiguration,
    metrics: EvaluationMetrics,
    cases: tuple[CaseResult, ...],
    failures: tuple[str, ...],
    embedding_model_version: str = EMBEDDING_MODEL_VERSION,
) -> None:
    payload = {
        "evaluationVersion": "retrieval-eval-v1",
        "embeddingModelVersion": embedding_model_version,
        "configuration": asdict(configuration),
        "metrics": asdict(metrics),
        "qualityGatePassed": not failures,
        "qualityGateFailures": list(failures),
        "cases": [asdict(case) for case in cases],
    }
    _write_json(output_json, payload)
    lines = [
        "# Retrieval evaluation v1",
        "",
        f"- Quality gate: {'PASS' if not failures else 'FAIL'}",
        f"- Embedding model: `{embedding_model_version}`",
        f"- Recall@1: {metrics.recall_at_1:.4f}",
        f"- Recall@3: {metrics.recall_at_3:.4f}",
        f"- Recall@5: {metrics.recall_at_5:.4f}",
        f"- MRR: {metrics.mrr:.4f}",
        f"- No-answer false-positive rate: {metrics.no_answer_false_positive_rate:.4f}",
        f"- Policy violations: {metrics.policy_violation_count}",
        "",
        "## Failures",
        "",
        *(f"- {failure}" for failure in failures),
    ]
    _write_text(output_markdown, "\n".join(lines) + "\n")


def write_tuning_report(
    output_json: Path,
    candidates: tuple[tuple[SearchConfiguration, EvaluationMetrics], ...],
    embedding_model_version: str = EMBEDDING_MODEL_VERSION,
) -> None:
    gate = QualityGate()
    _write_json(
        output_json,
        {
            "evaluationVersion": "retrieval-eval-v1",
            "embeddingModelVersion": embedding_model_version,
            "best": _candidate(candidates[0], gate),
            "candidates": [_candidate(candidate, gate) for candidate in candidates],
        },
    )


def _candidate(
    candidate: tuple[SearchConfiguration, EvaluationMetrics], gate: QualityGate
) -> dict[str, object]:
    configuration, metrics = candidate
    failures = gate.failures(metrics)
    return {
        "configuration": asdict(configuration),
        "metrics": asdict(metrics),
        "qualityGatePassed": not failures,
        "qualityGateFailures": list(failures),
    }


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _write_text(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")
