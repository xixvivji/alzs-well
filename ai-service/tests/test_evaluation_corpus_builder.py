from __future__ import annotations

import hashlib
import json
from datetime import date
from pathlib import Path

import pytest

from app.errors import KnowledgeContractError
from app.evaluation.corpus_builder import build_evaluation_corpus
from app.evaluation.corpus_cli import main
from app.ingestion.chunker import canonical_chunk_id


MANIFEST_PATH = "contracts/knowledge/fixtures/synthetic-approved-active.yaml"


def test_builds_official_evaluation_corpus_from_verified_chunks(
    repo_root: Path, tmp_path: Path
) -> None:
    _copy_contract(repo_root, tmp_path)
    row = _write_chunk(tmp_path)

    result = build_evaluation_corpus(
        tmp_path, (MANIFEST_PATH,), as_of=date(2026, 8, 21)
    )

    assert result.document_count == 1
    assert result.chunk_count == 1
    payload = json.loads(result.output_path.read_text(encoding="utf-8"))
    assert payload == {
        "chunkId": row["chunkId"],
        "documentId": "DOC-SYN-CONTRACT-001",
        "documentType": "SYNTHETIC_FIXTURE",
        "heading": "검증 절",
        "sectionPath": ["합성 문서", "검증 절"],
        "content": "승인된 합성 문서의 검색 근거입니다.",
        "allowedRoles": ["PROTECTION_STAFF", "DETECTION_ADMIN"],
        "audience": "STAFF",
        "approvalStatus": "APPROVED",
        "lifecycleStatus": "ACTIVE",
        "effectiveFrom": "2026-08-21",
        "effectiveTo": None,
    }


def test_rejects_tampered_or_unapproved_inputs(repo_root: Path, tmp_path: Path) -> None:
    _copy_contract(repo_root, tmp_path)
    row = _write_chunk(tmp_path)
    chunk_path = _chunk_path(tmp_path)
    row["text"] = "변조된 본문"
    chunk_path.write_text(json.dumps(row, ensure_ascii=False) + "\n", encoding="utf-8")

    with pytest.raises(KnowledgeContractError) as raised:
        build_evaluation_corpus(
            tmp_path, (MANIFEST_PATH,), as_of=date(2026, 8, 21)
        )
    assert raised.value.code == "CHUNK_VALIDATION_FAILED"

    with pytest.raises(KnowledgeContractError) as raised:
        build_evaluation_corpus(
            repo_root,
            ("knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml",),
            as_of=date(2026, 8, 26),
        )
    assert raised.value.code == "DOCUMENT_NOT_APPROVED"


def test_excludes_chunk_with_future_effective_marker(
    repo_root: Path, tmp_path: Path
) -> None:
    _copy_contract(repo_root, tmp_path)
    _write_chunk(tmp_path, text="개정 조문 [시행일: 2026. 10. 1.] 제1조")

    result = build_evaluation_corpus(
        tmp_path, (MANIFEST_PATH,), as_of=date(2026, 9, 1)
    )

    assert result.chunk_count == 0
    assert result.output_path.read_text(encoding="utf-8") == ""


def test_corpus_cli_reports_output_without_printing_content(
    repo_root: Path, tmp_path: Path, capsys: object
) -> None:
    _copy_contract(repo_root, tmp_path)
    _write_chunk(tmp_path)

    exit_code = main([
        "--repo-root", str(tmp_path),
        "--manifest", MANIFEST_PATH,
        "--as-of", "2026-08-21",
    ])
    captured = capsys.readouterr()  # type: ignore[attr-defined]
    payload = json.loads(captured.out)

    assert exit_code == 0
    assert payload["code"] == "EVALUATION_CORPUS_CREATED"
    assert payload["documentCount"] == 1
    assert payload["chunkCount"] == 1
    assert "검색 근거" not in captured.out


def _copy_contract(repo_root: Path, target: Path) -> None:
    schema = target / "contracts/knowledge/manifest.schema.json"
    schema.parent.mkdir(parents=True)
    schema.write_bytes((repo_root / schema.relative_to(target)).read_bytes())
    manifest = target / MANIFEST_PATH
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_bytes((repo_root / MANIFEST_PATH).read_bytes())


def _write_chunk(
    repository_root: Path,
    *,
    text: str = "승인된 합성 문서의 검색 근거입니다.",
) -> dict[str, object]:
    text_hash = "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()
    section_path = ["합성 문서", "검증 절"]
    chunk_id, _ = canonical_chunk_id(
        "DOC-SYN-CONTRACT-001", "1.0.0", section_path, 1, text_hash, "structure-ko-v1"
    )
    row: dict[str, object] = {
        "chunkId": chunk_id,
        "documentId": "DOC-SYN-CONTRACT-001",
        "versionLabel": "1.0.0",
        "heading": "검증 절",
        "sectionPath": section_path,
        "page": None,
        "pageStart": None,
        "pageEnd": None,
        "chunkOrder": 1,
        "text": text,
        "textHash": text_hash,
        "sourceHash": "sha256:232e71a1e03d58e8afd24e291ea341e67b7b6c302263f88a9ec06504dec3d653",
        "extractorVersion": "html-structure-v1",
        "chunkerVersion": "structure-ko-v1",
    }
    chunk_path = _chunk_path(repository_root)
    chunk_path.parent.mkdir(parents=True)
    chunk_path.write_text(json.dumps(row, ensure_ascii=False) + "\n", encoding="utf-8")
    return row


def _chunk_path(repository_root: Path) -> Path:
    return (
        repository_root
        / "ai-service/data/derived/chunks/DOC-SYN-CONTRACT-001-1.0.0.jsonl"
    )
