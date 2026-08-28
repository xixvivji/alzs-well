from __future__ import annotations

import re
import unicodedata


TOKEN_PATTERN = re.compile(r"[0-9A-Za-z가-힣]{2,}")
_KOREAN_SUFFIXES = (
    "으로부터",
    "에게서",
    "에서는",
    "으로는",
    "이라면",
    "인가요",
    "하나요",
    "해야",
    "에서",
    "에게",
    "으로",
    "라고",
    "이며",
    "에는",
    "부터",
    "까지",
    "처럼",
    "보다",
    "은",
    "는",
    "이",
    "가",
    "을",
    "를",
    "의",
    "에",
    "로",
    "와",
    "과",
    "도",
    "만",
)
_CASE_CONTEXT = re.compile(r"(?:이|해당)\s*(?:사건|고객|신청|거래)|고객(?:님)?")
_FINAL_DECISION = re.compile(
    r"(?:최종\s*)?(?:승인|결정|판정|판단|확정)(?:해|하여|하라|해주세요|해 주세요|해줘|해 줘|해주시)|"
    r"(?:대상|적격)(?:인지|여부).{0,12}(?:판단|결정|승인|확정)"
)
_PROHIBITED_EXECUTION = re.compile(
    r"(?:동의\s*없이|동의하지\s*않).{0,30}(?:연락|계좌.{0,8}(?:정지|차단)|지급정지)"
)
_UNSUPPORTED_PERSONAL_DATA = re.compile(
    r"(?:자료|문서|근거).{0,8}(?:없|없는).{0,30}(?:개인\s*)?(?:휴대전화|전화)\s*번호"
)
_MEDICAL_DIAGNOSIS = re.compile(
    r"(?:(?:이|해당)\s*(?:고객|사람|환자)|고객(?:님)?|나(?:를|의|에게)?)"
    r".{0,30}(?:치매|질환|질병|병명).{0,20}(?:진단|확정|판정)"
)
_PERSONALIZED_INVESTMENT = re.compile(
    r"(?:(?:이|해당)\s*고객|고객(?:님)?|나(?:를|의|에게)?)"
    r".{0,40}(?:금융상품|투자상품|주식|펀드|채권).{0,30}"
    r"(?:골라|추천|매수|매도|선택)"
)
_FUTURE_LAW_CERTAINTY = re.compile(
    r"(?:내일|향후|앞으로|시행\s*전|예정).{0,40}(?:법령|법률|법규|시행령|개정)"
    r".{0,30}(?:확정|단정|보장)"
)


def normalize(value: str) -> str:
    return unicodedata.normalize("NFC", " ".join(value.lower().split()))


def keyword_terms(value: str) -> tuple[str, ...]:
    """Return deterministic Korean-friendly terms for lexical retrieval."""
    terms: list[str] = []
    for token in TOKEN_PATTERN.findall(normalize(value)):
        stemmed = _strip_korean_suffix(token)
        if stemmed not in terms:
            terms.append(stemmed)
    return tuple(terms)


def keyword_query(value: str) -> str:
    """Build a safe whitespace-separated query for PostgreSQL websearch parsing."""
    return " ".join(keyword_terms(value))


def requires_abstention(value: str) -> bool:
    """Block requests that retrieval must never treat as grounded knowledge answers."""
    normalized = normalize(value)
    final_decision = _CASE_CONTEXT.search(normalized) and _FINAL_DECISION.search(normalized)
    return bool(
        final_decision
        or _PROHIBITED_EXECUTION.search(normalized)
        or _UNSUPPORTED_PERSONAL_DATA.search(normalized)
        or _MEDICAL_DIAGNOSIS.search(normalized)
        or _PERSONALIZED_INVESTMENT.search(normalized)
        or _FUTURE_LAW_CERTAINTY.search(normalized)
    )


def _strip_korean_suffix(token: str) -> str:
    for suffix in _KOREAN_SUFFIXES:
        if token.endswith(suffix) and len(token) - len(suffix) >= 1:
            return token[: -len(suffix)]
    return token
