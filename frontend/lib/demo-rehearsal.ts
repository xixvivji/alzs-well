export type DemoRehearsalScenario = "normal" | "caution" | "false-positive";

export type DemoRehearsalDefinition = {
  id: DemoRehearsalScenario;
  label: string;
  title: string;
  summary: string;
  customerAction: string;
  expectedState: string;
  checkpoints: string[];
};

export const DEMO_REHEARSAL_SCENARIOS: DemoRehearsalDefinition[] = [
  {
    id: "normal",
    label: "정상",
    title: "정상 생활맥락 확인",
    summary: "고객 응답을 서버의 검증된 구조적 근거와 대조해 사건 없이 종결합니다.",
    customerAction: "제가 알고 있는 금융활동입니다",
    expectedState: "CLOSED_NORMAL",
    checkpoints: ["고객이 알고 있는 활동으로 응답", "구조적 근거 4종 일치", "행원 사건 미생성"],
  },
  {
    id: "caution",
    label: "주의",
    title: "행원 보호업무 연결",
    summary: "고객이 확인하기 어려운 변화를 행원에게 연결하고 승인된 근거로 안내계획을 만듭니다.",
    customerAction: "잘 모르겠습니다. 도움받겠습니다",
    expectedState: "GUIDANCE_PLAN_APPROVED",
    checkpoints: ["고객 도움 요청", "AI 초안의 승인 근거 인용", "행원의 안내계획 승인"],
  },
  {
    id: "false-positive",
    label: "오탐",
    title: "사람 검토 후 오탐 종결",
    summary: "주의 사건을 자동 해제하지 않고 행원이 사실을 확인한 뒤 근거 메모와 함께 종결합니다.",
    customerAction: "잘 모르겠습니다. 도움받겠습니다",
    expectedState: "CLOSED_FALSE_POSITIVE",
    checkpoints: ["고객 도움 요청", "행원 사실확인 시작", "오탐 근거 기록 후 종결"],
  },
];

export function findRehearsalScenario(
  scenario: DemoRehearsalScenario | undefined,
): DemoRehearsalDefinition | null {
  return DEMO_REHEARSAL_SCENARIOS.find((item) => item.id === scenario) ?? null;
}

export function contextPayloadForScenario(scenario: DemoRehearsalScenario | undefined) {
  return scenario === "normal"
    ? { responseCode: "KNOWN_AND_INTENTIONAL", demoBranchCode: "FIN_MGMT_A_NORMAL_CONTEXT" }
    : { responseCode: "UNABLE_TO_CONFIRM", demoBranchCode: "FIN_MGMT_B_NO_CONTEXT" };
}
