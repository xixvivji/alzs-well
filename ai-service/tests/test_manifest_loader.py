from __future__ import annotations

from datetime import date
from pathlib import Path

import pytest

from app.domain.manifest import ensure_ingestion_eligible, governance_blocking_codes
from app.errors import KnowledgeContractError
from app.ingestion.manifest_loader import (
    _load_yaml_12,
    _read_utf8_manifest,
    _validate_semantics,
    _validate_transformations,
    load_and_validate_manifest,
)


def test_loads_approved_active_synthetic_manifest(repo_root: Path) -> None:
    manifest = load_and_validate_manifest(
        repo_root,
        "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
    )

    assert manifest.document_id == "DOC-SYN-CONTRACT-001"
    assert manifest.approval_status == "APPROVED"
    assert manifest.lifecycle_status == "ACTIVE"
    assert governance_blocking_codes(manifest) == []
    ensure_ingestion_eligible(manifest, as_of=date(2026, 8, 21))


def test_real_manifest_is_valid_but_not_ingestion_eligible(repo_root: Path) -> None:
    manifest = load_and_validate_manifest(
        repo_root,
        "knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml",
    )

    assert governance_blocking_codes(manifest) == ["DOCUMENT_NOT_APPROVED", "DOCUMENT_NOT_ACTIVE"]
    with pytest.raises(KnowledgeContractError) as raised:
        ensure_ingestion_eligible(manifest, as_of=date(2026, 8, 21))
    assert raised.value.code == "DOCUMENT_NOT_APPROVED"


def test_all_official_manifests_conform_to_the_shared_contract(repo_root: Path) -> None:
    manifest_paths = sorted((repo_root / "knowledge" / "manifests").glob("*.yaml"))

    assert manifest_paths
    for path in manifest_paths:
        manifest = load_and_validate_manifest(repo_root, path.relative_to(repo_root))
        assert manifest.source_path.startswith("knowledge/official-source/")
        assert (repo_root / manifest.source_path).is_file()


def test_current_telecom_fraud_law_manifests_are_versioned_and_approved(
    repo_root: Path,
) -> None:
    act = load_and_validate_manifest(
        repo_root,
        "knowledge/manifests/DOC-LAW-TELECOM-FRAUD-REFUND-ACT-001.yaml",
    )
    decree = load_and_validate_manifest(
        repo_root,
        "knowledge/manifests/DOC-REG-TELECOM-FRAUD-REFUND-DECREE-001.yaml",
    )

    assert act.document_type == "LAW"
    assert act.effective_from == date(2026, 8, 4)
    assert act.effective_to == date(2026, 9, 30)
    assert decree.document_type == "REGULATION"
    assert decree.effective_from == date(2026, 8, 4)
    assert decree.effective_to is None
    assert governance_blocking_codes(act, date(2026, 8, 26)) == []
    assert governance_blocking_codes(decree, date(2026, 8, 26)) == []


@pytest.mark.parametrize(
    ("fixture", "expected_code"),
    [
        ("contracts/knowledge/fixtures/synthetic-invalid/active-without-approval.yaml", "MANIFEST_SCHEMA_INVALID"),
        ("contracts/knowledge/fixtures/synthetic-invalid/malformed-source-hash.yaml", "MANIFEST_SCHEMA_INVALID"),
        ("contracts/knowledge/fixtures/synthetic-invalid/absolute-source-path.yaml", "MANIFEST_SCHEMA_INVALID"),
    ],
)
def test_rejects_schema_invalid_fixtures(repo_root: Path, fixture: str, expected_code: str) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        load_and_validate_manifest(repo_root, fixture)
    assert raised.value.code == expected_code


def test_rejects_duplicate_yaml_keys(repo_root: Path) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        load_and_validate_manifest(
            repo_root,
            "contracts/knowledge/fixtures/synthetic-invalid/duplicate-key.yaml",
        )
    assert raised.value.code == "MANIFEST_DUPLICATE_KEY"


@pytest.mark.parametrize(
    "yaml_text",
    [
        "base: &base\n  value: 1\ncopy: *base\n",
        "value: !unsafe payload\n",
    ],
)
def test_rejects_yaml_alias_anchor_and_custom_tag(yaml_text: str) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        _load_yaml_12(yaml_text)
    assert raised.value.code in {"MANIFEST_ALIAS_FORBIDDEN", "MANIFEST_SCHEMA_INVALID"}


def test_yaml_dates_remain_strings() -> None:
    payload = _load_yaml_12("effectiveFrom: 2026-08-21\napprovedAt: 2026-08-21T00:00:00Z\n")
    assert payload == {
        "effectiveFrom": "2026-08-21",
        "approvedAt": "2026-08-21T00:00:00Z",
    }


def test_rejects_document_outside_effective_period(repo_root: Path) -> None:
    manifest = load_and_validate_manifest(
        repo_root,
        "contracts/knowledge/fixtures/synthetic-approved-active.yaml",
    )
    with pytest.raises(KnowledgeContractError) as raised:
        ensure_ingestion_eligible(manifest, as_of=date(2026, 8, 20))
    assert raised.value.code == "DOCUMENT_NOT_EFFECTIVE"


def test_rejects_effective_to_before_effective_from() -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        _validate_semantics({"effectiveFrom": "2026-08-21", "effectiveTo": "2026-08-20"})
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"
    assert raised.value.safe_context == {"schemaPath": "effectiveTo"}


def test_rejects_unsupported_source_transformation() -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        _validate_transformations(
            {
                "sourceTransformations": [
                    {
                        "type": "CREDENTIAL_REDACTION",
                        "ruleId": "UNKNOWN_RULE",
                        "replacement": "REDACTED_SOURCE_CREDENTIAL",
                    }
                ]
            }
        )
    assert raised.value.code == "SOURCE_TRANSFORMATION_UNSUPPORTED"


def test_ignores_missing_transformation_list_until_schema_validation() -> None:
    _validate_transformations({})


def test_rejects_non_mapping_yaml_manifest(repo_root: Path, tmp_path: Path) -> None:
    contract_dir = tmp_path / "contracts" / "knowledge"
    contract_dir.mkdir(parents=True)
    contract_dir.joinpath("manifest.schema.json").write_bytes(
        (repo_root / "contracts/knowledge/manifest.schema.json").read_bytes()
    )
    manifest = tmp_path / "manifest.yaml"
    manifest.write_text("- not\n- a\n- mapping\n", encoding="utf-8")
    with pytest.raises(KnowledgeContractError) as raised:
        load_and_validate_manifest(tmp_path, "manifest.yaml")
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"


def test_rejects_unsupported_contract_version(repo_root: Path, tmp_path: Path) -> None:
    contract_dir = tmp_path / "contracts" / "knowledge"
    contract_dir.mkdir(parents=True)
    contract_dir.joinpath("manifest.schema.json").write_bytes(
        (repo_root / "contracts/knowledge/manifest.schema.json").read_bytes()
    )
    fixture = (repo_root / "contracts/knowledge/fixtures/synthetic-approved-active.yaml").read_text(encoding="utf-8")
    tmp_path.joinpath("manifest.yaml").write_text(
        fixture.replace('contractVersion: "1.0.0"', 'contractVersion: "2.0.0"'),
        encoding="utf-8",
    )
    with pytest.raises(KnowledgeContractError) as raised:
        load_and_validate_manifest(tmp_path, "manifest.yaml")
    assert raised.value.code == "CONTRACT_VERSION_UNSUPPORTED"


def test_rejects_non_utf8_manifest(tmp_path: Path) -> None:
    manifest = tmp_path / "manifest.yaml"
    manifest.write_bytes(b"title: \xff")
    with pytest.raises(KnowledgeContractError) as raised:
        _read_utf8_manifest(manifest)
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"


def test_rejects_unreadable_manifest_path(tmp_path: Path) -> None:
    with pytest.raises(KnowledgeContractError) as raised:
        _read_utf8_manifest(tmp_path / "missing.yaml")
    assert raised.value.code == "MANIFEST_SCHEMA_INVALID"
