from __future__ import annotations

import pytest

from app.api_config import ApiConfig
from app.errors import KnowledgeContractError


def test_reads_internal_token_without_exposing_it() -> None:
    token = "internal-token-that-is-longer-than-thirty-two-characters"

    config = ApiConfig.from_environment({"ALZS_AI_INTERNAL_TOKEN": token})

    assert config.internal_token == token
    assert token not in repr(config)


@pytest.mark.parametrize("token", ["", "short", " token-that-is-long-enough-but-has-space "])
def test_rejects_missing_short_or_padded_internal_token(token: str) -> None:
    with pytest.raises(KnowledgeContractError) as caught:
        ApiConfig.from_environment({"ALZS_AI_INTERNAL_TOKEN": token})

    assert caught.value.code == "API_CONFIGURATION_INVALID"
