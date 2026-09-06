// Display-only mappings. Questions/checks come from DeterministicCopilotAdapter.
const reviewPrompts: Record<string, { question: string; check: string }> = {
  MISSED_RECURRING_PAYMENT: { question: "최근 정기납부가 처리되지 않은 이유를 함께 확인해도 될까요?", check: "정기납부 처리상태 확인" },
  DUPLICATE_TRANSFER: { question: "짧은 시간 안에 같은 금액을 두 번 송금한 사유가 있었을까요?", check: "중복송금 취소·환불 여부 확인" },
  REPEATED_CONFIRMATION: { question: "거래 완료 후 결과를 여러 번 확인하게 된 불편이 있었을까요?", check: "거래 결과화면 지연 여부 확인" },
};

export function reviewPrompt(reasonCode: string) { return reviewPrompts[reasonCode] ?? null; }

export function reviewNextTask(status: string) {
  switch (status) {
    case "PENDING": return "고객 응답과 변화 근거를 확인한 뒤 검토를 시작하세요.";
    case "IN_REVIEW": return "고객 의향과 근거를 확인하고, 안내할 내용 또는 종결 사유를 정리하세요.";
    case "GUIDANCE_APPROVED": return "안내계획이 승인되었습니다. 검토를 마치면 종결 사유를 남겨 주세요.";
    case "COMPLETED": return "검토가 종결되었습니다. 남은 후속 일정과 처리 이력을 확인하세요.";
    default: return "현재 처리 상태를 확인해 주세요.";
  }
}

export function reviewEventLabel(type: string, fallback: string) {
  const labels: Record<string, string> = {
    CASE_CREATED: "은행 검토 접수", CONTEXT_RESPONDED: "고객 응답 접수",
    START_REVIEW: "행원 검토 시작", COMPLETE_REVIEW: "검토 종결", REOPEN_REVIEW: "검토 재개",
    GUIDANCE_APPROVED: "안내계획 승인", INTERNAL_NOTE_ADDED: "내부 메모 등록",
    FOLLOW_UP_CREATED: "후속 일정 등록", FOLLOW_UP_COMPLETED: "후속 확인 완료",
    FOLLOW_UP_CANCELLED: "후속 일정 취소", FOLLOW_UP_RESCHEDULED: "후속 일정 변경",
  };
  return labels[type] ?? fallback;
}
