from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Any, Mapping

from app.errors import KnowledgeContractError


@dataclass(frozen=True, slots=True)
class KnowledgeManifest:
    payload: Mapping[str, Any]

    @property
    def contract_version(self) -> str:
        return str(self.payload["contractVersion"])

    @property
    def document_id(self) -> str:
        return str(self.payload["documentId"])

    @property
    def version_label(self) -> str:
        return str(self.payload["versionLabel"])

    @property
    def title(self) -> str:
        return str(self.payload["title"])

    @property
    def issuer(self) -> str:
        return str(self.payload["issuer"])

    @property
    def source_url(self) -> str | None:
        value = self.payload["sourceUrl"]
        return None if value is None else str(value)

    @property
    def source_path(self) -> str:
        return str(self.payload["sourcePath"])

    @property
    def source_hash(self) -> str:
        return str(self.payload["sourceHash"])

    @property
    def document_type(self) -> str:
        return str(self.payload["documentType"])

    @property
    def approval_status(self) -> str:
        return str(self.payload["approvalStatus"])

    @property
    def lifecycle_status(self) -> str:
        return str(self.payload["lifecycleStatus"])

    @property
    def classification(self) -> str:
        return str(self.payload["classification"])

    @property
    def audience(self) -> str:
        return str(self.payload["audience"])

    @property
    def allowed_roles(self) -> tuple[str, ...]:
        return tuple(str(role) for role in self.payload["allowedRoles"])

    @property
    def effective_from(self) -> date:
        return date.fromisoformat(str(self.payload["effectiveFrom"]))

    @property
    def effective_to(self) -> date | None:
        value = self.payload["effectiveTo"]
        return None if value is None else date.fromisoformat(str(value))

    @property
    def source_transformations(self) -> tuple[Mapping[str, Any], ...]:
        return tuple(self.payload["sourceTransformations"])


def governance_blocking_codes(manifest: KnowledgeManifest, as_of: date | None = None) -> list[str]:
    codes: list[str] = []
    if manifest.approval_status != "APPROVED":
        codes.append("DOCUMENT_NOT_APPROVED")
    if manifest.lifecycle_status != "ACTIVE":
        codes.append("DOCUMENT_NOT_ACTIVE")
    if as_of is not None and not is_effective(manifest, as_of):
        codes.append("DOCUMENT_NOT_EFFECTIVE")
    return codes


def ensure_ingestion_eligible(manifest: KnowledgeManifest, *, as_of: date) -> None:
    codes = governance_blocking_codes(manifest, as_of)
    if codes:
        raise KnowledgeContractError(codes[0])


def is_effective(manifest: KnowledgeManifest, as_of: date) -> bool:
    if manifest.effective_from > as_of:
        return False
    return manifest.effective_to is None or manifest.effective_to >= as_of
