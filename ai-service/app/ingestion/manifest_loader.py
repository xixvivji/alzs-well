from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker
from ruamel.yaml import YAML
from ruamel.yaml.constructor import ConstructorError, DuplicateKeyError
from ruamel.yaml.error import YAMLError
from ruamel.yaml.events import AliasEvent

from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.repository import CONTRACT_SCHEMA_PATH, resolve_manifest_file


SUPPORTED_CONTRACT_VERSION = "1.0.0"
SUPPORTED_TRANSFORMATION = {
    "type": "CREDENTIAL_REDACTION",
    "ruleId": "PUBLIC_WEB_CREDENTIAL_REDACTION_V1",
    "replacement": "REDACTED_SOURCE_CREDENTIAL",
}


def load_and_validate_manifest(repository_root: Path, manifest_path: str | Path) -> KnowledgeManifest:
    resolved_manifest = resolve_manifest_file(repository_root, manifest_path)
    raw_text = _read_utf8_manifest(resolved_manifest)
    payload = _load_yaml_12(raw_text)
    if not isinstance(payload, dict):
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID")

    contract_version = payload.get("contractVersion")
    if contract_version is not None and contract_version != SUPPORTED_CONTRACT_VERSION:
        raise KnowledgeContractError("CONTRACT_VERSION_UNSUPPORTED")
    _validate_transformations(payload)
    _validate_schema(repository_root, payload)
    _validate_semantics(payload)
    return KnowledgeManifest(payload=payload)


def _read_utf8_manifest(path: Path) -> str:
    try:
        raw = path.read_bytes()
        return raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID") from None
    except OSError:
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID") from None


def _load_yaml_12(raw_text: str) -> Any:
    yaml = YAML(typ="safe", pure=True)
    yaml.version = (1, 2)
    yaml.allow_duplicate_keys = False
    yaml.constructor.add_constructor(
        "tag:yaml.org,2002:timestamp",
        lambda constructor, node: constructor.construct_scalar(node),
    )
    try:
        for event in yaml.parse(raw_text):
            if isinstance(event, AliasEvent) or getattr(event, "anchor", None) is not None:
                raise KnowledgeContractError("MANIFEST_ALIAS_FORBIDDEN")
            tag = getattr(event, "tag", None)
            if tag is not None and not str(tag).startswith("tag:yaml.org,2002:"):
                raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID")
        return yaml.load(raw_text)
    except KnowledgeContractError:
        raise
    except DuplicateKeyError:
        raise KnowledgeContractError("MANIFEST_DUPLICATE_KEY") from None
    except (ConstructorError, YAMLError):
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID") from None


def _validate_schema(repository_root: Path, payload: dict[str, Any]) -> None:
    try:
        schema = json.loads((repository_root / CONTRACT_SCHEMA_PATH).read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        errors = sorted(validator.iter_errors(payload), key=lambda item: tuple(str(p) for p in item.path))
    except (OSError, json.JSONDecodeError):
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID") from None
    if errors:
        first = errors[0]
        schema_path = "/".join(str(part) for part in first.absolute_schema_path)
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID", {"schemaPath": schema_path})


def _validate_semantics(payload: dict[str, Any]) -> None:
    effective_from = date.fromisoformat(payload["effectiveFrom"])
    effective_to = payload["effectiveTo"]
    if effective_to is not None and date.fromisoformat(effective_to) < effective_from:
        raise KnowledgeContractError("MANIFEST_SCHEMA_INVALID", {"schemaPath": "effectiveTo"})


def _validate_transformations(payload: dict[str, Any]) -> None:
    transformations = payload.get("sourceTransformations")
    if not isinstance(transformations, list):
        return
    for transformation in transformations:
        if isinstance(transformation, dict) and transformation != SUPPORTED_TRANSFORMATION:
            raise KnowledgeContractError("SOURCE_TRANSFORMATION_UNSUPPORTED")
