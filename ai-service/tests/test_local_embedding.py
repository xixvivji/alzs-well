from __future__ import annotations

import math

import pytest

from app.embedding.local_hash import EMBEDDING_DIMENSIONS, embed_text, vector_literal


def test_embedding_is_deterministic_normalized_and_nfc_stable() -> None:
    composed = embed_text("금융거래 안심차단 안내")
    decomposed = embed_text("금융거래 안심차단 안내")

    assert composed == decomposed
    assert len(composed) == EMBEDDING_DIMENSIONS
    assert math.sqrt(sum(value * value for value in composed)) == pytest.approx(1.0)
    literal = vector_literal(composed)
    assert literal.startswith("[") and literal.endswith("]")
    assert literal.count(",") == EMBEDDING_DIMENSIONS - 1


def test_related_korean_text_has_higher_similarity_than_unrelated_text() -> None:
    query = embed_text("금융거래 안심차단")
    related = embed_text("금융거래 안심차단 서비스 신청 안내")
    unrelated = embed_text("반려동물 예방접종 일정")

    assert _cosine(query, related) > _cosine(query, unrelated)


def _cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    return sum(a * b for a, b in zip(left, right, strict=True))
