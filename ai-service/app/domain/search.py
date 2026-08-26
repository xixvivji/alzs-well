from __future__ import annotations

import unicodedata
from dataclasses import dataclass
from datetime import date
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


KnowledgeRole = Literal[
    "CUSTOMER",
    "PROTECTION_STAFF",
    "DETECTION_ADMIN",
    "COMPLIANCE_REVIEWER",
    "KNOWLEDGE_ADMIN",
    "SECURITY_ADMIN",
]
Audience = Literal["CUSTOMER", "STAFF"]


def _camel_case(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class SearchRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda value: _camel_case(value), extra="forbid", populate_by_name=True
    )

    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    query: str = Field(min_length=2, max_length=200)
    permissions: tuple[Literal["KNOWLEDGE_READ", "KNOWLEDGE_SEARCH"], ...] = Field(
        min_length=1
    )
    principal_roles: tuple[KnowledgeRole, ...] = Field(min_length=1)
    requester_audiences: tuple[Audience, ...] = Field(min_length=1)
    as_of: date
    limit: int = Field(default=10, ge=1, le=20)

    @field_validator("query")
    @classmethod
    def normalize_query(cls, value: str) -> str:
        normalized = unicodedata.normalize("NFC", " ".join(value.split()))
        if len(normalized) < 2 or not any(character.isalnum() for character in normalized):
            raise ValueError("query must contain searchable characters")
        return normalized

    @field_validator("permissions", "principal_roles", "requester_audiences")
    @classmethod
    def reject_duplicates(cls, values: tuple[str, ...]) -> tuple[str, ...]:
        if len(values) != len(set(values)):
            raise ValueError("duplicate values are not allowed")
        return values

    @model_validator(mode="after")
    def ensure_audience_matches_roles(self) -> SearchRequest:
        derived: set[str] = set()
        if "CUSTOMER" in self.principal_roles:
            derived.add("CUSTOMER")
        if set(self.principal_roles) & {
            "PROTECTION_STAFF",
            "DETECTION_ADMIN",
            "COMPLIANCE_REVIEWER",
            "KNOWLEDGE_ADMIN",
            "SECURITY_ADMIN",
        }:
            derived.add("STAFF")
        if set(self.requester_audiences) != derived:
            raise ValueError("requester audiences do not match principal roles")
        return self


class Citation(BaseModel):
    model_config = ConfigDict(alias_generator=lambda value: _camel_case(value), populate_by_name=True)

    contract_version: Literal["1.0.0"] = "1.0.0"
    document_id: str
    version_label: str
    chunk_id: str
    chunk_order: int
    title: str
    issuer: str
    heading: str
    section_path: tuple[str, ...]
    page: int | None
    citation_label: str
    source_url: str | None
    source_hash: str
    text_hash: str
    retrieved_as_of: date
    retrieval_method: Literal["HYBRID"] = "HYBRID"
    index_version: str = Field(
        ...,
        min_length=1,
        max_length=80,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$",
    )


class SearchResult(BaseModel):
    model_config = ConfigDict(alias_generator=lambda value: _camel_case(value), populate_by_name=True)

    score: float = Field(ge=0)
    content: str = Field(min_length=1, max_length=1200)
    citation: Citation


class SearchResponse(BaseModel):
    model_config = ConfigDict(alias_generator=lambda value: _camel_case(value), populate_by_name=True)

    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    query_hash: str
    results: tuple[SearchResult, ...]


@dataclass(frozen=True, slots=True)
class StoredSearchResult:
    document_id: str
    version_label: str
    chunk_id: str
    chunk_order: int
    title: str
    issuer: str
    heading: str
    section_path: tuple[str, ...]
    page: int | None
    source_url: str | None
    source_hash: str
    text_hash: str
    content: str
    score: float
