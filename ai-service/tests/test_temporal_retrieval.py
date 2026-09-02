from __future__ import annotations

import json
from datetime import date
from pathlib import Path

import pytest

from app.retrieval.temporal import (
    effective_dates_for_chunks,
    explicit_effective_dates,
    is_content_effective,
)


def test_extracts_korean_effective_date_with_spacing() -> None:
    content = "현행 조문 [시행일: 2026. 10. 1.] 제5조"

    assert explicit_effective_dates(content) == (date(2026, 10, 1),)
    assert not is_content_effective(content, date(2026, 9, 1))
    assert is_content_effective(content, date(2026, 10, 1))


def test_ordinary_content_and_invalid_marker_remain_searchable() -> None:
    assert is_content_effective("시행일 설명이 없는 현행 조문", date(2026, 9, 1))
    assert explicit_effective_dates("[시행일: 2026. 13. 40.]") == ()


def test_multiple_markers_fail_closed_when_any_date_is_future() -> None:
    content = "[시행일: 2026. 8. 1.] 본문 [시행일: 2026. 10. 1.]"

    assert not is_content_effective(content, date(2026, 9, 1))


def test_propagates_marker_to_repeated_provision_start_only() -> None:
    contents = (
        "① 현재 조문",
        "8. 현재 조문의 끝",
        "① 미래 개정 조문",
        "4. 미래 조문의 끝 [시행일: 2026. 10. 1.]",
    )
    paths = (("법", "제5조"),) * 4

    assert effective_dates_for_chunks(contents, paths) == (
        None,
        None,
        date(2026, 10, 1),
        date(2026, 10, 1),
    )


def test_marker_on_complete_repeated_chunk_does_not_mark_current_version() -> None:
    contents = (
        "현재 제1조 목적",
        "미래 제1조 목적 [시행일: 2026. 10. 1.]",
    )
    paths = (("법", "제1조"),) * 2

    assert effective_dates_for_chunks(contents, paths) == (
        None,
        date(2026, 10, 1),
    )


def test_rejects_mismatched_chunk_metadata_counts() -> None:
    with pytest.raises(ValueError, match="counts must match"):
        effective_dates_for_chunks(("본문",), ())


def test_does_not_propagate_across_missing_split_chunk() -> None:
    contents = (
        "① 현재 조문",
        "4. 미래 조문의 끝 [시행일: 2026. 10. 1.]",
    )
    paths = (("법", "제5조"),) * 2

    assert effective_dates_for_chunks(contents, paths, (10, 21)) == (
        None,
        date(2026, 10, 1),
    )


def test_committed_temporal_filter_report_preserves_claim_boundary(
    repo_root: Path,
) -> None:
    report = json.loads(
        (
            repo_root
            / "ai-service/evaluation/independent-review-temporal-filter-v1.json"
        ).read_text(encoding="utf-8")
    )

    assert report["humanApprovalRecorded"] is True
    assert report["approvedSixCaseTop1PassCount"] == 6
    assert len(report["approvedSixCases"]) == 6
    assert report["after"] == {
        "top1": 82,
        "top3": 99,
        "review": 17,
        "failures": 6,
    }
    assert report["corpusChunkCountBefore"] - report["corpusChunkCountAfter"] == 30
    assert report["excludedFutureChunkCount"] == 30
    assert report["configurationChanged"] is False
    assert report["allIndependentCandidatesReviewed"] is False
    assert report["officialPerformanceClaim"] is False
