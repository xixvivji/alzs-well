from __future__ import annotations

import json
from pathlib import Path

from app.cli import main
from app.ingestion.pdf_validator import ValidatedPdfSource


def test_cli_validates_approved_fixture(repo_root: Path, capsys: object) -> None:
    exit_code = main(
        [
            "validate-manifest",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.out)

    assert exit_code == 0
    assert payload["ok"] is True
    assert payload["approvalAndLifecycleEligible"] is True
    assert payload["source"]["hashVerified"] is True
    assert "text" not in payload["source"]


def test_cli_reports_real_manifest_as_valid_but_blocked(repo_root: Path, capsys: object) -> None:
    exit_code = main(
        [
            "validate-manifest",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.out)

    assert exit_code == 0
    assert payload["ok"] is True
    assert payload["approvalAndLifecycleEligible"] is False
    assert payload["governanceBlockingCodes"] == ["DOCUMENT_NOT_APPROVED", "DOCUMENT_NOT_ACTIVE"]


def test_cli_requires_explicit_repository_root(monkeypatch: object, capsys: object) -> None:
    monkeypatch.delenv("ALZS_REPO_ROOT", raising=False)  # type: ignore[attr-defined]
    exit_code = main(
        [
            "validate-manifest",
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.err)

    assert exit_code == 2
    assert payload == {
        "code": "REPOSITORY_ROOT_REQUIRED",
        "message": "저장소 루트를 명시해야 합니다.",
        "ok": False,
    }


def test_cli_uses_repository_root_environment(
    repo_root: Path,
    monkeypatch: object,
    capsys: object,
) -> None:
    monkeypatch.setenv("ALZS_REPO_ROOT", str(repo_root))  # type: ignore[attr-defined]
    exit_code = main(
        [
            "validate-manifest",
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    assert exit_code == 0
    assert json.loads(captured.out)["documentId"] == "DOC-SYN-CONTRACT-001"


def test_cli_returns_sanitized_schema_context(repo_root: Path, capsys: object) -> None:
    exit_code = main(
        [
            "validate-manifest",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-invalid/malformed-source-hash.yaml",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.err)
    assert exit_code == 2
    assert payload["code"] == "MANIFEST_SCHEMA_INVALID"
    assert "context" in payload
    assert "sha256:ABC123" not in captured.err


def test_cli_extracts_eligible_html_without_printing_body(repo_root: Path, capsys: object) -> None:
    exit_code = main(
        [
            "extract-html",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
            "--as-of",
            "2026-08-21",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.out)

    assert exit_code == 0
    assert payload["code"] == "HTML_EXTRACTION_COMPLETED"
    assert payload["blockCount"] == 2
    assert payload["source"]["hashVerified"] is True
    assert "이 문서는 계약 검증을 위한 합성 자료입니다." not in captured.out


def test_cli_rejects_unapproved_official_document(repo_root: Path, capsys: object) -> None:
    exit_code = main(
        [
            "extract-html",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml",
            "--as-of",
            "2026-08-21",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]

    assert exit_code == 3
    assert json.loads(captured.err)["code"] == "DOCUMENT_NOT_APPROVED"


def test_cli_rejects_invalid_as_of(repo_root: Path, capsys: object) -> None:
    exit_code = main(
        [
            "extract-html",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
            "--as-of",
            "21-08-2026",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]

    assert exit_code == 2
    assert json.loads(captured.err)["code"] == "MANIFEST_SCHEMA_INVALID"


def test_cli_rejects_document_outside_effective_period(repo_root: Path, capsys: object) -> None:
    exit_code = main(
        [
            "extract-html",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
            "--as-of",
            "2026-08-20",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]

    assert exit_code == 3
    assert json.loads(captured.err)["code"] == "DOCUMENT_NOT_EFFECTIVE"


def test_cli_ingests_html_to_derived_jsonl(
    repo_root: Path, tmp_path: Path, monkeypatch: object, capsys: object
) -> None:
    del tmp_path
    target = repo_root / "ai-service/data/derived/chunks/result.jsonl"

    def fake_writer(repository_root: Path, chunks: object) -> Path:
        assert repository_root == repo_root
        assert len(chunks) == 1  # type: ignore[arg-type]
        return target

    monkeypatch.setattr("app.cli.write_chunks_jsonl", fake_writer)  # type: ignore[attr-defined]
    exit_code = main(
        [
            "ingest-html",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
            "--as-of",
            "2026-08-21",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.out)

    assert exit_code == 0
    assert payload["code"] == "HTML_INGESTION_COMPLETED"
    assert payload["chunkCount"] == 1
    assert payload["outputPath"] == "ai-service/data/derived/chunks/result.jsonl"
    assert "이 문서는 계약 검증을 위한 합성 자료입니다." not in captured.out


def test_cli_validates_pdf_without_printing_document_content(
    repo_root: Path, monkeypatch: object, capsys: object
) -> None:
    monkeypatch.setattr(  # type: ignore[attr-defined]
        "app.cli.validate_pdf_source",
        lambda repository_root, manifest: ValidatedPdfSource(
            path=repo_root / "synthetic.pdf",
            size_bytes=1024,
            source_hash="sha256:" + "0" * 64,
            page_count=1,
            encrypted=False,
            active_content=False,
        ),
    )

    exit_code = main(
        [
            "validate-pdf",
            "--repo-root",
            str(repo_root),
            "--manifest",
            "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
            "--as-of",
            "2026-08-25",
        ]
    )
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.out)

    assert exit_code == 0
    assert payload["code"] == "PDF_VALIDATION_COMPLETED"
    assert payload["pageCount"] == 1
    assert payload["encrypted"] is False
    assert payload["activeContent"] is False
    assert "text" not in captured.out
