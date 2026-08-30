from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass, field

from app.errors import KnowledgeContractError


REQUIRED_ENVIRONMENT = (
    "ALZS_AI_DB_HOST",
    "ALZS_AI_DB_NAME",
    "ALZS_AI_DB_USER",
    "ALZS_AI_DB_PASSWORD",
)
ALLOWED_SSL_MODES = {"disable", "allow", "prefer", "require", "verify-ca", "verify-full"}


@dataclass(frozen=True, slots=True)
class DatabaseConfig:
    host: str
    port: int
    dbname: str
    user: str
    password: str = field(repr=False)
    sslmode: str
    connect_timeout: int

    @classmethod
    def from_environment(
        cls, environment: Mapping[str, str] | None = None
    ) -> DatabaseConfig:
        values = os.environ if environment is None else environment
        if any(not values.get(name, "").strip() for name in REQUIRED_ENVIRONMENT):
            raise KnowledgeContractError("DATABASE_CONFIGURATION_INVALID")
        try:
            port = int(values.get("ALZS_AI_DB_PORT", "5432"))
            connect_timeout = int(values.get("ALZS_AI_DB_CONNECT_TIMEOUT", "5"))
        except ValueError:
            raise KnowledgeContractError("DATABASE_CONFIGURATION_INVALID") from None
        sslmode = values.get("ALZS_AI_DB_SSLMODE", "prefer").strip().lower()
        if (
            port < 1
            or port > 65_535
            or connect_timeout < 1
            or connect_timeout > 30
            or sslmode not in ALLOWED_SSL_MODES
        ):
            raise KnowledgeContractError("DATABASE_CONFIGURATION_INVALID")
        return cls(
            host=values["ALZS_AI_DB_HOST"].strip(),
            port=port,
            dbname=values["ALZS_AI_DB_NAME"].strip(),
            user=values["ALZS_AI_DB_USER"].strip(),
            password=values["ALZS_AI_DB_PASSWORD"],
            sslmode=sslmode,
            connect_timeout=connect_timeout,
        )
