from __future__ import annotations

from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import _api_config, create_app, get_embedding_config, get_embedding_provider


TOKEN = "test-internal-token-that-is-longer-than-32-characters"


def test_structures_intent_with_evidence_and_safe_boundaries(monkeypatch: object) -> None:
    client = _client(monkeypatch)
    response = client.post(
        "/internal/v1/intent-structure",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "utterance": "공과금은 계속 납부하고 쉬운 말로 천천히 설명해 주세요.",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["suggestion"]["paymentContinuity"] == "KEEP_ESSENTIAL_PAYMENTS"
    assert body["suggestion"]["explanationMode"] == "SIMPLE_TEXT"
    assert body["suggestion"]["shareScopes"] == []
    assert body["needsClarification"] is True
    assert body["healthInferenceUsed"] is False
    assert body["financialActionExecuted"] is False
    assert len(body["evidence"]) == 4


def test_intent_respects_explicit_no_sharing(monkeypatch: object) -> None:
    client = _client(monkeypatch)
    response = client.post(
        "/internal/v1/intent-structure",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "utterance": "필수 납부는 유지하고 행원에게는 공유하지 말아 주세요.",
        },
    )
    assert response.status_code == 200
    assert response.json()["suggestion"]["shareScopes"] == []


def test_intent_does_not_misclassify_payment_stop_negation_as_keep(
    monkeypatch: object,
) -> None:
    client = _client(monkeypatch)
    response = client.post(
        "/internal/v1/intent-structure",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "utterance": "공과금은 더 이상 납부하지 말아 주세요.",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["suggestion"]["paymentContinuity"] == "REVIEW_BEFORE_CHANGE"
    assert body["needsClarification"] is True
    assert "중단하려는 뜻인지" in body["clarifyingQuestions"][0]
    assert body["financialActionExecuted"] is False


def test_intent_treats_negated_stop_and_change_as_keep(monkeypatch: object) -> None:
    client = _client(monkeypatch)

    for utterance in (
        "공과금 납부를 중단하지 말아 주세요.",
        "보험료 납부 방식은 바꾸지 말아 주세요.",
    ):
        response = client.post(
            "/internal/v1/intent-structure",
            headers=_headers(),
            json={"requestId": str(uuid4()), "utterance": utterance},
        )

        assert response.status_code == 200
        body = response.json()
        assert body["suggestion"]["paymentContinuity"] == "KEEP_ESSENTIAL_PAYMENTS"
        assert all("중단하려는 뜻인지" not in question for question in body["clarifyingQuestions"])


def test_detects_persistent_longitudinal_change(monkeypatch: object) -> None:
    client = _client(monkeypatch)
    values = [0.0] * 60 + [0.0] * 20 + [1.0] * 7 + [0.0] * 3
    response = client.post(
        "/internal/v1/change-analysis",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "baselineDays": 60,
            "recentDays": 30,
            "features": [{"featureCode": "REPEATED_CONFIRMATION_COUNT", "dailyValues": values}],
        },
    )
    assert response.status_code == 200
    change = response.json()["changes"][0]
    assert change["recentValue"] == 7.0
    assert change["changeDetected"] is True
    assert change["persistent"] is True
    assert response.json()["diagnosisInferred"] is False


def test_detects_persistent_longitudinal_decrease_with_directional_explanation(
    monkeypatch: object,
) -> None:
    client = _client(monkeypatch)
    values = [1.0] * 60 + [0.0] * 30
    response = client.post(
        "/internal/v1/change-analysis",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "baselineDays": 60,
            "recentDays": 30,
            "features": [{"featureCode": "MISSED_RECURRING_COUNT", "dailyValues": values}],
        },
    )

    assert response.status_code == 200
    change = response.json()["changes"][0]
    assert change["direction"] == "DECREASE"
    assert change["changeDetected"] is True
    assert change["persistent"] is True
    assert change["cusumScore"] >= 3.0
    assert "지속적으로 감소했습니다" in change["explanation"]
    assert response.json()["diagnosisInferred"] is False


def test_detects_persistent_decrease_for_sparse_nonnegative_count_series(
    monkeypatch: object,
) -> None:
    client = _client(monkeypatch)
    baseline = ([1.0] + [0.0] * 9) * 6
    response = client.post(
        "/internal/v1/change-analysis",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "features": [
                {
                    "featureCode": "DUPLICATE_TRANSFER_COUNT",
                    "dailyValues": baseline + [0.0] * 30,
                }
            ],
        },
    )

    assert response.status_code == 200
    change = response.json()["changes"][0]
    assert change["baselineValue"] == 3.0
    assert change["recentValue"] == 0.0
    assert change["direction"] == "DECREASE"
    assert change["changeDetected"] is True
    assert change["persistent"] is True
    assert "지속적으로 감소했습니다" in change["explanation"]


def test_reports_stable_series_without_false_alarm(monkeypatch: object) -> None:
    client = _client(monkeypatch)
    response = client.post(
        "/internal/v1/change-analysis",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "features": [{"featureCode": "DUPLICATE_TRANSFER_COUNT", "dailyValues": [0.0] * 90}],
        },
    )
    assert response.status_code == 200
    assert response.json()["changes"][0]["changeDetected"] is False


def test_generates_plain_language_without_diagnosis_or_action(monkeypatch: object) -> None:
    client = _client(monkeypatch)
    response = client.post(
        "/internal/v1/plain-language",
        headers=_headers(),
        json={
            "requestId": str(uuid4()),
            "explanationMode": "VOICE_AND_TEXT",
            "fact": {
                "featureCode": "REPEATED_CONFIRMATION_COUNT",
                "baselineValue": 2,
                "recentValue": 8,
                "recentDays": 30,
            },
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert "2회에서 8회" in body["text"]
    assert body["generationMode"] == "CONSTRAINED_NLG_V1"
    assert body["modelInvoked"] is False
    assert body["diagnosisInferred"] is False


def test_assistance_endpoints_require_internal_token_and_safe_validation(monkeypatch: object) -> None:
    client = _client(monkeypatch)
    unauthorized = client.post(
        "/internal/v1/intent-structure",
        json={"requestId": str(uuid4()), "utterance": "공과금을 유지해 주세요"},
    )
    assert unauthorized.status_code == 401
    invalid = client.post(
        "/internal/v1/change-analysis",
        headers=_headers(),
        json={"requestId": str(uuid4()), "features": []},
    )
    assert invalid.status_code == 422
    assert invalid.json()["code"] == "AI_ASSISTANCE_REQUEST_INVALID"


def _client(monkeypatch: object) -> TestClient:
    monkeypatch.setenv("ALZS_AI_INTERNAL_TOKEN", TOKEN)  # type: ignore[attr-defined]
    monkeypatch.setenv("ALZS_EMBEDDING_BACKEND", "hash")  # type: ignore[attr-defined]
    _api_config.cache_clear()
    get_embedding_config.cache_clear()
    get_embedding_provider.cache_clear()
    return TestClient(create_app())


def _headers() -> dict[str, str]:
    return {"X-Internal-Service-Token": TOKEN}
