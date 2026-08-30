from __future__ import annotations

import unicodedata
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


def _camel_case(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class AssistanceModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_camel_case,
        extra="forbid",
        populate_by_name=True,
    )


PaymentContinuity = Literal["KEEP_ESSENTIAL_PAYMENTS", "REVIEW_BEFORE_CHANGE"]
ExplanationMode = Literal["SIMPLE_TEXT", "VOICE_AND_TEXT", "STAFF_EXPLANATION"]
HelpCondition = Literal["ON_REPEATED_CHANGE", "ON_CUSTOMER_REQUEST", "NEVER_AUTOMATIC"]
ShareScope = Literal[
    "PAYMENT_PREFERENCE",
    "EXPLANATION_PREFERENCE",
    "HELP_CONDITION",
    "ACCESSIBILITY",
]


class IntentStructureRequest(AssistanceModel):
    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    utterance: str = Field(min_length=4, max_length=500)

    @field_validator("utterance")
    @classmethod
    def normalize_utterance(cls, value: str) -> str:
        normalized = unicodedata.normalize("NFC", " ".join(value.split()))
        if not any(character.isalnum() for character in normalized):
            raise ValueError("utterance must contain meaningful characters")
        return normalized


class IntentSuggestion(AssistanceModel):
    payment_continuity: PaymentContinuity
    explanation_mode: ExplanationMode
    help_condition: HelpCondition
    share_scopes: tuple[ShareScope, ...] = Field(max_length=4)


class IntentFieldEvidence(AssistanceModel):
    field: Literal[
        "paymentContinuity",
        "explanationMode",
        "helpCondition",
        "shareScopes",
    ]
    excerpt: str = Field(min_length=1, max_length=120)
    confidence: float = Field(ge=0, le=1)


class IntentStructureResponse(AssistanceModel):
    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    suggestion: IntentSuggestion
    summary: str = Field(min_length=1, max_length=300)
    evidence: tuple[IntentFieldEvidence, ...]
    needs_clarification: bool
    clarifying_questions: tuple[str, ...] = Field(max_length=4)
    generated_by: str = Field(min_length=1, max_length=100)
    model_invoked: bool
    fallback_used: bool
    health_inference_used: Literal[False] = False
    financial_action_executed: Literal[False] = False


class FeatureSeries(AssistanceModel):
    feature_code: Literal[
        "MISSED_RECURRING_COUNT",
        "DUPLICATE_TRANSFER_COUNT",
        "REPEATED_CONFIRMATION_COUNT",
        "NEW_COUNTERPARTY_COUNT",
        "UNUSUAL_TIME_COUNT",
        "UNUSUAL_AMOUNT_COUNT",
    ]
    daily_values: tuple[float, ...] = Field(min_length=30, max_length=180)
    unit: Literal["COUNT"] = "COUNT"

    @field_validator("daily_values")
    @classmethod
    def bounded_values(cls, values: tuple[float, ...]) -> tuple[float, ...]:
        if any(value < 0 or value > 1_000_000 for value in values):
            raise ValueError("daily values are outside the safe range")
        return values


class ChangeAnalysisRequest(AssistanceModel):
    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    baseline_days: int = Field(default=60, ge=30, le=120)
    recent_days: int = Field(default=30, ge=7, le=60)
    features: tuple[FeatureSeries, ...] = Field(min_length=1, max_length=6)

    @model_validator(mode="after")
    def require_complete_windows(self) -> ChangeAnalysisRequest:
        required = self.baseline_days + self.recent_days
        if any(len(feature.daily_values) < required for feature in self.features):
            raise ValueError("feature series does not cover both analysis windows")
        codes = [feature.feature_code for feature in self.features]
        if len(codes) != len(set(codes)):
            raise ValueError("duplicate feature codes are not allowed")
        return self


class ChangeSignal(AssistanceModel):
    feature_code: str
    baseline_value: float = Field(ge=0)
    recent_value: float = Field(ge=0)
    delta: float
    direction: Literal["INCREASE", "DECREASE", "STABLE"]
    ewma_score: float
    cusum_score: float = Field(ge=0)
    change_detected: bool
    persistent: bool
    data_sufficient: bool
    method: Literal["EWMA_CUSUM_V1"] = "EWMA_CUSUM_V1"
    explanation: str = Field(min_length=1, max_length=300)


class ChangeAnalysisResponse(AssistanceModel):
    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    baseline_days: int
    recent_days: int
    changes: tuple[ChangeSignal, ...]
    diagnosis_inferred: Literal[False] = False
    financial_action_executed: Literal[False] = False


class PlainLanguageFact(AssistanceModel):
    feature_code: Literal[
        "MISSED_RECURRING_COUNT",
        "DUPLICATE_TRANSFER_COUNT",
        "REPEATED_CONFIRMATION_COUNT",
        "NEW_COUNTERPARTY_COUNT",
        "UNUSUAL_TIME_COUNT",
        "UNUSUAL_AMOUNT_COUNT",
    ]
    baseline_value: float = Field(ge=0, le=1_000_000)
    recent_value: float = Field(ge=0, le=1_000_000)
    recent_days: int = Field(default=30, ge=7, le=90)
    unit: Literal["COUNT"] = "COUNT"


class PlainLanguageRequest(AssistanceModel):
    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    explanation_mode: ExplanationMode
    fact: PlainLanguageFact


class PlainLanguageResponse(AssistanceModel):
    contract_version: Literal["1.0.0"] = "1.0.0"
    request_id: UUID
    title: str = Field(min_length=1, max_length=80)
    text: str = Field(min_length=1, max_length=300)
    speech_text: str = Field(min_length=1, max_length=300)
    generation_mode: Literal["CONSTRAINED_NLG_V1"] = "CONSTRAINED_NLG_V1"
    model_invoked: Literal[False] = False
    fallback_used: Literal[False] = False
    diagnosis_inferred: Literal[False] = False
    financial_action_executed: Literal[False] = False
