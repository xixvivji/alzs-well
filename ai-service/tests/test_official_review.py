from __future__ import annotations

import csv
import hashlib
import json
import shutil
from datetime import date
from pathlib import Path

import pytest

from app.domain.document import ExtractedBlock, ExtractedDocument
from app.errors import KnowledgeContractError
from app.evaluation.official_review import _write_atomic, build_official_review_corpus
from app.evaluation.official_review_cli import main
from app.evaluation.models import load_corpus
from app.evaluation.review import (
    load_review_candidates,
    validate_review_candidates,
)
from app.ingestion.manifest_loader import load_and_validate_manifest


MANIFEST_PATH = "knowledge/manifests/DOC-OFFICIAL-REVIEW-001.yaml"
OFFICIAL_MANIFESTS = (
    "knowledge/manifests/DOC-FSC-DESIGNATED-PERSON-NOTICE-001.yaml",
    "knowledge/manifests/DOC-FSC-FACE-TO-FACE-PHISHING-RELIEF-001.yaml",
    "knowledge/manifests/DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001.yaml",
    "knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml",
    "knowledge/manifests/DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001.yaml",
    "knowledge/manifests/DOC-LAW-TELECOM-FRAUD-REFUND-ACT-001.yaml",
    "knowledge/manifests/DOC-REG-TELECOM-FRAUD-REFUND-DECREE-001.yaml",
)
HTML_OFFICIAL_MANIFESTS = tuple(
    manifest_path
    for manifest_path in OFFICIAL_MANIFESTS
    if manifest_path
    not in {
        "knowledge/manifests/DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001.yaml",
        "knowledge/manifests/DOC-LAW-TELECOM-FRAUD-REFUND-ACT-001.yaml",
        "knowledge/manifests/DOC-REG-TELECOM-FRAUD-REFUND-DECREE-001.yaml",
    }
)


def test_builds_non_searchable_official_review_corpus(
    repo_root: Path, tmp_path: Path
) -> None:
    _write_review_repository(repo_root, tmp_path)

    result = build_official_review_corpus(
        tmp_path, (MANIFEST_PATH,), as_of=date(2026, 8, 27)
    )

    rows = [json.loads(line) for line in result.output_path.read_text().splitlines()]
    assert result.document_count == 1
    assert result.chunk_count == 1
    assert rows[0]["documentType"] == "PUBLIC_GUIDE"
    assert rows[0]["approvalStatus"] == "IN_REVIEW"
    assert rows[0]["lifecycleStatus"] == "PENDING_ACTIVATION"
    assert rows[0]["reviewOnly"] is True
    assert rows[0]["effectiveOnReviewDate"] is True
    assert not (tmp_path / "ai-service/data/derived/chunks").exists()


def test_rejects_non_review_governance(repo_root: Path, tmp_path: Path) -> None:
    manifest = _write_review_repository(repo_root, tmp_path)
    manifest.write_text(
        manifest.read_text(encoding="utf-8").replace(
            "usageRights: REVIEW_REQUIRED", "usageRights: PUBLIC_REUSE_ALLOWED"
        ),
        encoding="utf-8",
    )

    with pytest.raises(KnowledgeContractError) as raised:
        build_official_review_corpus(
            tmp_path, (MANIFEST_PATH,), as_of=date(2026, 8, 27)
        )
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"
    assert raised.value.safe_context == {"schemaPath": "officialReviewGovernance"}


def test_accepts_approved_internal_use_source_for_review_corpus(
    repo_root: Path, tmp_path: Path
) -> None:
    manifest = _write_review_repository(repo_root, tmp_path)
    payload = manifest.read_text(encoding="utf-8")
    payload = payload.replace("usageRights: REVIEW_REQUIRED", "usageRights: INTERNAL_USE_APPROVED")
    payload = payload.replace("approvalStatus: IN_REVIEW", "approvalStatus: APPROVED")
    payload = payload.replace("lifecycleStatus: PENDING_ACTIVATION", "lifecycleStatus: ACTIVE")
    payload = payload.replace("approvedBy: null", "approvedBy: official-reviewer")
    payload = payload.replace("approvedAt: null", 'approvedAt: "2026-08-28T00:31:28Z"')
    manifest.write_text(payload, encoding="utf-8")

    result = build_official_review_corpus(
        tmp_path, (MANIFEST_PATH,), as_of=date(2026, 8, 28)
    )
    row = json.loads(result.output_path.read_text())

    assert row["approvalStatus"] == "APPROVED"
    assert row["lifecycleStatus"] == "ACTIVE"
    assert row["reviewOnly"] is True


def test_rejects_duplicate_manifests_and_duplicate_chunks(
    repo_root: Path, tmp_path: Path
) -> None:
    manifest = _write_review_repository(repo_root, tmp_path)
    with pytest.raises(KnowledgeContractError) as raised:
        build_official_review_corpus(
            tmp_path, (MANIFEST_PATH, MANIFEST_PATH), as_of=date(2026, 8, 27)
        )
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"

    duplicate_path = manifest.with_name("DOC-OFFICIAL-REVIEW-DUPLICATE.yaml")
    duplicate_path.write_bytes(manifest.read_bytes())
    with pytest.raises(KnowledgeContractError) as raised:
        build_official_review_corpus(
            tmp_path,
            (MANIFEST_PATH, duplicate_path.relative_to(tmp_path).as_posix()),
            as_of=date(2026, 8, 27),
        )
    assert raised.value.code == "CHUNK_VALIDATION_FAILED"


def test_extracts_pdf_review_source_through_pdf_pipeline(
    repo_root: Path, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    manifest_path = _write_review_repository(repo_root, tmp_path)
    payload = manifest_path.read_text(encoding="utf-8").replace(
        "knowledge/official-source/review.html", "knowledge/official-source/review.pdf"
    )
    manifest_path.write_text(payload, encoding="utf-8")
    manifest = load_and_validate_manifest(tmp_path, MANIFEST_PATH)

    monkeypatch.setattr(
        "app.evaluation.official_review.validate_pdf_source",
        lambda repository_root, value: object(),
    )
    monkeypatch.setattr(
        "app.evaluation.official_review.extract_pdf_document",
        lambda value, source: ExtractedDocument(
            document_id=value.document_id,
            version_label=value.version_label,
            title=value.title,
            source_hash=value.source_hash,
            extractor_version="pypdf-text-v1",
            blocks=(
                ExtractedBlock(
                    block_order=1,
                    block_type="PARAGRAPH",
                    text="PDF 공식 검수 근거입니다.",
                    heading_level=None,
                    section_path=(value.title, "신청 기준"),
                    page_start=1,
                    page_end=1,
                ),
            ),
            warnings=(),
        ),
    )

    result = build_official_review_corpus(
        tmp_path, (MANIFEST_PATH,), as_of=date(2026, 8, 27)
    )
    assert result.chunk_count == 1
    assert json.loads(result.output_path.read_text())["pageStart"] == 1
    assert manifest.source_path.endswith(".pdf")


def test_cli_reports_metadata_without_printing_content(
    repo_root: Path, tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    _write_review_repository(repo_root, tmp_path)

    assert main(
        [
            "--repo-root",
            str(tmp_path),
            "--manifest",
            MANIFEST_PATH,
            "--as-of",
            "2026-08-27",
        ]
    ) == 0
    payload = json.loads(capsys.readouterr().out)
    assert payload["code"] == "OFFICIAL_REVIEW_CORPUS_CREATED"
    assert payload["reviewOnly"] is True
    assert payload["documents"][0]["chunkCount"] == 1
    assert "공식 검수 근거" not in json.dumps(payload, ensure_ascii=False)

    assert main(
        [
            "--repo-root",
            str(tmp_path),
            "--manifest",
            MANIFEST_PATH,
            "--as-of",
            "invalid",
        ]
    ) == 2
    error = json.loads(capsys.readouterr().err)
    assert error["code"] == "MANIFEST_SCHEMA_INVALID"
    assert error["context"] == {"schemaPath": "asOf"}


def test_atomic_writer_removes_temporary_file_on_failure(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(
        "app.evaluation.official_review.os.replace",
        lambda source, target: (_ for _ in ()).throw(OSError("synthetic")),
    )
    with pytest.raises(KnowledgeContractError) as raised:
        _write_atomic(tmp_path / "review.jsonl", [{"reviewOnly": True}])
    assert raised.value.code == "OUTPUT_WRITE_FAILED"
    assert not tuple(tmp_path.glob("*.tmp"))


def test_committed_official_review_pack_matches_reviewable_sources(
    repo_root: Path, tmp_path: Path
) -> None:
    _copy_official_review_sources(repo_root, tmp_path, HTML_OFFICIAL_MANIFESTS)
    result = build_official_review_corpus(
        tmp_path, HTML_OFFICIAL_MANIFESTS, as_of=date(2026, 8, 27)
    )
    corpus = load_corpus(result.output_path)
    review_root = repo_root / "ai-service/evaluation/reviews"
    candidates = load_review_candidates(
        review_root / "official-retrieval-review-candidates-v1.jsonl"
    )
    with (review_root / "official-retrieval-review-v1.csv").open(
        encoding="utf-8-sig", newline=""
    ) as stream:
        review_rows = list(csv.DictReader(stream))
    html_chunk_ids = {chunk.chunk_id for chunk in corpus}
    html_candidates = tuple(
        candidate
        for candidate in candidates
        if not candidate.relevant_chunk_ids
        or set(candidate.relevant_chunk_ids) <= html_chunk_ids
    )

    validate_review_candidates(html_candidates, corpus)

    assert result.document_count == 4
    assert result.chunk_count == 9
    assert len(candidates) == 30
    assert len(review_rows) == 30
    assert sum(candidate.expected_action == "ANSWER" for candidate in candidates) == 24
    assert sum(candidate.expected_action == "ABSTAIN" for candidate in candidates) == 6
    decisions = [candidate.review_decision for candidate in candidates]
    assert decisions.count("ACCEPTED") == 27
    assert decisions.count("PENDING") == 3
    assert {chunk.approval_status for chunk in corpus} == {"APPROVED", "IN_REVIEW"}
    assert {chunk.lifecycle_status for chunk in corpus} == {
        "ACTIVE",
        "PENDING_ACTIVATION",
    }
    assert [row["candidateId"] for row in review_rows] == [
        candidate.candidate_id for candidate in candidates
    ]
    assert all(row["evidenceExcerpt"] for row in review_rows[:24])
    assert all(not row["evidenceExcerpt"] for row in review_rows[24:])
    for candidate, row in zip(candidates, review_rows, strict=True):
        assert row["query"] == candidate.query
        assert row["expectedAction"] == candidate.expected_action
        assert row["relevantChunkIds"] == "|".join(candidate.relevant_chunk_ids)
        assert row["reviewDecision"] == candidate.review_decision
        assert row["reviewComment"] == candidate.review_comment

    approved_dataset = repo_root / (
        "ai-service/evaluation/datasets/official-operational-golden-v1.jsonl"
    )
    approved_rows = [
        json.loads(line)
        for line in approved_dataset.read_text(encoding="utf-8").splitlines()
    ]
    assert len(approved_rows) == 27
    assert {row["queryId"] for row in approved_rows} == {
        candidate.candidate_id
        for candidate in candidates
        if candidate.review_decision == "ACCEPTED"
    }
    assert {"ORC-004", "ORC-005", "ORC-013"}.isdisjoint(
        row["queryId"] for row in approved_rows
    )

    approved_manifests = {
        "knowledge/manifests/DOC-FSC-DESIGNATED-PERSON-NOTICE-001.yaml",
        "knowledge/manifests/DOC-FSC-NONFACE-ACCOUNT-BLOCK-QA-001.yaml",
        "knowledge/manifests/DOC-KDIC-MISTAKEN-REMITTANCE-ELIGIBILITY-001.yaml",
        "knowledge/manifests/DOC-LAW-TELECOM-FRAUD-REFUND-ACT-001.yaml",
        "knowledge/manifests/DOC-REG-TELECOM-FRAUD-REFUND-DECREE-001.yaml",
    }
    for manifest_path in OFFICIAL_MANIFESTS:
        manifest = load_and_validate_manifest(repo_root, manifest_path)
        if manifest_path in approved_manifests:
            assert manifest.approval_status == "APPROVED"
            assert manifest.lifecycle_status == "ACTIVE"
            assert manifest.payload["usageRights"] == "INTERNAL_USE_APPROVED"
        else:
            assert manifest.approval_status == "IN_REVIEW"
            assert manifest.lifecycle_status == "PENDING_ACTIVATION"
            assert manifest.payload["usageRights"] == "REVIEW_REQUIRED"


def _write_review_repository(repo_root: Path, target: Path) -> Path:
    schema = target / "contracts/knowledge/manifest.schema.json"
    schema.parent.mkdir(parents=True)
    schema.write_bytes((repo_root / "contracts/knowledge/manifest.schema.json").read_bytes())
    source = target / "knowledge/official-source/review.html"
    source.parent.mkdir(parents=True)
    raw = (
        "<!doctype html><html><head><meta charset=\"utf-8\"><title>공식 검수 안내"
        "</title></head><body><main><h1>공식 검수 안내</h1><h2>신청 기준</h2>"
        "<p>공식 검수 근거입니다.</p></main></body></html>"
    ).encode()
    source.write_bytes(raw)
    digest = "sha256:" + hashlib.sha256(raw).hexdigest()
    manifest = target / MANIFEST_PATH
    manifest.parent.mkdir(parents=True)
    manifest.write_text(
        f'''contractVersion: "1.0.0"
documentId: DOC-OFFICIAL-REVIEW-001
versionLabel: "2026-08-27"
title: 공식 검수 안내
issuer: 공식기관
sourceType: OFFICIAL_EXTERNAL
sourcePath: knowledge/official-source/review.html
sourceUrl: https://example.invalid/review
sourceHash: {digest}
sourceTransformations: []
documentType: PUBLIC_GUIDE
classification: PUBLIC_OFFICIAL
audience: BOTH
allowedRoles:
  - PROTECTION_STAFF
  - DETECTION_ADMIN
effectiveFrom: "2026-08-27"
effectiveTo: null
checkedAt: "2026-08-27"
usageRights: REVIEW_REQUIRED
approvalStatus: IN_REVIEW
lifecycleStatus: PENDING_ACTIVATION
approvedBy: null
approvedAt: null
supersedes: null
''',
        encoding="utf-8",
    )
    return manifest


def _copy_official_review_sources(
    repo_root: Path, target: Path, manifest_paths: tuple[str, ...]
) -> None:
    schema = target / "contracts/knowledge/manifest.schema.json"
    schema.parent.mkdir(parents=True)
    shutil.copy2(repo_root / "contracts/knowledge/manifest.schema.json", schema)
    for manifest_path in manifest_paths:
        source_manifest = repo_root / manifest_path
        target_manifest = target / manifest_path
        target_manifest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source_manifest, target_manifest)
        manifest = load_and_validate_manifest(repo_root, manifest_path)
        source = repo_root / manifest.source_path
        target_source = target / manifest.source_path
        target_source.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target_source)
