from __future__ import annotations

import json
import os
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path

from app.embedding.base import EmbeddingProvider
from app.evaluation.embedding_cache import cached
from app.evaluation.models import EvaluationCase, EvaluationChunk
from app.evaluation.ranker import SearchConfiguration, rank


@dataclass(frozen=True, slots=True)
class RankingReviewItem:
    rank: int
    chunk_id: str
    document_id: str
    heading: str
    section_path: tuple[str, ...]
    score: float
    keyword_score: float
    vector_score: float
    relevant: bool


@dataclass(frozen=True, slots=True)
class RankingReviewCase:
    query_id: str
    query: str
    expected_chunk_ids: tuple[str, ...]
    expected_document_ids: tuple[str, ...]
    first_relevant_rank: int | None
    top_1_relevant: bool
    verdict: str
    results: tuple[RankingReviewItem, ...]


@dataclass(frozen=True, slots=True)
class RankingReviewSummary:
    case_count: int
    answerable_count: int
    no_answer_count: int
    top_1_pass_count: int
    top_3_pass_count: int
    review_count: int
    failure_count: int


def review_rankings(
    corpus: tuple[EvaluationChunk, ...],
    cases: tuple[EvaluationCase, ...],
    configuration: SearchConfiguration,
    embedding_provider: EmbeddingProvider,
) -> tuple[RankingReviewSummary, tuple[RankingReviewCase, ...]]:
    provider = cached(embedding_provider)
    chunk_by_id = {chunk.chunk_id: chunk for chunk in corpus}
    reviews: list[RankingReviewCase] = []
    for case in cases:
        ranked = rank(
            case,
            corpus,
            configuration,
            limit=5,
            embedding_provider=provider,
        )
        items = tuple(
            RankingReviewItem(
                rank=index,
                chunk_id=item.chunk.chunk_id,
                document_id=item.chunk.document_id,
                heading=item.chunk.heading,
                section_path=item.chunk.section_path,
                score=item.score,
                keyword_score=item.keyword_score,
                vector_score=item.vector_score,
                relevant=item.chunk.chunk_id in case.relevant_chunk_ids,
            )
            for index, item in enumerate(ranked, start=1)
        )
        first_relevant_rank = next(
            (item.rank for item in items if item.relevant),
            None,
        )
        reviews.append(
            RankingReviewCase(
                query_id=case.query_id,
                query=case.query,
                expected_chunk_ids=tuple(sorted(case.relevant_chunk_ids)),
                expected_document_ids=tuple(
                    sorted(
                        {
                            chunk_by_id[chunk_id].document_id
                            for chunk_id in case.relevant_chunk_ids
                        }
                    )
                ),
                first_relevant_rank=first_relevant_rank,
                top_1_relevant=first_relevant_rank == 1,
                verdict=_verdict(case, items, first_relevant_rank),
                results=items,
            )
        )
    review_tuple = tuple(reviews)
    answerable = tuple(
        review
        for review, case in zip(review_tuple, cases, strict=True)
        if not case.expect_no_results
    )
    summary = RankingReviewSummary(
        case_count=len(cases),
        answerable_count=len(answerable),
        no_answer_count=len(cases) - len(answerable),
        top_1_pass_count=sum(review.first_relevant_rank == 1 for review in answerable),
        top_3_pass_count=sum(
            review.first_relevant_rank is not None and review.first_relevant_rank <= 3
            for review in answerable
        ),
        review_count=sum(review.verdict == "REVIEW_TOP_2_OR_3" for review in review_tuple),
        failure_count=sum(review.verdict.startswith("FAIL_") for review in review_tuple),
    )
    return summary, review_tuple


def write_ranking_review(
    output_json: Path,
    output_markdown: Path,
    summary: RankingReviewSummary,
    reviews: tuple[RankingReviewCase, ...],
    configuration: SearchConfiguration,
    embedding_model_version: str,
) -> None:
    payload = {
        "reviewVersion": "demo-retrieval-ranking-review-v1",
        "embeddingModelVersion": embedding_model_version,
        "configuration": asdict(configuration),
        "summary": asdict(summary),
        "cases": [asdict(review) for review in reviews],
    }
    _atomic_write(
        output_json,
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
    )
    lines = [
        "# Demo retrieval ranking review v1",
        "",
        f"- Embedding model: `{embedding_model_version}`",
        f"- Cases: {summary.case_count}",
        f"- Answerable / no-answer: {summary.answerable_count} / {summary.no_answer_count}",
        f"- Top-1: {summary.top_1_pass_count}/{summary.answerable_count}",
        f"- Top-3: {summary.top_3_pass_count}/{summary.answerable_count}",
        f"- Review / failure: {summary.review_count} / {summary.failure_count}",
        "",
        "| ID | Verdict | Expected document | First relevant rank | Top-1 document | Top-1 score | Query |",
        "|---|---|---|---:|---|---:|---|",
    ]
    for review in reviews:
        top = review.results[0] if review.results else None
        lines.append(
            "| "
            + " | ".join(
                (
                    _cell(review.query_id),
                    review.verdict,
                    _cell(", ".join(review.expected_document_ids) or "-"),
                    str(review.first_relevant_rank or "-"),
                    _cell(top.document_id if top else "-"),
                    f"{top.score:.4f}" if top else "-",
                    _cell(review.query),
                )
            )
            + " |"
        )
    for review in reviews:
        if review.verdict in {"PASS_TOP_1", "PASS_NO_RESULTS"}:
            continue
        lines.extend(("", f"## {review.query_id}: {review.verdict}", "", review.query, ""))
        if not review.results:
            lines.append("- No result")
            continue
        lines.extend(
            (
                "| Rank | Relevant | Document | Heading | Score | Keyword | Vector |",
                "|---:|---|---|---|---:|---:|---:|",
            )
        )
        for item in review.results:
            lines.append(
                f"| {item.rank} | {'YES' if item.relevant else 'NO'} | "
                f"{_cell(item.document_id)} | {_cell(item.heading)} | {item.score:.4f} | "
                f"{item.keyword_score:.4f} | {item.vector_score:.4f} |"
            )
    _atomic_write(output_markdown, "\n".join(lines) + "\n")


def _verdict(
    case: EvaluationCase,
    items: tuple[RankingReviewItem, ...],
    first_relevant_rank: int | None,
) -> str:
    if case.expect_no_results:
        return "PASS_NO_RESULTS" if not items else "FAIL_FALSE_POSITIVE"
    if first_relevant_rank == 1:
        return "PASS_TOP_1"
    if first_relevant_rank is not None and first_relevant_rank <= 3:
        return "REVIEW_TOP_2_OR_3"
    return "FAIL_BELOW_TOP_3"


def _cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


def _atomic_write(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="") as stream:
            stream.write(value)
            stream.flush()
            os.fsync(stream.fileno())
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
