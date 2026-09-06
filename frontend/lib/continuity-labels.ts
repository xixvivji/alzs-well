export function changeLabel(value: string) {
  return ({ MISSED_PAYMENT: "정기납부 누락", MISSED_RECURRING_PAYMENT: "정기납부 누락", DUPLICATE_TRANSFER: "반복 송금", REPEATED_CONFIRMATION: "거래결과 반복 확인", NEW_COUNTERPARTY: "새 수취인" } as Record<string, string>)[value] ?? "금융생활 변화";
}

export function responseLabel(value?: string | null) {
  return ({ EXPECTED_CHANGE: "제가 알고 있는 변화예요", UNRECOGNIZED: "제가 모르는 변화예요", NOT_SURE: "잘 모르겠어요" } as Record<string, string>)[value ?? ""] ?? "등록된 응답 없음";
}

export function alertStateLabel(value: string) {
  return ({ AWAITING_CONTEXT: "내 확인 필요", DEFERRED: "나중에 확인", BANK_REVIEW: "은행 검토 연결됨", CLOSED_NORMAL: "알고 있는 변화로 확인 완료" } as Record<string, string>)[value] ?? value;
}

export function caseStateLabel(value: string) {
  return ({ PENDING: "검토 대기", IN_REVIEW: "행원 검토 중", GUIDANCE_APPROVED: "안내계획 승인", COMPLETED: "검토 종결" } as Record<string, string>)[value] ?? value;
}

export function metric(value: string | number, unit: string) {
  const number = Number(value);
  const label = ({ COUNT: "회", KRW: "원", RATIO: "%" } as Record<string, string>)[unit] ?? unit;
  return `${Number.isFinite(number) ? new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 }).format(number) : value}${label}`;
}

export function dateTime(value?: string | null) {
  if (!value) return "기록 없음";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function intentValueLabel(value?: string) {
  return ({ KEEP_ESSENTIAL_PAYMENTS: "필수 납부 유지", REVIEW_BEFORE_CHANGE: "변경 전에 다시 확인", SIMPLE_TEXT: "짧고 쉬운 글", VOICE_AND_TEXT: "음성과 글 함께", STAFF_EXPLANATION: "행원이 천천히 설명", ON_REPEATED_CHANGE: "반복 변화가 있을 때", ON_CUSTOMER_REQUEST: "내가 요청했을 때", NEVER_AUTOMATIC: "자동 요청하지 않기" } as Record<string, string>)[value ?? ""] ?? "설정 없음";
}
