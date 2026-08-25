from __future__ import annotations

import json
from pathlib import Path

from app.evaluation.cli import main


DATASETS = Path(__file__).parents[1] / "evaluation" / "datasets"


def test_evaluate_cli_writes_reports_and_enforces_gate(tmp_path: Path) -> None:
    output_json = tmp_path / "evaluation.json"
    output_markdown = tmp_path / "evaluation.md"

    exit_code = main([
        "evaluate",
        "--corpus", str(DATASETS / "retrieval-corpus-v1.jsonl"),
        "--dataset", str(DATASETS / "retrieval-v1.jsonl"),
        "--output-json", str(output_json),
        "--output-markdown", str(output_markdown),
        "--fail-on-gate",
    ])
    payload = json.loads(output_json.read_text(encoding="utf-8"))

    assert exit_code == 0
    assert payload["qualityGatePassed"] is True
    assert "Quality gate: PASS" in output_markdown.read_text(encoding="utf-8")


def test_evaluate_cli_returns_failure_for_overly_strict_threshold(tmp_path: Path) -> None:
    exit_code = main([
        "evaluate",
        "--corpus", str(DATASETS / "retrieval-corpus-v1.jsonl"),
        "--dataset", str(DATASETS / "retrieval-v1.jsonl"),
        "--output-json", str(tmp_path / "evaluation.json"),
        "--output-markdown", str(tmp_path / "evaluation.md"),
        "--result-threshold", "0.99",
        "--fail-on-gate",
    ])

    assert exit_code == 1
