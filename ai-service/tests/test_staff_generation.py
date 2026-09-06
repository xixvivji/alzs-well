import json
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.assistance.copilot import DraftRequest, generate_draft, BedrockProvider, _slots
from app.main import _api_config, create_app


def payload():
    return DraftRequest(syntheticData=True, reasonCodes=["DUPLICATE_TRANSFER"],
                        customerResponseCode="UNSURE", passages=["고객 의사를 먼저 확인합니다."])


@pytest.fixture
def enabled(monkeypatch):
    monkeypatch.setenv("ALZS_COPILOT_PROVIDER", "bedrock")
    monkeypatch.setenv("ALZS_BEDROCK_SYNTHETIC_EGRESS_ALLOWED", "true")
    monkeypatch.setenv("ALZS_BEDROCK_MODEL_ID", "test-model")
    monkeypatch.setenv("ALZS_BEDROCK_REGION", "ap-northeast-2")


def valid_text():
    return json.dumps(dict(summary="고객 확인이 필요합니다.",
                           suggestedQuestions=["송금 목적을 기억하시나요?"],
                           checklist=["고객 의사를 확인하세요."]), ensure_ascii=False)


@pytest.mark.parametrize("key,value", [("ALZS_COPILOT_PROVIDER", "template"),
    ("ALZS_BEDROCK_SYNTHETIC_EGRESS_ALLOWED", "false"), ("ALZS_BEDROCK_MODEL_ID", ""),
    ("ALZS_BEDROCK_REGION", "")])
def test_disabled_never_invokes(enabled, monkeypatch, key, value):
    monkeypatch.setenv(key, value)
    provider = Mock()
    assert not generate_draft(payload(), provider).modelInvoked
    provider.generate.assert_not_called()


def test_valid_draft(enabled):
    result = generate_draft(payload(), Mock(generate=Mock(return_value=valid_text())))
    assert result.generatedBy == "BEDROCK_GENERATIVE_DRAFT"
    assert result.modelInvoked and result.externalEgressAttempted and not result.fallbackUsed


@pytest.mark.parametrize("raw", ["not json", "x" * 12001, '{"summary":"x"}',
    valid_text().replace("송금", "HTTPS://bad.example 송금"),
    valid_text().replace("송금", "<script>송금")])
def test_bad_output_falls_back(enabled, raw):
    result = generate_draft(payload(), Mock(generate=Mock(return_value=raw)))
    assert result.fallbackUsed and result.draft is None


def test_failure_and_slots(enabled):
    assert generate_draft(payload(), Mock(generate=Mock(side_effect=TimeoutError("secret")))).fallbackUsed
    _slots.acquire()
    _slots.acquire()
    try:
        assert not generate_draft(payload(), Mock()).modelInvoked
    finally:
        _slots.release()
        _slots.release()


def test_identifiers_not_sent(enabled):
    request = payload().model_copy(update={"passages": ["person@example.com"]})
    provider = Mock()
    assert not generate_draft(request, provider).modelInvoked
    provider.generate.assert_not_called()


def test_real_data_flag_rejected():
    with pytest.raises(ValidationError):
        DraftRequest.model_validate({**payload().model_dump(), "syntheticData": False})


def test_internal_auth(monkeypatch):
    monkeypatch.setenv("ALZS_AI_INTERNAL_TOKEN", "t" * 40)
    monkeypatch.setenv("ALZS_COPILOT_PROVIDER", "template")
    _api_config.cache_clear()
    try:
        client = TestClient(create_app())
        assert client.post("/internal/v1/copilot-draft", json=payload().model_dump()).status_code == 401
        response = client.post("/internal/v1/copilot-draft", json=payload().model_dump(),
                               headers={"X-Internal-Service-Token": "t" * 40})
        assert response.status_code == 200
        assert response.json()["fallbackUsed"]
    finally:
        _api_config.cache_clear()


def test_converse_contract(enabled, monkeypatch):
    import sys
    client = Mock()
    client.converse.return_value = {"stopReason": "end_turn", "output": {"message": {"content": [{"text": valid_text()}]}}}
    boto = Mock(client=Mock(return_value=client))
    monkeypatch.setitem(sys.modules, "boto3", boto)
    monkeypatch.setitem(sys.modules, "botocore.config", Mock())
    assert BedrockProvider().generate(payload()) == valid_text()
    assert client.converse.call_args.kwargs["modelId"] == "test-model"
    assert "tools" not in client.converse.call_args.kwargs
    client.converse.return_value = {"stopReason": "max_tokens"}
    with pytest.raises(ValueError):
        BedrockProvider().generate(payload())
