from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


def test_synthetic_ingestion_bundle_satisfies_shared_schema(repo_root: Path) -> None:
    schema = json.loads(
        (repo_root / "contracts/knowledge/ingestion-import.schema.json").read_text(
            encoding="utf-8"
        )
    )
    Draft202012Validator.check_schema(schema)
    payload = {
        "contractVersion": "1.0.0",
        "ingestionRunId": "97000000-0000-0000-0000-000000000063",
        "documentId": "DOC-SYN-CONTRACT-001",
        "versionLabel": "1.0.0",
        "sourceHash": "sha256:232e71a1e03d58e8afd24e291ea341e67b7b6c302263f88a9ec06504dec3d653",
        "asOf": "2026-08-21",
        "extractorVersion": "html-structure-v1",
        "chunkerVersion": "structure-ko-v1",
        "chunks": [
            {
                "chunkId": "chk_65b4505aedbcfff7fc3ffbd6775a65e312f18e9fd22f1104e5bfcc6bb6e3ba38",
                "chunkOrder": 1,
                "heading": "신청 방법",
                "sectionPath": ["합성 안심 안내", "신청 방법"],
                "page": None,
                "pageStart": None,
                "pageEnd": None,
                "text": "이 문서는 계약 검증을 위한 합성 자료입니다.",
                "textHash": "sha256:237c5ec148b7417f1c4d0fa8010ceae541aa2b26766bbf516e6bd72b67afdb2c",
                "sourceHash": "sha256:232e71a1e03d58e8afd24e291ea341e67b7b6c302263f88a9ec06504dec3d653",
                "extractorVersion": "html-structure-v1",
                "chunkerVersion": "structure-ko-v1",
            }
        ],
    }

    Draft202012Validator(schema, format_checker=FormatChecker()).validate(payload)
