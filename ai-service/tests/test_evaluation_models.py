from __future__ import annotations

from pathlib import Path

import pytest

from app.evaluation.models import load_cases, load_corpus, validate_dataset


DATASETS = Path(__file__).parents[1] / "evaluation" / "datasets"


def test_committed_dataset_has_valid_references_and_no_answer_contract() -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    cases = load_cases(DATASETS / "retrieval-v1.jsonl")

    validate_dataset(corpus, cases)

    assert len(corpus) == 11
    assert len(cases) == 15
    assert sum(case.expect_no_results for case in cases) == 5


def test_dataset_rejects_unknown_relevant_chunk(tmp_path: Path) -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    dataset = tmp_path / "invalid.jsonl"
    dataset.write_text(
        '{"queryId":"Q-X","query":"질문","principalRoles":["PROTECTION_STAFF"],'
        '"requesterAudiences":["STAFF"],"asOf":"2026-08-25",'
        '"relevantChunkIds":["chk_unknown"],"expectNoResults":false,"tags":[]}\n',
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="unknown relevant chunk"):
        validate_dataset(corpus, load_cases(dataset))


def test_dataset_rejects_non_object_jsonl_line(tmp_path: Path) -> None:
    path = tmp_path / "invalid.jsonl"
    path.write_text("[]\n", encoding="utf-8")

    with pytest.raises(ValueError, match="object required"):
        load_cases(path)
