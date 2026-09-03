from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from app.evaluation.models import load_corpus
from app.evaluation.review import load_review_candidates, validate_review_candidates
from app.evaluation.triage import triage_ranking_review, write_triage_outputs


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="retrieval-ai-triage")
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--ranking-json", type=Path, required=True)
    parser.add_argument("--output-csv", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--output-markdown", type=Path, required=True)
    args = parser.parse_args(argv)

    corpus = load_corpus(args.corpus)
    candidates = load_review_candidates(args.candidates)
    validate_review_candidates(candidates, corpus)
    ranking_payload = json.loads(args.ranking_json.read_text(encoding="utf-8"))
    rows = triage_ranking_review(candidates, corpus, ranking_payload)
    write_triage_outputs(
        args.output_csv, args.output_json, args.output_markdown, rows
    )
    print(
        json.dumps(
            {"ok": True, "code": "AI_TRIAGE_COMPLETED", "caseCount": len(rows)},
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
