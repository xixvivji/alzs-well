from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Sequence
from datetime import date

from app.errors import KnowledgeContractError
from app.evaluation.corpus_builder import build_evaluation_corpus
from app.ingestion.repository import resolve_repository_root


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="retrieval-corpus")
    parser.add_argument("--repo-root", help="명시적인 저장소 루트")
    parser.add_argument(
        "--manifest", action="append", required=True, help="승인된 manifest 경로(반복 가능)"
    )
    parser.add_argument("--as-of", required=True, help="효력 기준일(YYYY-MM-DD)")
    args = parser.parse_args(argv)
    try:
        repository_root = resolve_repository_root(args.repo_root)
        try:
            as_of = date.fromisoformat(args.as_of)
        except ValueError:
            raise KnowledgeContractError(
                "MANIFEST_SCHEMA_INVALID", {"schemaPath": "asOf"}
            ) from None
        result = build_evaluation_corpus(
            repository_root, tuple(args.manifest), as_of=as_of
        )
        print(json.dumps({
            "ok": True,
            "code": "EVALUATION_CORPUS_CREATED",
            "documentCount": result.document_count,
            "chunkCount": result.chunk_count,
            "outputPath": result.output_path.relative_to(repository_root).as_posix(),
        }, ensure_ascii=False, sort_keys=True))
        return 0
    except KnowledgeContractError as error:
        payload: dict[str, object] = {
            "ok": False, "code": error.code, "message": error.safe_message
        }
        if error.safe_context:
            payload["context"] = error.safe_context
        print(json.dumps(payload, ensure_ascii=False, sort_keys=True), file=sys.stderr)
        return error.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
