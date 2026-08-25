from __future__ import annotations

import pytest

from app.errors import KnowledgeContractError
from app.storage.database_config import DatabaseConfig


VALID_ENVIRONMENT = {
    "ALZS_AI_DB_HOST": "postgres",
    "ALZS_AI_DB_NAME": "alzs_well",
    "ALZS_AI_DB_USER": "alzswell_ai_ingestor",
    "ALZS_AI_DB_PASSWORD": "not-a-real-password",
}


def test_loads_database_configuration_with_safe_defaults() -> None:
    config = DatabaseConfig.from_environment(VALID_ENVIRONMENT)

    assert config.host == "postgres"
    assert config.port == 5432
    assert config.sslmode == "prefer"
    assert config.connect_timeout == 5
    assert "not-a-real-password" not in repr(config)


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("ALZS_AI_DB_PORT", "invalid"),
        ("ALZS_AI_DB_PORT", "0"),
        ("ALZS_AI_DB_PORT", "65536"),
        ("ALZS_AI_DB_CONNECT_TIMEOUT", "0"),
        ("ALZS_AI_DB_CONNECT_TIMEOUT", "31"),
        ("ALZS_AI_DB_SSLMODE", "unsafe-mode"),
    ],
)
def test_rejects_invalid_database_configuration(name: str, value: str) -> None:
    environment = {**VALID_ENVIRONMENT, name: value}

    with pytest.raises(KnowledgeContractError) as caught:
        DatabaseConfig.from_environment(environment)

    assert caught.value.code == "DATABASE_CONFIGURATION_INVALID"


def test_rejects_missing_secret_without_echoing_configuration() -> None:
    environment = {**VALID_ENVIRONMENT, "ALZS_AI_DB_PASSWORD": ""}

    with pytest.raises(KnowledgeContractError) as caught:
        DatabaseConfig.from_environment(environment)

    assert caught.value.code == "DATABASE_CONFIGURATION_INVALID"
    assert "not-a-real-password" not in caught.value.safe_message
