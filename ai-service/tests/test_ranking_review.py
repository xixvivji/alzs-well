from __future__ import annotations

import json
from datetime import date
from pathlib import Path

from app.embedding.base import EmbeddingDescriptor
from app.evaluation.models import EvaluationCase, EvaluationChunk
from app.evaluation.ranker import SearchConfiguration
from app.evaluation.ranking_review import review_rankings, write_ranking_review


class ReviewEmbeddingProvider:
    descriptor = EmbeddingDescriptor(
        backend="fixture",
        model_id="fixture/ranking-review",
        model_version="fixture-v1",
        dimensions=2,
    )

    def embed_query(self, value: str) -> tuple[float, ...]:
        return {
            "top one": (1.0, 0.0),
            "top two": (0.0, 1.0),
            "missing": (-1.0, 0.0),
            "no answer": (-1.0, -1.0),
        }[value]

    def embed_passage(self, value: str) -> tuple[float, ...]:
        if "first evidence" in value:
            return (1.0, 0.0)
        if "second evidence" in value:
            return (0.6, 0.8)
        return (0.0, 1.0)


def _chunk(chunk_id: str, content: str) -> EvaluationChunk:
    return EvaluationChunk(
        chunk_id=chunk_id,
        document_id=f"DOC-{chunk_id}",
        document_type="PUBLIC_GUIDE",
        heading=f"Heading {chunk_id}",
        section_path=("Section",),
        content=content,
        allowed_roles=("PROTECTION_STAFF",),
        audience="STAFF",
        approval_status="APPROVED",
        lifecycle_status="ACTIVE",
        effective_from=date(2026, 1, 1),
        effective_to=None,
    )


def _case(
    query_id: str, query: str, relevant: frozenset[str]
) -> EvaluationCase:
    return EvaluationCase(
        query_id=query_id,
        query=query,
        principal_roles=("PROTECTION_STAFF",),
        requester_audiences=("STAFF",),
        as_of=date(2026, 8, 28),
        relevant_chunk_ids=relevant,
        expect_no_results=not relevant,
        tags=(),
    )


def test_review_classifies_top_ranks_failures_and_no_answer(tmp_path: Path) -> None:
    corpus = (
        _chunk("A", "first evidence"),
        _chunk("B", "second evidence"),
        _chunk("C", "distractor evidence"),
    )
    cases = (
        _case("Q1", "top one", frozenset({"A"})),
        _case("Q2", "top two", frozenset({"B"})),
        _case("Q3", "missing", frozenset({"A"})),
        _case("Q4", "no answer", frozenset()),
    )
    configuration = SearchConfiguration(
        keyword_weight=0.0,
        vector_weight=1.0,
        vector_threshold=0.1,
        result_threshold=0.1,
    )

    summary, reviews = review_rankings(
        corpus, cases, configuration, ReviewEmbeddingProvider()
    )

    assert [review.verdict for review in reviews] == [
        "PASS_TOP_1",
        "REVIEW_TOP_2_OR_3",
        "FAIL_BELOW_TOP_3",
        "PASS_NO_RESULTS",
    ]
    assert summary.case_count == 4
    assert summary.top_1_pass_count == 1
    assert summary.top_3_pass_count == 2
    assert summary.review_count == 1
    assert summary.failure_count == 1

    output_json = tmp_path / "review.json"
    output_markdown = tmp_path / "review.md"
    write_ranking_review(
        output_json,
        output_markdown,
        summary,
        reviews,
        configuration,
        ReviewEmbeddingProvider.descriptor.model_version,
    )
    payload = json.loads(output_json.read_text(encoding="utf-8"))
    assert payload["reviewVersion"] == "demo-retrieval-ranking-review-v1"
    assert payload["cases"][1]["results"][0]["document_id"] == "DOC-C"
    markdown = output_markdown.read_text(encoding="utf-8")
    assert "Q2: REVIEW_TOP_2_OR_3" in markdown
    assert "Q3: FAIL_BELOW_TOP_3" in markdown
