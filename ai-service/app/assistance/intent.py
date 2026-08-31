from __future__ import annotations

import re
from dataclasses import dataclass

from app.domain.assistance import (
    IntentFieldEvidence,
    IntentStructureRequest,
    IntentStructureResponse,
    IntentSuggestion,
)
from app.embedding.base import EmbeddingProvider, EmbeddingVector
from app.retrieval.query import normalize


@dataclass(frozen=True, slots=True)
class _Candidate:
    value: str
    phrases: tuple[str, ...]
    keywords: tuple[str, ...]


_PAYMENT = (
    _Candidate("KEEP_ESSENTIAL_PAYMENTS", ("필수 납부를 계속 유지하고 싶어요",), ("계속", "유지", "공과금", "보험료", "납부")),
    _Candidate("REVIEW_BEFORE_CHANGE", ("납부 방식을 바꾸기 전에 먼저 확인하고 싶어요",), ("바꾸기 전", "변경 전", "먼저 확인")),
)
_EXPLANATION = (
    _Candidate("SIMPLE_TEXT", ("쉽고 짧은 문장으로 천천히 설명해 주세요",), ("쉽게", "쉬운", "짧게", "천천히", "간단")),
    _Candidate("VOICE_AND_TEXT", ("글과 음성으로 함께 설명해 주세요",), ("음성", "읽어", "소리", "말로")),
    _Candidate("STAFF_EXPLANATION", ("행원이 직접 설명해 주세요",), ("행원", "직원", "상담")),
)
_HELP = (
    _Candidate("ON_REPEATED_CHANGE", ("평소와 다른 변화가 반복되면 도움을 요청해 주세요",), ("반복", "여러 번", "계속 달라")),
    _Candidate("ON_CUSTOMER_REQUEST", ("제가 요청할 때 도움을 받고 싶어요",), ("요청할 때", "원할 때", "도움받고")),
    _Candidate("NEVER_AUTOMATIC", ("자동으로 도움을 요청하거나 연락하지 마세요",), ("자동으로 하지", "자동 연락", "원하지 않")),
)
_PAYMENT_STOP_PATTERNS = (
    re.compile(r"(?:납부|공과금|보험료).{0,12}(?:중단|멈추|그만)"),
    re.compile(r"(?:납부하|납부를\s*하|돈을\s*내|요금을\s*내)지\s*(?:않|말)"),
    re.compile(r"(?:납부|공과금|보험료).{0,12}유지하지\s*(?:않|말)"),
    re.compile(
        r"(?:공과금|보험료|요금)(?:은|는|을|를)?\s*(?:계속\s*)?"
        r"(?:내|납부하)지\s*(?:않|말)"
    ),
)
_PAYMENT_KEEP_NEGATION_PATTERNS = (
    re.compile(
        r"(?:납부|공과금|보험료).{0,12}(?:중단|멈추|그만두|끊)하지\s*(?:않|말)"
    ),
    re.compile(r"(?:납부|결제).{0,12}(?:바꾸지|변경하지)\s*(?:않|말)"),
    re.compile(r"(?:납부|결제)\s*방식은?\s*(?:그대로|유지)"),
)


def structure_intent(
    request: IntentStructureRequest,
    provider: EmbeddingProvider,
) -> IntentStructureResponse:
    text = normalize(request.utterance)
    query_vector = provider.embed_query(text)
    payment_override = _payment_negation_override(text)
    if payment_override is None:
        payment, payment_confidence, payment_excerpt = _select(
            text, query_vector, _PAYMENT, provider
        )
    else:
        payment, payment_confidence, payment_excerpt, _ = payment_override
    explanation, explanation_confidence, explanation_excerpt = _select(
        text, query_vector, _EXPLANATION, provider
    )
    help_condition, help_confidence, help_excerpt = _select(text, query_vector, _HELP, provider)
    scopes, scope_confidence, scope_excerpt, scope_question = _share_scopes(text)

    questions: list[str] = []
    if payment_override is not None and payment_override[3]:
        questions.append(payment_override[3])
    elif payment_confidence < 0.55:
        questions.append("필수 납부를 계속 유지할지, 변경 전에 확인할지 선택해 주세요.")
    if explanation_confidence < 0.55:
        questions.append("쉬운 글, 음성 안내, 행원 설명 중 원하는 방식을 선택해 주세요.")
    if help_confidence < 0.55:
        questions.append("어떤 상황에서 도움을 요청할지 선택해 주세요.")
    if scope_question:
        questions.append(scope_question)

    suggestion = IntentSuggestion(
        payment_continuity=payment,  # type: ignore[arg-type]
        explanation_mode=explanation,  # type: ignore[arg-type]
        help_condition=help_condition,  # type: ignore[arg-type]
        share_scopes=scopes,  # type: ignore[arg-type]
    )
    summary = _summary(payment, explanation, help_condition, scopes)
    descriptor = provider.descriptor
    return IntentStructureResponse(
        request_id=request.request_id,
        suggestion=suggestion,
        summary=summary,
        evidence=(
            IntentFieldEvidence(field="paymentContinuity", excerpt=payment_excerpt, confidence=payment_confidence),
            IntentFieldEvidence(field="explanationMode", excerpt=explanation_excerpt, confidence=explanation_confidence),
            IntentFieldEvidence(field="helpCondition", excerpt=help_excerpt, confidence=help_confidence),
            IntentFieldEvidence(field="shareScopes", excerpt=scope_excerpt, confidence=scope_confidence),
        ),
        needs_clarification=bool(questions),
        clarifying_questions=tuple(questions),
        generated_by=f"{descriptor.model_id}:{descriptor.model_version}",
        model_invoked=descriptor.backend != "hash",
        fallback_used=descriptor.backend == "hash",
    )


def _payment_negation_override(text: str) -> tuple[str, float, str, str | None] | None:
    # "중단하지 말아 주세요"처럼 중단 동사 자체가 부정된 문장은
    # 단순히 "중단"만 찾으면 정반대로 분류된다. 이중 부정을 먼저 판별한다.
    for pattern in _PAYMENT_KEEP_NEGATION_PATTERNS:
        matched = pattern.search(text)
        if matched:
            return "KEEP_ESSENTIAL_PAYMENTS", 0.99, matched.group(0)[:120], None
    for pattern in _PAYMENT_STOP_PATTERNS:
        matched = pattern.search(text)
        if matched:
            return (
                "REVIEW_BEFORE_CHANGE",
                0.99,
                matched.group(0)[:120],
                "필수 납부를 중단하려는 뜻인지 직접 확인해 주세요.",
            )
    return None


def _select(
    text: str,
    query_vector: EmbeddingVector,
    candidates: tuple[_Candidate, ...],
    provider: EmbeddingProvider,
) -> tuple[str, float, str]:
    ranked: list[tuple[float, _Candidate, str | None]] = []
    for candidate in candidates:
        matched = next((keyword for keyword in candidate.keywords if keyword in text), None)
        semantic = max(
            _cosine(query_vector, provider.embed_passage(phrase))
            for phrase in candidate.phrases
        )
        score = min(1.0, max(0.0, semantic) + (0.65 if matched else 0.0))
        ranked.append((score, candidate, matched))
    score, selected, matched = max(ranked, key=lambda item: item[0])
    excerpt = matched or text[:120]
    return selected.value, round(score, 4), excerpt


def _share_scopes(text: str) -> tuple[tuple[str, ...], float, str, str | None]:
    if any(phrase in text for phrase in ("공유하지", "알리지 마", "보여주지 마")):
        return (), 0.99, "공유하지", None
    if not any(phrase in text for phrase in ("공유", "행원에게", "직원에게", "상담할 때")):
        return (), 0.0, text[:120], "행원과 공유할 항목을 직접 선택해 주세요."
    scopes: list[str] = []
    if any(word in text for word in ("납부", "공과금", "보험료")):
        scopes.append("PAYMENT_PREFERENCE")
    if any(word in text for word in ("설명", "쉬운", "천천히")):
        scopes.append("EXPLANATION_PREFERENCE")
    if any(word in text for word in ("도움", "요청", "반복")):
        scopes.append("HELP_CONDITION")
    if any(word in text for word in ("음성", "큰 글씨", "접근성")):
        scopes.append("ACCESSIBILITY")
    return tuple(scopes), 0.9 if scopes else 0.4, "공유", None if scopes else "공유할 항목을 선택해 주세요."


def _summary(payment: str, explanation: str, help_condition: str, scopes: tuple[str, ...]) -> str:
    payment_text = "필수 납부를 유지" if payment == "KEEP_ESSENTIAL_PAYMENTS" else "납부 변경 전 확인"
    explanation_text = {
        "SIMPLE_TEXT": "쉬운 글",
        "VOICE_AND_TEXT": "글과 음성",
        "STAFF_EXPLANATION": "행원 설명",
    }[explanation]
    help_text = {
        "ON_REPEATED_CHANGE": "반복된 변화가 있을 때 도움 요청",
        "ON_CUSTOMER_REQUEST": "고객이 요청할 때 도움 제공",
        "NEVER_AUTOMATIC": "자동 도움 요청 안 함",
    }[help_condition]
    share_text = f"{len(scopes)}개 항목 공유" if scopes else "행원 공유 없음"
    return f"{payment_text}, {explanation_text} 방식, {help_text}, {share_text}으로 정리했습니다."


def _cosine(left: EmbeddingVector, right: EmbeddingVector) -> float:
    return sum(a * b for a, b in zip(left, right, strict=True))
