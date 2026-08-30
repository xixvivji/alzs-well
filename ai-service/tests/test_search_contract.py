from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator


def test_search_contract_schemas_are_valid_draft_2020_12(repo_root: Path) -> None:
    contract_root = repo_root / "contracts/knowledge"

    for name in ("search-request.schema.json", "search-response.schema.json"):
        schema = json.loads((contract_root / name).read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)


def test_search_request_fixture_satisfies_shared_schema(repo_root: Path) -> None:
    contract_root = repo_root / "contracts/knowledge"
    schema = json.loads((contract_root / "search-request.schema.json").read_text(encoding="utf-8"))
    payload = {
        "contractVersion": "1.0.0",
        "requestId": "99000000-0000-0000-0000-000000000001",
        "query": "금융거래 안심차단",
        "permissions": ["KNOWLEDGE_SEARCH"],
        "principalRoles": ["PROTECTION_STAFF"],
        "requesterAudiences": ["STAFF"],
        "asOf": "2026-08-25",
        "limit": 10,
    }

    Draft202012Validator(schema).validate(payload)
