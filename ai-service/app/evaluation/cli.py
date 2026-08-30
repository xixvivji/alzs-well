from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from app.embedding.config import EmbeddingConfig, create_embedding_provider
from app.evaluation.model_catalog import create_evaluation_embedding_provider
from app.evaluation.metrics import evaluate
from app.evaluation.models import load_cases, load_corpus, validate_dataset
from app.evaluation.ranker import SearchConfiguration
from app.evaluation.runner import (
    QualityGate,
    tune,
    write_evaluation_report,
    write_tuning_report,
)


def main(argv: Sequence[str] | None = None) -> int:
    parser = _parser()
    args = parser.parse_args(argv)
    corpus = load_corpus(Path(args.corpus))
    cases = load_cases(Path(args.dataset))
    validate_dataset(corpus, cases)
    model_options = (
        args.evaluation_model_catalog,
        args.evaluation_model_name,
        args.evaluation_model_root,
    )
    if any(value is not None for value in model_options):
        if not all(value is not None for value in model_options):
            parser.error(
                "evaluation model catalog, name and root must be provided together"
            )
        embedding_provider = create_evaluation_embedding_provider(
            args.evaluation_model_catalog,
            args.evaluation_model_name,
            args.evaluation_model_root,
        )
    else:
        embedding_provider = create_embedding_provider(
            EmbeddingConfig.from_environment()
        )
    if args.command == "tune":
        candidates = tune(corpus, cases, embedding_provider)
        write_tuning_report(
            Path(args.output_json),
            candidates,
            embedding_provider.descriptor.model_version,
        )
        best_configuration, best_metrics = candidates[0]
        print(json.dumps({
            "ok": not QualityGate().failures(best_metrics),
            "code": "RETRIEVAL_TUNING_COMPLETED",
            "embeddingModelVersion": embedding_provider.descriptor.model_version,
            "best": {
                "configuration": {
                    "keywordWeight": best_configuration.keyword_weight,
                    "vectorWeight": best_configuration.vector_weight,
                    "vectorThreshold": best_configuration.vector_threshold,
                    "resultThreshold": best_configuration.result_threshold,
                }
            },
        }, ensure_ascii=False))
        return 0
    configuration = SearchConfiguration(
        keyword_weight=args.keyword_weight,
        vector_weight=args.vector_weight,
        vector_threshold=args.vector_threshold,
        result_threshold=args.result_threshold,
    )
    metrics, results = evaluate(
        corpus, cases, configuration, embedding_provider=embedding_provider
    )
    failures = QualityGate().failures(metrics)
    write_evaluation_report(
        Path(args.output_json),
        Path(args.output_markdown),
        configuration,
        metrics,
        results,
        failures,
        embedding_provider.descriptor.model_version,
    )
    print(json.dumps({
        "ok": not failures,
        "code": "RETRIEVAL_QUALITY_GATE_PASSED" if not failures else "RETRIEVAL_QUALITY_GATE_FAILED",
        "embeddingModelVersion": embedding_provider.descriptor.model_version,
        "recallAt3": metrics.recall_at_3,
        "recallAt5": metrics.recall_at_5,
        "mrr": metrics.mrr,
        "ndcgAt10": metrics.ndcg_at_10,
        "noAnswerFalsePositiveRate": metrics.no_answer_false_positive_rate,
        "policyViolationCount": metrics.policy_violation_count,
        "failures": failures,
    }, ensure_ascii=False))
    return 1 if failures and args.fail_on_gate else 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="retrieval-evaluation")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("evaluate", "tune"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("--corpus", required=True)
        subparser.add_argument("--dataset", required=True)
        subparser.add_argument("--output-json", required=True)
        subparser.add_argument("--evaluation-model-catalog", type=Path)
        subparser.add_argument("--evaluation-model-name")
        subparser.add_argument("--evaluation-model-root", type=Path)
        if command == "evaluate":
            subparser.add_argument("--output-markdown", required=True)
            subparser.add_argument("--keyword-weight", type=float, default=0.35)
            subparser.add_argument("--vector-weight", type=float, default=0.65)
            subparser.add_argument("--vector-threshold", type=float, default=0.15)
            subparser.add_argument("--result-threshold", type=float, default=0.35)
            subparser.add_argument("--fail-on-gate", action="store_true")
    return parser


if __name__ == "__main__":
    raise SystemExit(main())
