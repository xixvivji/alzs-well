from __future__ import annotations

from pathlib import Path

import pytest

from app.errors import ERROR_EXIT_CODES, KnowledgeContractError
from app.ingestion.manifest_loader import _load_yaml_12


def test_python_error_codes_are_defined_by_shared_contract(repo_root: Path) -> None:
    payload = _load_yaml_12((repo_root / "contracts/knowledge/error-codes.yaml").read_text(encoding="utf-8"))
    contract_codes = {item["code"]: item["cliExitCode"] for item in payload["errors"]}
    assert set(ERROR_EXIT_CODES).issubset(contract_codes)
    assert {code: contract_codes[code] for code in ERROR_EXIT_CODES} == ERROR_EXIT_CODES


def test_unknown_error_code_is_programming_error() -> None:
    with pytest.raises(ValueError, match="unknown knowledge error code"):
        KnowledgeContractError("UNKNOWN")
