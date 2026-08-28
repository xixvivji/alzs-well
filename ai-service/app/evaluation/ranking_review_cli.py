from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from app.evaluation.model_catalog import create_evaluation_embedding_provider
from app.evaluation.models import load_cases, load_corpus, validate_dataset
from app.evaluation.ranker import SearchConfiguration
from app.evaluation.ranking_review import review_rankings, write_ranking_review


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="demo-retrieval-ranking-review")
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--output-markdown", type=Path, required=True)
    parser.add_argument("--evaluation-model-catalog", type=Path, required=True)
    parser.add_argument("--evaluation-model-name", required=True)
    parser.add_argument("--evaluation-model-root", type=Path, required=True)
    parser.add_argument("--keyword-weight", type=float, default=0.35)
    parser.add_argument("--vector-weight", type=float, default=0.65)
    parser.add_argument("--vector-threshold", type=float, default=0.15)
    parser.add_argument("--result-threshold", type=float, default=0.35)
    parser.add_argument("--fail-on-top-3-miss", action="store_true")
    args = parser.parse_args(argv)

    corpus = load_corpus(args.corpus)
    cases = load_cases(args.dataset)
    validate_dataset(corpus, cases)
    provider = create_evaluation_embedding_provider(
        args.evaluation_model_catalog,
        args.evaluation_model_name,
        args.evaluation_model_root,
    )
    configuration = SearchConfiguration(
        keyword_weight=args.keyword_weight,
        vector_weight=args.vector_weight,
        vector_threshold=args.vector_threshold,
        result_threshold=args.result_threshold,
    )
    summary, reviews = review_rankings(corpus, cases, configuration, provider)
    write_ranking_review(
        args.output_json,
        args.output_markdown,
        summary,
        reviews,
        configuration,
        provider.descriptor.model_version,
    )
    print(
        json.dumps(
            {
                "ok": summary.failure_count == 0,
                "code": "DEMO_RETRIEVAL_RANKING_REVIEW_COMPLETED",
                "top1": summary.top_1_pass_count,
                "top3": summary.top_3_pass_count,
                "review": summary.review_count,
                "failures": summary.failure_count,
            },
            ensure_ascii=False,
        )
    )
    return 1 if args.fail_on_top_3_miss and summary.failure_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
