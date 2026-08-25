from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Sequence
from datetime import date

from app.domain.manifest import ensure_ingestion_eligible, governance_blocking_codes
from app.errors import KnowledgeContractError
from app.ingestion.chunker import chunk_document
from app.ingestion.html_extractor import extract_html_document
from app.ingestion.manifest_loader import load_and_validate_manifest
from app.ingestion.output_writer import write_chunks_jsonl
from app.ingestion.pdf_validator import validate_pdf_source
from app.ingestion.repository import resolve_repository_root
from app.ingestion.source_validator import validate_source


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

    validate_pdf = subparsers.add_parser("validate-pdf", help="승인된 PDF의 입력 보안 계약을 검증합니다")
    validate_pdf.add_argument("--repo-root", help="명시적인 저장소 루트")
    validate_pdf.add_argument("--manifest", required=True, help="저장소 루트 기준 manifest 경로")
    validate_pdf.add_argument("--as-of", required=True, help="효력 기준일(YYYY-MM-DD)")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        repository_root = resolve_repository_root(args.repo_root)
        manifest = load_and_validate_manifest(repository_root, args.manifest)
        if args.command == "validate-pdf":
            as_of = _parse_as_of(args.as_of)
            ensure_ingestion_eligible(manifest, as_of=as_of)
            source = validate_pdf_source(repository_root, manifest)
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
            source = validate_source(repository_root, manifest)
            document = extract_html_document(manifest, source)
            if args.command == "ingest-html":
                chunks = chunk_document(document)
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
                        "outputPath": output_path.relative_to(repository_root).as_posix(),
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

        source = validate_source(repository_root, manifest)
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
                "source": {
                    "hashVerified": True,
                    "sizeBytes": source.size_bytes,
                    "encoding": source.encoding,
                },
            },
        )
        return 0
    except KnowledgeContractError as error:
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


def _write_json(stream: object, payload: dict[str, object]) -> None:
    print(json.dumps(payload, ensure_ascii=False, sort_keys=True), file=stream)


if __name__ == "__main__":
    raise SystemExit(main())
