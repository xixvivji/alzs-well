import { customerLoginFromId } from "./prototype-navigation";

// Exact seed phrases only: preserve unknown evidence and all amounts, dates and source records.
const evidenceCopy: Record<string, string> = {
  "예정된 정기납부가 확인되지 않은 합성 근거입니다.": "예정된 정기납부 내역이 확인되지 않았습니다.",
  "같은 수취인과 금액이 반복된 합성 거래 근거입니다.": "같은 받는 분에게 같은 금액을 반복해서 보낸 내역이 있습니다.",
  "같은 거래 결과를 반복 확인한 합성 상호작용 근거입니다.": "같은 거래 결과를 여러 번 확인한 기록이 있습니다.",
  "결정론적 합성 기준선과 현재 관측값 비교": "평소 기록과 최근 활동을 비교했습니다.",
};
export function evidenceDescription(value: string) { return evidenceCopy[value] ?? value; }
export function customerLabel(customerId: string) { return customerLoginFromId(customerId) ?? customerId; }
export function accountDisplayName(value: string) {
  return value.replace(/^합성 보호업무 행원 (\d{2})$/, "행원 $1").replace(/^합성 탐지관리자 (\d{2})$/, "관리자 $1").replace(/^합성 이용자 (\d{6})$/, "고객 $1");
}
