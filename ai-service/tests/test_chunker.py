from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.domain.document import ExtractedBlock, ExtractedDocument
from app.errors import KnowledgeContractError
from app.ingestion.chunker import canonical_chunk_id, chunk_document
from app.ingestion.html_extractor import extract_html_document
from app.ingestion.manifest_loader import load_and_validate_manifest
from app.ingestion.source_validator import validate_source


def test_chunk_id_matches_all_contract_vectors(repo_root: Path) -> None:
    contract = json.loads((repo_root / "contracts/knowledge/chunk-id-test-vectors.json").read_text())

    for vector in contract["vectors"]:
        values = vector["input"]
        chunk_id, canonical_json = canonical_chunk_id(
            values[0], values[1], values[2], values[3], values[4], values[5]
        )
        assert canonical_json == vector["canonicalJson"], vector["name"]
        assert chunk_id == vector["expectedChunkId"], vector["name"]


def test_chunks_synthetic_fixture_deterministically(repo_root: Path) -> None:
    manifest = load_and_validate_manifest(
        repo_root, "contracts/knowledge/fixtures/synthetic-approved-active.yaml"
    )
    source = validate_source(repo_root, manifest)
    document = extract_html_document(manifest, source)

    first = chunk_document(document)
    second = chunk_document(document)

    assert first == second
    assert len(first) == 1
    assert first[0].chunk_order == 1
    assert first[0].heading == "신청 방법"
    assert first[0].section_path == ("합성 안심 안내", "신청 방법")
    assert first[0].text == "이 문서는 계약 검증을 위한 합성 자료입니다."
    assert first[0].text_hash.startswith("sha256:")


def test_keeps_section_boundaries_and_splits_long_text() -> None:
    document = _document(
        (
            ExtractedBlock(1, "PARAGRAPH", "가나다 라마바 사아자", None, ("문서", "첫 절")),
            ExtractedBlock(2, "HEADING", "둘째 절", 2, ("문서", "둘째 절")),
            ExtractedBlock(3, "PARAGRAPH", "짧은 본문", None, ("문서", "둘째 절")),
        )
    )

    chunks = chunk_document(document, max_chars=7)

    assert [chunk.chunk_order for chunk in chunks] == [1, 2, 3]
    assert [chunk.text for chunk in chunks] == ["가나다 라마바", "사아자", "짧은 본문"]
    assert chunks[0].section_path == ("문서", "첫 절")
    assert chunks[2].section_path == ("문서", "둘째 절")


def test_splits_unbroken_text_at_hard_limit() -> None:
    chunks = chunk_document(
        _document((ExtractedBlock(1, "PARAGRAPH", "가나다라마바사", None, ("문서",)),)),
        max_chars=3,
    )

    assert [chunk.text for chunk in chunks] == ["가나다", "라마바", "사"]


def test_rejects_heading_only_document() -> None:
    with pytest.raises(KnowledgeContractError) as caught:
        chunk_document(_document((ExtractedBlock(1, "HEADING", "절", 2, ("문서", "절")),)))

    assert caught.value.code == "CHUNK_VALIDATION_FAILED"


def test_rejects_non_positive_maximum() -> None:
    with pytest.raises(ValueError):
        chunk_document(_document(()), max_chars=0)


def _document(blocks: tuple[ExtractedBlock, ...]) -> ExtractedDocument:
    return ExtractedDocument(
        document_id="DOC-SYN-CHUNK-001",
        version_label="1.0.0",
        title="문서",
        source_hash="sha256:" + "0" * 64,
        extractor_version="html-structure-v1",
        blocks=blocks,
        warnings=(),
    )
