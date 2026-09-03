from __future__ import annotations

import re
import unicodedata


TOKEN_PATTERN = re.compile(r"[0-9A-Za-z가-힣]{2,}")
_PRESENTATION_PREFIX = re.compile(
    r"^(?:"
    r"고객(?:님)?에게\s+안내(?:하려고|하기\s+위해)[^.!?。]{0,20}|"
    r"행원\s+검토용으로[^.!?。]{0,40}|"
    r"현재\s+승인된\s+(?:규정|문서)\s+기준으로[^.!?。]{0,30}|"
    r"어려운\s+표현\s+없이\s+쉽게[^.!?。]{0,20}|"
    r"핵심\s+(?:조건|내용)만\s+정확히[^.!?。]{0,20}"
    r")[.!?。]\s*"
)
_DEFINITION_INTENT = re.compile(r"(?:뜻|의미|정의|무엇(?:인가요|인지|을\s+말하))")
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
_FUTURE_AS_CURRENT = re.compile(
    r"(?:내년|내일|향후|앞으로|시행\s*전|예정).{0,40}"
    r"(?:바뀔|발표될|개정|시행|적용).{0,40}"
    r"(?:현행|현재|오늘).{0,20}(?:기준|조항|규정|처럼|적용|인용|안내)"
)
_PROMPT_INJECTION = re.compile(
    r"(?:이전|앞선|위의|기존|시스템|개발자|관리자)\s*(?:지시|명령|규칙|정책|프롬프트)"
    r".{0,30}(?:무시|삭제|우회|덮어쓰|공개|출력)|"
    r"(?:ignore|bypass|override|reveal).{0,30}(?:instruction|prompt|policy|system)"
)
_PRIVILEGE_ESCALATION = re.compile(
    r"(?:권한|역할|role|permission).{0,30}(?:관리자|행원|준법|admin|staff).{0,20}"
    r"(?:변경|상승|추가|부여|가장|위장)|"
    r"(?:관리자|행원|준법|admin|staff).{0,30}(?:권한|역할).{0,20}(?:우회|획득|부여)"
)
_SECRET_EXTRACTION = re.compile(
    r"(?:비밀번호|암호|api\s*키|access\s*key|secret|토큰|인증키|환경변수|시스템\s*프롬프트)"
    r".{0,30}(?:알려|보여|출력|공개|노출|복호화|가져와)"
)
_PERSONAL_DATA_EXTRACTION = re.compile(
    r"(?:다른|전체|모든|타인|해당).{0,20}(?:고객|회원|직원).{0,30}"
    r"(?:주민등록번호|계좌번호|전화번호|주소|거래내역|개인정보).{0,20}"
    r"(?:알려|보여|출력|공개|내보내|조회)"
)
_CITATION_FABRICATION = re.compile(
    r"(?:근거|인용|출처|문서|조항).{0,20}(?:없|없어도|없이|찾지\s*말고).{0,30}"
    r"(?:만들|지어|생성|꾸며|답변|확정)|"
    r"(?:가짜|허위).{0,10}(?:근거|인용|출처|문서|조항)"
)
_UNGROUNDED_OPERATIONAL_CODE = re.compile(
    r"(?:승인\s*문서|근거|자료|문서).{0,15}(?:없|없는|없이).{0,35}"
    r"(?:자동|지급정지|계좌|송금|차단).{0,25}(?:실행\s*코드|코드|스크립트|명령)"
)


def normalize(value: str) -> str:
    return unicodedata.normalize("NFC", " ".join(value.lower().split()))


def retrieval_query(value: str) -> str:
    """Remove a leading presentation request that does not change retrieval intent."""
    normalized = normalize(value)
    stripped = _PRESENTATION_PREFIX.sub("", normalized, count=1).strip()
    return stripped or normalized


def has_definition_intent(value: str) -> bool:
    return bool(_DEFINITION_INTENT.search(retrieval_query(value)))


def keyword_terms(value: str) -> tuple[str, ...]:
    """Return deterministic Korean-friendly terms for lexical retrieval."""
    terms: list[str] = []
    for token in TOKEN_PATTERN.findall(retrieval_query(value)):
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
        or _FUTURE_AS_CURRENT.search(normalized)
        or _PROMPT_INJECTION.search(normalized)
        or _PRIVILEGE_ESCALATION.search(normalized)
        or _SECRET_EXTRACTION.search(normalized)
        or _PERSONAL_DATA_EXTRACTION.search(normalized)
        or _CITATION_FABRICATION.search(normalized)
        or _UNGROUNDED_OPERATIONAL_CODE.search(normalized)
    )


def _strip_korean_suffix(token: str) -> str:
    for suffix in _KOREAN_SUFFIXES:
        if token.endswith(suffix) and len(token) - len(suffix) >= 1:
            return token[: -len(suffix)]
    return token
