from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.embedding.local_hash import LocalHashEmbeddingProvider
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


def test_evaluate_cli_can_select_verified_evaluation_model(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    calls: list[tuple[Path, str, Path]] = []

    def provider(catalog: Path, name: str, root: Path) -> LocalHashEmbeddingProvider:
        calls.append((catalog, name, root))
        return LocalHashEmbeddingProvider()

    monkeypatch.setattr(
        "app.evaluation.cli.create_evaluation_embedding_provider", provider
    )
    catalog = tmp_path / "catalog.json"
    model_root = tmp_path / "models"

    exit_code = main([
        "evaluate",
        "--corpus", str(DATASETS / "retrieval-corpus-v1.jsonl"),
        "--dataset", str(DATASETS / "retrieval-v1.jsonl"),
        "--output-json", str(tmp_path / "evaluation.json"),
        "--output-markdown", str(tmp_path / "evaluation.md"),
        "--evaluation-model-catalog", str(catalog),
        "--evaluation-model-name", "arctic-ko",
        "--evaluation-model-root", str(model_root),
    ])

    assert exit_code == 0
    assert calls == [(catalog, "arctic-ko", model_root)]


def test_evaluate_cli_requires_all_evaluation_model_options(tmp_path: Path) -> None:
    with pytest.raises(SystemExit) as failure:
        main([
            "evaluate",
            "--corpus", str(DATASETS / "retrieval-corpus-v1.jsonl"),
            "--dataset", str(DATASETS / "retrieval-v1.jsonl"),
            "--output-json", str(tmp_path / "evaluation.json"),
            "--output-markdown", str(tmp_path / "evaluation.md"),
            "--evaluation-model-name", "arctic-ko",
        ])

    assert failure.value.code == 2
