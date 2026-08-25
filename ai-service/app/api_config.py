from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass, field

from app.errors import KnowledgeContractError


@dataclass(frozen=True, slots=True)
class ApiConfig:
    internal_token: str = field(repr=False)

    @classmethod
    def from_environment(cls, environment: Mapping[str, str] | None = None) -> ApiConfig:
        values = os.environ if environment is None else environment
        token = values.get("ALZS_AI_INTERNAL_TOKEN", "")
        if len(token) < 32 or token.strip() != token:
            raise KnowledgeContractError("API_CONFIGURATION_INVALID")
        return cls(internal_token=token)
