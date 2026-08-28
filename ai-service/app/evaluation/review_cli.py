from __future__ import annotations

import argparse
import json
from collections.abc import Sequence
from pathlib import Path

from app.evaluation.models import load_corpus
from app.evaluation.review import (
    finalize_review_csv,
    load_review_candidates,
    validate_review_candidates,
    write_provisional_benchmark_dataset,
    write_review_csv,
)


def main(argv: Sequence[str] | None = None) -> int:
    parser = _parser()
    args = parser.parse_args(argv)
    if args.command == "prepare":
        corpus = load_corpus(Path(args.corpus))
        candidates = load_review_candidates(Path(args.candidates))
        validate_review_candidates(candidates, corpus)
        write_review_csv(Path(args.output_csv), candidates, corpus)
        print(json.dumps({
            "ok": True,
            "code": "RETRIEVAL_REVIEW_PREPARED",
            "candidateCount": len(candidates),
            "pendingCount": sum(candidate.review_decision == "PENDING" for candidate in candidates),
            "outputCsv": args.output_csv,
        }, ensure_ascii=False))
        return 0
    corpus = load_corpus(Path(args.corpus))
    candidates = load_review_candidates(Path(args.candidates))
    if args.command == "benchmark":
        case_count = write_provisional_benchmark_dataset(
            Path(args.output_jsonl), candidates, corpus
        )
        print(json.dumps({
            "ok": True,
            "code": "PROVISIONAL_BENCHMARK_DATASET_CREATED",
            "caseCount": case_count,
            "outputJsonl": args.output_jsonl,
        }, ensure_ascii=False))
        return 0
    accepted_count = finalize_review_csv(
        Path(args.input_csv), Path(args.output_jsonl), candidates, corpus
    )
    print(json.dumps({
        "ok": True,
        "code": "RETRIEVAL_REVIEW_FINALIZED",
        "acceptedCount": accepted_count,
        "outputJsonl": args.output_jsonl,
    }, ensure_ascii=False))
    return 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="retrieval-review")
    subparsers = parser.add_subparsers(dest="command", required=True)
    prepare = subparsers.add_parser("prepare")
    prepare.add_argument("--corpus", required=True)
    prepare.add_argument("--candidates", required=True)
    prepare.add_argument("--output-csv", required=True)
    finalize = subparsers.add_parser("finalize")
    finalize.add_argument("--corpus", required=True)
    finalize.add_argument("--candidates", required=True)
    finalize.add_argument("--input-csv", required=True)
    finalize.add_argument("--output-jsonl", required=True)
    benchmark = subparsers.add_parser("benchmark")
    benchmark.add_argument("--corpus", required=True)
    benchmark.add_argument("--candidates", required=True)
    benchmark.add_argument("--output-jsonl", required=True)
    return parser


if __name__ == "__main__":
    raise SystemExit(main())
