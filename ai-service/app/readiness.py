from __future__ import annotations

from uuid import UUID

from app.assistance.change import analyze_changes
from app.assistance.intent import structure_intent
from app.assistance.plain_language import plain_language
from app.domain.assistance import (
    ChangeAnalysisRequest,
    FeatureSeries,
    IntentStructureRequest,
    PlainLanguageFact,
    PlainLanguageRequest,
)
from app.embedding.local_hash import LocalHashEmbeddingProvider


_INTENT_REQUEST_ID = UUID("97000000-0000-4000-8000-000000000001")
_CHANGE_REQUEST_ID = UUID("97000000-0000-4000-8000-000000000002")
_LANGUAGE_REQUEST_ID = UUID("97000000-0000-4000-8000-000000000003")


def assistance_contracts_ready() -> bool:
    """Exercise every deterministic assistance contract without customer data or actions."""
    try:
        intent = structure_intent(
            IntentStructureRequest(
                request_id=_INTENT_REQUEST_ID,
                utterance="필수 납부는 유지하고 쉬운 글로 설명해 주세요.",
            ),
            LocalHashEmbeddingProvider(),
        )
        change = analyze_changes(
            ChangeAnalysisRequest(
                request_id=_CHANGE_REQUEST_ID,
                features=(
                    FeatureSeries(
                        feature_code="REPEATED_CONFIRMATION_COUNT",
                        daily_values=(0.0,) * 90,
                    ),
                ),
            )
        )
        language = plain_language(
            PlainLanguageRequest(
                request_id=_LANGUAGE_REQUEST_ID,
                explanation_mode="SIMPLE_TEXT",
                fact=PlainLanguageFact(
                    feature_code="REPEATED_CONFIRMATION_COUNT",
                    baseline_value=1,
                    recent_value=2,
                ),
            )
        )
    except (TypeError, ValueError):
        return False
    return bool(
        intent.request_id == _INTENT_REQUEST_ID
        and not intent.health_inference_used
        and not intent.financial_action_executed
        and change.request_id == _CHANGE_REQUEST_ID
        and len(change.changes) == 1
        and not change.diagnosis_inferred
        and not change.financial_action_executed
        and language.request_id == _LANGUAGE_REQUEST_ID
        and not language.diagnosis_inferred
        and not language.financial_action_executed
    )
