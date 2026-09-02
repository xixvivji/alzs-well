from __future__ import annotations

from dataclasses import replace
from datetime import date
from pathlib import Path

import pytest

from app.embedding.base import EmbeddingDescriptor
from app.evaluation.models import EvaluationCase, EvaluationChunk, load_cases, load_corpus
from app.evaluation.ranker import DOCUMENT_AUTHORITY, SearchConfiguration, is_eligible, rank


DATASETS = Path(__file__).parents[1] / "evaluation" / "datasets"


class RecordingEmbeddingProvider:
    descriptor = EmbeddingDescriptor(
        backend="test", model_id="test", model_version="test-v1", dimensions=2
    )

    def __init__(self) -> None:
        self.queries: list[str] = []
        self.passages: list[str] = []

    def embed_query(self, value: str) -> tuple[float, ...]:
        self.queries.append(value)
        return (1.0, 0.0)

    def embed_passage(self, value: str) -> tuple[float, ...]:
        self.passages.append(value)
        return (1.0, 0.0)


class AuthorityOrderingEmbeddingProvider(RecordingEmbeddingProvider):
    def embed_passage(self, value: str) -> tuple[float, ...]:
        self.passages.append(value)
        return (0.6, 0.8) if "법령 저점수" in value else (1.0, 0.0)


def test_ranker_returns_relevant_chunk_and_applies_final_abstention_threshold() -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    cases = load_cases(DATASETS / "retrieval-v1.jsonl")

    answer = rank(cases[0], corpus, SearchConfiguration())
    no_answer = rank(cases[13], corpus, SearchConfiguration())

    assert answer[0].chunk.chunk_id in cases[0].relevant_chunk_ids
    assert answer[0].score >= 0.35
    assert no_answer == ()


def test_eligibility_blocks_acl_audience_lifecycle_and_effective_date() -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    cases = load_cases(DATASETS / "retrieval-v1.jsonl")
    staff_case = cases[0]

    assert is_eligible(corpus[0], staff_case)
    assert not is_eligible(corpus[5], staff_case)
    assert not is_eligible(corpus[8], staff_case)
    assert not is_eligible(corpus[9], staff_case)
    assert not is_eligible(corpus[10], staff_case)
    assert not is_eligible(replace(corpus[0], allowed_roles=("CUSTOMER",)), staff_case)
    assert not is_eligible(
        replace(corpus[0], content="개정 조문 [시행일: 2026. 10. 1.]"),
        staff_case,
    )


def test_search_configuration_rejects_invalid_weights_and_negative_threshold() -> None:
    with pytest.raises(ValueError, match="sum to one"):
        SearchConfiguration(keyword_weight=0.5, vector_weight=0.6)
    with pytest.raises(ValueError, match="cannot be negative"):
        SearchConfiguration(result_threshold=-0.1)
    with pytest.raises(ValueError, match="cannot exceed one"):
        SearchConfiguration(result_threshold=1.1)


def test_ranker_uses_injected_embedding_provider() -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    case = load_cases(DATASETS / "retrieval-v1.jsonl")[0]
    provider = RecordingEmbeddingProvider()

    ranked = rank(
        case,
        corpus,
        SearchConfiguration(),
        embedding_provider=provider,
    )

    assert ranked
    assert provider.queries == [case.query]
    assert provider.passages


def test_document_authority_order_matches_postgresql_contract() -> None:
    assert sorted(DOCUMENT_AUTHORITY, key=DOCUMENT_AUTHORITY.get, reverse=True) == [
        "LAW",
        "REGULATION",
        "INTERNAL_POLICY",
        "PUBLIC_GUIDE",
        "PUBLIC_NOTICE",
        "FORM",
        "SYNTHETIC_FIXTURE",
    ]


def test_authority_is_sorted_before_hybrid_score() -> None:
    case = EvaluationCase(
        query_id="AUTHORITY-FIRST",
        query="피해 지원",
        principal_roles=("PROTECTION_STAFF",),
        requester_audiences=("STAFF",),
        as_of=date(2026, 8, 31),
        relevant_chunk_ids=frozenset({"law", "notice"}),
        expect_no_results=False,
        tags=("authority",),
    )
    common = {
        "heading": "피해 지원",
        "section_path": ("안내",),
        "allowed_roles": ("PROTECTION_STAFF",),
        "audience": "STAFF",
        "approval_status": "APPROVED",
        "lifecycle_status": "ACTIVE",
        "effective_from": date(2026, 1, 1),
        "effective_to": None,
    }
    law = EvaluationChunk(
        chunk_id="law",
        document_id="DOC-LAW",
        document_type="LAW",
        content="법령 저점수",
        **common,
    )
    notice = EvaluationChunk(
        chunk_id="notice",
        document_id="DOC-NOTICE",
        document_type="PUBLIC_NOTICE",
        content="피해 지원 고점수",
        **common,
    )

    ranked = rank(
        case,
        (notice, law),
        SearchConfiguration(),
        embedding_provider=AuthorityOrderingEmbeddingProvider(),
    )

    assert ranked[0].chunk.chunk_id == "law"
    assert ranked[0].score < ranked[1].score


def test_law_and_regulation_golden_queries_return_the_expected_authority() -> None:
    corpus = load_corpus(DATASETS / "retrieval-corpus-v1.jsonl")
    cases = load_cases(DATASETS / "retrieval-v1.jsonl")

    law_results = rank(cases[15], corpus, SearchConfiguration())
    regulation_results = rank(cases[16], corpus, SearchConfiguration())

    assert law_results[0].chunk.chunk_id in cases[15].relevant_chunk_ids
    assert regulation_results[0].chunk.chunk_id in cases[16].relevant_chunk_ids
