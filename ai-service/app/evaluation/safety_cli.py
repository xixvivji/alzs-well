from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from app.evaluation.safety import evaluate_safety, load_safety_cases


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="safety-evaluation")
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--output-json", required=True, type=Path)
    args = parser.parse_args(argv)
    report = evaluate_safety(load_safety_cases(args.dataset))
    payload = {
        "evaluationVersion": "ai-safety-policy-v1",
        "humanApproved": False,
        "purpose": "machine-authored regression only",
        "total": report.total,
        "passed": report.passed,
        "failedCaseIds": list(report.failed_case_ids),
        "categoryCounts": report.category_counts,
        "qualityGatePassed": report.ok,
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(payload, ensure_ascii=False))
    return 0 if report.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
