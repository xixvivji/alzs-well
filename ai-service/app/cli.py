from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Sequence
from datetime import date
from pathlib import Path
from uuid import UUID

from app.domain.manifest import ensure_ingestion_eligible, governance_blocking_codes
from app.embedding.config import EmbeddingConfig, create_embedding_provider
from app.errors import KnowledgeContractError
from app.ingestion.chunker import chunk_document
from app.ingestion.html_extractor import extract_html_document
from app.ingestion.manifest_loader import load_and_validate_manifest
from app.ingestion.output_writer import write_chunks_jsonl
from app.ingestion.pdf_extractor import extract_pdf_document
from app.ingestion.pdf_validator import validate_pdf_source
from app.ingestion.repository import resolve_repository_root
from app.ingestion.source_validator import validate_source
from app.storage.database_config import DatabaseConfig
from app.storage.postgres import PostgresIngestionStore


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="alzs-ai", description="ALZ's well knowledge ingestion CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate-manifest", help="manifest와 원문 무결성을 검증합니다")
    validate.add_argument("--repo-root", help="명시적인 저장소 루트")
    validate.add_argument("--manifest", required=True, help="저장소 루트 기준 manifest 경로")

    extract = subparsers.add_parser("extract-html", help="승인된 HTML의 구조화 추출 결과를 검증합니다")
    extract.add_argument("--repo-root", help="명시적인 저장소 루트")
    extract.add_argument("--manifest", required=True, help="저장소 루트 기준 manifest 경로")
    extract.add_argument("--as-of", required=True, help="효력 기준일(YYYY-MM-DD)")

    ingest = subparsers.add_parser("ingest-html", help="승인된 HTML을 결정론적 chunk JSONL로 적재합니다")
    ingest.add_argument("--repo-root", help="명시적인 저장소 루트")
    ingest.add_argument("--manifest", required=True, help="저장소 루트 기준 manifest 경로")
    ingest.add_argument("--as-of", required=True, help="효력 기준일(YYYY-MM-DD)")
    ingest.add_argument(
        "--storage", choices=("jsonl", "postgres"), default="jsonl", help="파생 chunk 저장소"
    )

    validate_pdf = subparsers.add_parser("validate-pdf", help="승인된 PDF의 입력 보안 계약을 검증합니다")
    validate_pdf.add_argument("--repo-root", help="명시적인 저장소 루트")
    validate_pdf.add_argument("--manifest", required=True, help="저장소 루트 기준 manifest 경로")
    validate_pdf.add_argument("--as-of", required=True, help="효력 기준일(YYYY-MM-DD)")

    extract_pdf = subparsers.add_parser("extract-pdf", help="승인된 PDF의 페이지별 텍스트를 추출합니다")
    extract_pdf.add_argument("--repo-root", help="명시적인 저장소 루트")
    extract_pdf.add_argument("--manifest", required=True, help="저장소 루트 기준 manifest 경로")
    extract_pdf.add_argument("--as-of", required=True, help="효력 기준일(YYYY-MM-DD)")

    ingest_pdf = subparsers.add_parser("ingest-pdf", help="승인된 PDF를 페이지 추적 chunk JSONL로 적재합니다")
    ingest_pdf.add_argument("--repo-root", help="명시적인 저장소 루트")
    ingest_pdf.add_argument("--manifest", required=True, help="저장소 루트 기준 manifest 경로")
    ingest_pdf.add_argument("--as-of", required=True, help="효력 기준일(YYYY-MM-DD)")
    ingest_pdf.add_argument(
        "--storage", choices=("jsonl", "postgres"), default="jsonl", help="파생 chunk 저장소"
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    active_store: PostgresIngestionStore | None = None
    active_run_id: UUID | None = None
    try:
        repository_root = resolve_repository_root(args.repo_root)
        manifest = load_and_validate_manifest(repository_root, args.manifest)
        if args.command in {"validate-pdf", "extract-pdf", "ingest-pdf"}:
            as_of = _parse_as_of(args.as_of)
            ensure_ingestion_eligible(manifest, as_of=as_of)
            if args.command == "ingest-pdf" and args.storage == "postgres":
                active_store = _postgres_store()
                active_run_id = active_store.start_run(
                    document_id=manifest.document_id,
                    version_label=manifest.version_label,
                    source_hash=manifest.source_hash,
                    as_of=as_of,
                )
            source = validate_pdf_source(repository_root, manifest)
            if args.command in {"extract-pdf", "ingest-pdf"}:
                document = extract_pdf_document(manifest, source)
                if args.command == "ingest-pdf":
                    chunks = chunk_document(document)
                    output_path = None
                    completed_run_id = active_run_id
                    if active_store is not None and active_run_id is not None:
                        active_store.complete_run(active_run_id, chunks, document.warnings, manifest)
                        active_run_id = None
                    else:
                        output_path = write_chunks_jsonl(repository_root, chunks)
                    _write_json(
                        sys.stdout,
                        {
                            "ok": True,
                            "code": "PDF_INGESTION_COMPLETED",
                            "contractVersion": manifest.contract_version,
                            "documentId": document.document_id,
                            "versionLabel": document.version_label,
                            "extractorVersion": document.extractor_version,
                            "chunkerVersion": chunks[0].chunker_version,
                            "pageCount": source.page_count,
                            "chunkCount": len(chunks),
                            "warnings": list(document.warnings),
                            "storage": args.storage.upper(),
                            "runId": str(completed_run_id) if completed_run_id is not None else None,
                            "outputPath": (
                                output_path.relative_to(repository_root).as_posix()
                                if output_path is not None
                                else None
                            ),
                            "source": {"hashVerified": True, "sizeBytes": source.size_bytes},
                        },
                    )
                    return 0
                text_pages = {
                    block.page_start for block in document.blocks if block.page_start is not None
                }
                _write_json(
                    sys.stdout,
                    {
                        "ok": True,
                        "code": "PDF_EXTRACTION_COMPLETED",
                        "contractVersion": manifest.contract_version,
                        "documentId": document.document_id,
                        "versionLabel": document.version_label,
                        "title": document.title,
                        "extractorVersion": document.extractor_version,
                        "pageCount": source.page_count,
                        "textPageCount": len(text_pages),
                        "blockCount": len(document.blocks),
                        "sectionCount": len(document.section_paths),
                        "warnings": list(document.warnings),
                        "source": {"hashVerified": True, "sizeBytes": source.size_bytes},
                    },
                )
                return 0
            _write_json(
                sys.stdout,
                {
                    "ok": True,
                    "code": "PDF_VALIDATION_COMPLETED",
                    "contractVersion": manifest.contract_version,
                    "documentId": manifest.document_id,
                    "versionLabel": manifest.version_label,
                    "pageCount": source.page_count,
                    "encrypted": source.encrypted,
                    "activeContent": source.active_content,
                    "source": {"hashVerified": True, "sizeBytes": source.size_bytes},
                },
            )
            return 0
        if args.command in {"extract-html", "ingest-html"}:
            as_of = _parse_as_of(args.as_of)
            ensure_ingestion_eligible(manifest, as_of=as_of)
            if args.command == "ingest-html" and args.storage == "postgres":
                active_store = _postgres_store()
                active_run_id = active_store.start_run(
                    document_id=manifest.document_id,
                    version_label=manifest.version_label,
                    source_hash=manifest.source_hash,
                    as_of=as_of,
                )
            source = validate_source(repository_root, manifest)
            document = extract_html_document(manifest, source)
            if args.command == "ingest-html":
                chunks = chunk_document(document)
                output_path = None
                completed_run_id = active_run_id
                if active_store is not None and active_run_id is not None:
                    active_store.complete_run(active_run_id, chunks, document.warnings, manifest)
                    active_run_id = None
                else:
                    output_path = write_chunks_jsonl(repository_root, chunks)
                _write_json(
                    sys.stdout,
                    {
                        "ok": True,
                        "code": "HTML_INGESTION_COMPLETED",
                        "contractVersion": manifest.contract_version,
                        "documentId": document.document_id,
                        "versionLabel": document.version_label,
                        "extractorVersion": document.extractor_version,
                        "chunkerVersion": chunks[0].chunker_version,
                        "chunkCount": len(chunks),
                        "warnings": list(document.warnings),
                        "storage": args.storage.upper(),
                        "runId": str(completed_run_id) if completed_run_id is not None else None,
                        "outputPath": (
                            output_path.relative_to(repository_root).as_posix()
                            if output_path is not None
                            else None
                        ),
                        "source": {"hashVerified": True, "sizeBytes": source.size_bytes},
                    },
                )
                return 0
            _write_json(
                sys.stdout,
                {
                    "ok": True,
                    "code": "HTML_EXTRACTION_COMPLETED",
                    "contractVersion": manifest.contract_version,
                    "documentId": document.document_id,
                    "versionLabel": document.version_label,
                    "title": document.title,
                    "extractorVersion": document.extractor_version,
                    "blockCount": len(document.blocks),
                    "sectionCount": len(document.section_paths),
                    "warnings": list(document.warnings),
                    "source": {"hashVerified": True, "sizeBytes": source.size_bytes},
                },
            )
            return 0

        if Path(manifest.source_path).suffix.lower() == ".pdf":
            pdf_source = validate_pdf_source(repository_root, manifest)
            source_summary: dict[str, object] = {
                "hashVerified": True,
                "sizeBytes": pdf_source.size_bytes,
                "format": "PDF",
                "pageCount": pdf_source.page_count,
                "encrypted": pdf_source.encrypted,
                "activeContent": pdf_source.active_content,
            }
        else:
            source = validate_source(repository_root, manifest)
            source_summary = {
                "hashVerified": True,
                "sizeBytes": source.size_bytes,
                "format": "HTML",
                "encoding": source.encoding,
            }
        blockers = governance_blocking_codes(manifest)
        _write_json(
            sys.stdout,
            {
                "ok": True,
                "code": "MANIFEST_VALID",
                "contractVersion": manifest.contract_version,
                "documentId": manifest.document_id,
                "versionLabel": manifest.version_label,
                "approvalStatus": manifest.approval_status,
                "lifecycleStatus": manifest.lifecycle_status,
                "approvalAndLifecycleEligible": not blockers,
                "governanceBlockingCodes": blockers,
                "source": source_summary,
            },
        )
        return 0
    except KnowledgeContractError as error:
        if active_store is not None and active_run_id is not None:
            try:
                active_store.fail_run(active_run_id, error.code)
            except KnowledgeContractError:
                pass
        payload = {"ok": False, "code": error.code, "message": error.safe_message}
        if error.safe_context:
            payload["context"] = error.safe_context
        _write_json(sys.stderr, payload)
        return error.exit_code


def _parse_as_of(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID", {"schemaPath": "asOf"}) from None


def _postgres_store() -> PostgresIngestionStore:
    return PostgresIngestionStore(
        DatabaseConfig.from_environment(),
        embedding_provider=create_embedding_provider(EmbeddingConfig.from_environment()),
    )


def _write_json(stream: object, payload: dict[str, object]) -> None:
    print(json.dumps(payload, ensure_ascii=False, sort_keys=True), file=stream)


if __name__ == "__main__":
    raise SystemExit(main())
