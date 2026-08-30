from __future__ import annotations

from app.domain.assistance import PlainLanguageRequest, PlainLanguageResponse


_LABELS = {
    "MISSED_RECURRING_COUNT": "정기납부",
    "DUPLICATE_TRANSFER_COUNT": "같은 송금",
    "REPEATED_CONFIRMATION_COUNT": "거래결과 확인",
    "NEW_COUNTERPARTY_COUNT": "새로운 분과의 거래",
    "UNUSUAL_TIME_COUNT": "평소와 다른 시간대의 거래",
    "UNUSUAL_AMOUNT_COUNT": "평소와 다른 금액의 거래",
}


def plain_language(request: PlainLanguageRequest) -> PlainLanguageResponse:
    fact = request.fact
    label = _LABELS[fact.feature_code]
    baseline = _count(fact.baseline_value)
    recent = _count(fact.recent_value)
    title = f"{label} 변화를 확인해 주세요"
    change = "늘었습니다" if fact.recent_value > fact.baseline_value else "줄었습니다"
    if abs(fact.recent_value - fact.baseline_value) < 0.01:
        first = f"최근 {fact.recent_days}일 동안 {label}은 평소와 비슷했습니다."
    else:
        first = f"최근 {fact.recent_days}일 동안 {label}이 평소 {baseline}에서 {recent}로 {change}."
    ending = {
        "SIMPLE_TEXT": "알고 있는 변화인지 천천히 확인해 주세요.",
        "VOICE_AND_TEXT": "지금 들은 내용이 알고 있는 변화인지 확인해 주세요.",
        "STAFF_EXPLANATION": "잘 모르겠다면 행원과 함께 확인할 수 있습니다.",
    }[request.explanation_mode]
    text = f"{first} {ending}"
    return PlainLanguageResponse(
        request_id=request.request_id,
        title=title,
        text=text,
        speech_text=text,
    )


def _count(value: float) -> str:
    rounded = round(value)
    return f"{rounded}회" if abs(value - rounded) < 0.01 else f"{value:.1f}회"
