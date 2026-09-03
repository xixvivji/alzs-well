from __future__ import annotations

import math
from statistics import fmean, pstdev

from app.domain.assistance import (
    ChangeAnalysisRequest,
    ChangeAnalysisResponse,
    ChangeSignal,
    FeatureSeries,
)


_FEATURE_LABELS = {
    "MISSED_RECURRING_COUNT": "정기납부 누락",
    "DUPLICATE_TRANSFER_COUNT": "중복송금",
    "REPEATED_CONFIRMATION_COUNT": "거래결과 재확인",
    "NEW_COUNTERPARTY_COUNT": "새 수취인 거래",
    "UNUSUAL_TIME_COUNT": "평소와 다른 시간대 거래",
    "UNUSUAL_AMOUNT_COUNT": "평소 범위를 벗어난 금액 거래",
}

_CONFIRMATION_QUESTIONS = {
    "MISSED_RECURRING_COUNT": "최근 납부일이나 납부 방법을 바꾸셨나요?",
    "DUPLICATE_TRANSFER_COUNT": "같은 곳에 두 번 보낸 것으로 알고 계신가요?",
    "REPEATED_CONFIRMATION_COUNT": "거래 결과가 잘 보이지 않아 여러 번 확인하셨나요?",
    "NEW_COUNTERPARTY_COUNT": "최근 새로 거래하기 시작한 분이나 업체가 있나요?",
    "UNUSUAL_TIME_COUNT": "평소와 다른 시간에 금융서비스를 이용한 이유가 있나요?",
    "UNUSUAL_AMOUNT_COUNT": "최근 평소보다 큰 금액을 사용하거나 옮길 일이 있었나요?",
}

_REVIEW_ITEMS = {
    "MISSED_RECURRING_COUNT": "납부일·납부 방법 변경 여부를 확인합니다.",
    "DUPLICATE_TRANSFER_COUNT": "같은 송금의 목적과 본인 인지 여부를 확인합니다.",
    "REPEATED_CONFIRMATION_COUNT": "화면 이해나 거래 결과 확인에 어려움이 있었는지 확인합니다.",
    "NEW_COUNTERPARTY_COUNT": "새 거래 상대와의 관계를 고객에게 직접 확인합니다.",
    "UNUSUAL_TIME_COUNT": "이용 시간 변화의 생활 맥락을 확인합니다.",
    "UNUSUAL_AMOUNT_COUNT": "금액 변화의 목적을 고객에게 직접 확인합니다.",
}


def analyze_changes(request: ChangeAnalysisRequest) -> ChangeAnalysisResponse:
    changes = tuple(_analyze(request, feature) for feature in request.features)
    summary, questions, checklist = _guidance(changes, request.recent_days)
    return ChangeAnalysisResponse(
        request_id=request.request_id,
        baseline_days=request.baseline_days,
        recent_days=request.recent_days,
        changes=changes,
        summary=summary,
        confirmation_questions=questions,
        review_checklist=checklist,
    )


def _guidance(
    changes: tuple[ChangeSignal, ...], recent_days: int
) -> tuple[str, tuple[str, ...], tuple[str, ...]]:
    detected = tuple(change for change in changes if change.change_detected)
    if detected:
        first_label = _FEATURE_LABELS[detected[0].feature_code]
        summary = (
            f"최근 {recent_days}일 동안 {first_label} 등 {len(detected)}개 항목에서 "
            "평소와 다른 장기 변화가 확인됐습니다. 이상이나 질환을 뜻하지 않으며, "
            "알고 있는 생활 변화인지 먼저 확인해 주세요."
        )
        questions = tuple(
            _CONFIRMATION_QUESTIONS[change.feature_code] for change in detected[:3]
        )
        feature_checks = tuple(
            _REVIEW_ITEMS[change.feature_code] for change in detected[:2]
        )
    else:
        summary = (
            f"최근 {recent_days}일은 과거 기준과 비교해 뚜렷한 장기 변화가 "
            "확인되지 않았습니다. 현재 결과만으로 이상이나 질환을 판단하지 않습니다."
        )
        questions = ("최근 납부 방법이나 금융 이용 습관을 바꾸셨나요?",)
        feature_checks = ()
    checklist = (
        "표시된 기간과 횟수가 내 금융생활과 맞는지 확인합니다.",
        "알고 있는 변화인지 또는 도움이 필요한지 직접 선택합니다.",
        *feature_checks,
    )
    return summary, questions, checklist


def _analyze(request: ChangeAnalysisRequest, feature: FeatureSeries) -> ChangeSignal:
    values = feature.daily_values[-(request.baseline_days + request.recent_days) :]
    baseline = values[: request.baseline_days]
    recent = values[request.baseline_days :]
    baseline_daily = fmean(baseline)
    baseline_value = baseline_daily * request.recent_days
    recent_value = sum(recent)
    delta = recent_value - baseline_value
    scale = max(pstdev(baseline), math.sqrt(max(baseline_daily, 0.0) + 0.25), 0.5)
    ewma = baseline_daily
    for value in recent:
        ewma = 0.3 * value + 0.7 * ewma
    ewma_score = (ewma - baseline_daily) / scale
    positive_allowance = 0.25 * scale
    # Count series are non-negative and often sparse. Reusing the positive
    # allowance for decreases can put the lower boundary below zero and make a
    # sustained drop to zero mathematically impossible to detect.
    negative_allowance = min(0.25 * scale, baseline_daily * 0.25)
    positive_cusum = 0.0
    negative_cusum = 0.0
    for value in recent:
        positive_cusum = max(
            0.0, positive_cusum + value - baseline_daily - positive_allowance
        )
        negative_cusum = max(
            0.0, negative_cusum + baseline_daily - value - negative_allowance
        )
    cusum_score = max(positive_cusum, negative_cusum) / scale
    persistence_days = max(3, math.ceil(request.recent_days * 0.1))
    increased_days = sum(value > baseline_daily + 0.25 * scale for value in recent)
    decreased_days = sum(value < baseline_daily - negative_allowance for value in recent)
    persistent = (
        increased_days >= persistence_days
        if delta > 0
        else decreased_days >= persistence_days if delta < 0 else False
    )
    meaningful_delta = abs(delta) >= max(1.0, baseline_value * 0.5)
    detected = meaningful_delta and (abs(ewma_score) >= 1.5 or cusum_score >= 3.0) and persistent
    if delta > 0.25:
        direction = "INCREASE"
    elif delta < -0.25:
        direction = "DECREASE"
    else:
        direction = "STABLE"
    explanation = _explanation(
        feature.feature_code,
        baseline_value,
        recent_value,
        request.recent_days,
        direction,
        detected,
    )
    return ChangeSignal(
        feature_code=feature.feature_code,
        baseline_value=round(baseline_value, 2),
        recent_value=round(recent_value, 2),
        delta=round(delta, 2),
        direction=direction,
        ewma_score=round(ewma_score, 4),
        cusum_score=round(cusum_score, 4),
        change_detected=detected,
        persistent=persistent,
        data_sufficient=len(values) >= request.baseline_days + request.recent_days,
        explanation=explanation,
    )


def _explanation(
    feature_code: str,
    baseline_value: float,
    recent_value: float,
    recent_days: int,
    direction: str,
    detected: bool,
) -> str:
    label = _FEATURE_LABELS[feature_code]
    baseline = _count_text(baseline_value)
    recent = _count_text(recent_value)
    if detected:
        direction_text = "증가했습니다." if direction == "INCREASE" else "감소했습니다."
        return (
            f"최근 {recent_days}일 동안 {label}이 평소 {baseline}에서 {recent}로 "
            f"지속적으로 {direction_text}"
        )
    return f"최근 {recent_days}일 동안 {label}은 평소 범위와 뚜렷하게 다른 장기 변화가 없습니다."


def _count_text(value: float) -> str:
    rounded = round(value)
    return f"{rounded}회" if abs(value - rounded) < 0.01 else f"{value:.1f}회"
