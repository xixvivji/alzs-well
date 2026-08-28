from __future__ import annotations

import math
from dataclasses import dataclass

from app.embedding.base import EmbeddingProvider
from app.evaluation.embedding_cache import cached
from app.evaluation.models import EvaluationCase, EvaluationChunk
from app.evaluation.ranker import SearchConfiguration, is_eligible, rank


@dataclass(frozen=True, slots=True)
class EvaluationMetrics:
    answerable_count: int
    no_answer_count: int
    recall_at_1: float
    recall_at_3: float
    recall_at_5: float
    mrr: float
    ndcg_at_10: float
    no_answer_false_positive_rate: float
    policy_violation_count: int


@dataclass(frozen=True, slots=True)
class CaseResult:
    query_id: str
    returned_chunk_ids: tuple[str, ...]
    first_relevant_rank: int | None
    false_positive: bool
    policy_violation_count: int


def evaluate(
    corpus: tuple[EvaluationChunk, ...],
    cases: tuple[EvaluationCase, ...],
    configuration: SearchConfiguration,
    embedding_provider: EmbeddingProvider | None = None,
) -> tuple[EvaluationMetrics, tuple[CaseResult, ...]]:
    evaluation_provider = cached(embedding_provider)
    results = tuple(
        _evaluate_case(corpus, case, configuration, evaluation_provider) for case in cases
    )
    answerable = tuple(
        (case, result) for case, result in zip(cases, results, strict=True) if not case.expect_no_results
    )
    no_answer = tuple(
        result for case, result in zip(cases, results, strict=True) if case.expect_no_results
    )
    metrics = EvaluationMetrics(
        answerable_count=len(answerable),
        no_answer_count=len(no_answer),
        recall_at_1=_recall(answerable, 1),
        recall_at_3=_recall(answerable, 3),
        recall_at_5=_recall(answerable, 5),
        mrr=(
            sum(0.0 if result.first_relevant_rank is None else 1 / result.first_relevant_rank
                for _, result in answerable) / len(answerable)
            if answerable else 0.0
        ),
        ndcg_at_10=(
            sum(_ndcg(case, result, 10) for case, result in answerable) / len(answerable)
            if answerable else 0.0
        ),
        no_answer_false_positive_rate=(
            sum(result.false_positive for result in no_answer) / len(no_answer)
            if no_answer else 0.0
        ),
        policy_violation_count=sum(result.policy_violation_count for result in results),
    )
    return metrics, results


def _evaluate_case(
    corpus: tuple[EvaluationChunk, ...],
    case: EvaluationCase,
    configuration: SearchConfiguration,
    embedding_provider: EmbeddingProvider | None,
) -> CaseResult:
    ranked = rank(
        case, corpus, configuration, limit=10, embedding_provider=embedding_provider
    )
    returned = tuple(item.chunk.chunk_id for item in ranked)
    first_rank = next(
        (index for index, chunk_id in enumerate(returned, start=1)
         if chunk_id in case.relevant_chunk_ids),
        None,
    )
    violations = sum(not is_eligible(item.chunk, case) for item in ranked)
    return CaseResult(
        query_id=case.query_id,
        returned_chunk_ids=returned,
        first_relevant_rank=first_rank,
        false_positive=case.expect_no_results and bool(ranked),
        policy_violation_count=violations,
    )


def _recall(answerable: tuple[tuple[EvaluationCase, CaseResult], ...], at: int) -> float:
    if not answerable:
        return 0.0
    return sum(
        result.first_relevant_rank is not None and result.first_relevant_rank <= at
        for _, result in answerable
    ) / len(answerable)


def _ndcg(case: EvaluationCase, result: CaseResult, at: int) -> float:
    gains = tuple(
        1.0 if chunk_id in case.relevant_chunk_ids else 0.0
        for chunk_id in result.returned_chunk_ids[:at]
    )
    dcg = sum(gain / math.log2(rank + 1) for rank, gain in enumerate(gains, start=1))
    ideal_count = min(len(case.relevant_chunk_ids), at)
    ideal = sum(1.0 / math.log2(rank + 1) for rank in range(1, ideal_count + 1))
    return dcg / ideal if ideal else 0.0
