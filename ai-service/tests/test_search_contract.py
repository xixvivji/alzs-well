from __future__ import annotations

import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator
from jsonschema.exceptions import ValidationError as JsonSchemaValidationError
from pydantic import ValidationError as PydanticValidationError

from app.domain.search import SearchResponse


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


def test_search_response_outcomes_satisfy_shared_schema(repo_root: Path) -> None:
    contract_root = repo_root / "contracts/knowledge"
    schema = json.loads((contract_root / "search-response.schema.json").read_text(encoding="utf-8"))
    base = {
        "contractVersion": "1.0.0",
        "requestId": "99000000-0000-0000-0000-000000000001",
        "queryHash": "sha256:" + "1" * 64,
        "retryable": False,
        "results": [],
    }

    Draft202012Validator(schema).validate(
        {**base, "outcome": "POLICY_ABSTAIN", "reasonCode": "POLICY_GUARDRAIL"}
    )
    Draft202012Validator(schema).validate(
        {**base, "outcome": "NO_MATCH", "reasonCode": "NO_RELEVANT_MATCH"}
    )
    Draft202012Validator(schema).validate(
        {
            **base,
            "outcome": "INDEX_UNAVAILABLE",
            "retryable": True,
            "reasonCode": "SEARCH_TIMEOUT",
        }
    )


@pytest.mark.parametrize(
    "invalid_fields",
    [
        {"outcome": "RESULTS", "reasonCode": None},
        {
            "outcome": "POLICY_ABSTAIN",
            "reasonCode": "POLICY_GUARDRAIL",
            "retryable": True,
        },
        {
            "outcome": "INDEX_UNAVAILABLE",
            "reasonCode": "POLICY_GUARDRAIL",
            "retryable": True,
        },
    ],
)
def test_search_response_schema_rejects_inconsistent_outcomes(
    repo_root: Path,
    invalid_fields: dict[str, object],
) -> None:
    schema = json.loads(
        (repo_root / "contracts/knowledge/search-response.schema.json").read_text(
            encoding="utf-8"
        )
    )
    payload = {
        "contractVersion": "1.0.0",
        "requestId": "99000000-0000-0000-0000-000000000001",
        "queryHash": "sha256:" + "1" * 64,
        "retryable": False,
        "results": [],
        **invalid_fields,
    }

    with pytest.raises(JsonSchemaValidationError):
        Draft202012Validator(schema).validate(payload)


@pytest.mark.parametrize(
    ("outcome", "retryable", "reason_code"),
    [
        ("RESULTS", False, None),
        ("POLICY_ABSTAIN", True, "POLICY_GUARDRAIL"),
        ("NO_MATCH", False, "POLICY_GUARDRAIL"),
        ("INDEX_UNAVAILABLE", False, "SEARCH_TIMEOUT"),
    ],
)
def test_runtime_model_rejects_inconsistent_empty_outcomes(
    outcome: str,
    retryable: bool,
    reason_code: str | None,
) -> None:
    with pytest.raises(PydanticValidationError):
        SearchResponse.model_validate(
            {
                "requestId": "99000000-0000-0000-0000-000000000001",
                "queryHash": "sha256:" + "1" * 64,
                "outcome": outcome,
                "retryable": retryable,
                "reasonCode": reason_code,
                "results": [],
            }
        )
