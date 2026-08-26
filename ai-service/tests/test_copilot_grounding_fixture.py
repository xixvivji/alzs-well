from __future__ import annotations

from datetime import date
from pathlib import Path

from app.domain.manifest import ensure_ingestion_eligible
from app.ingestion.chunker import chunk_document
from app.ingestion.html_extractor import extract_html_document
from app.ingestion.manifest_loader import load_and_validate_manifest
from app.ingestion.source_validator import validate_source


MANIFEST = "contracts/knowledge/fixtures/synthetic-copilot-grounding.yaml"


def test_copilot_grounding_fixture_is_eligible_and_deterministic(repo_root: Path) -> None:
    manifest = load_and_validate_manifest(repo_root, MANIFEST)
    ensure_ingestion_eligible(manifest, as_of=date(2026, 8, 26))
    source = validate_source(repo_root, manifest)

    first = chunk_document(extract_html_document(manifest, source))
    second = chunk_document(extract_html_document(manifest, source))

    assert first == second
    assert first
    assert [chunk.chunk_order for chunk in first] == list(range(1, len(first) + 1))
    searchable = " ".join(chunk.text for chunk in first)
    assert "정기납부 미처리 고객 상담 안내" in searchable
    assert "중복 송금 고객 상담 안내" in searchable
    assert "거래 반복 확인 고객 상담 안내" in searchable
    assert all(chunk.chunk_id.startswith("chk_") for chunk in first)
