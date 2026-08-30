import rehearsalData from "../data/rehearsal-scenarios-v1.json";

export type DemoRehearsalScenario = "normal" | "caution" | "false-positive";

export type DemoRehearsalDefinition = {
  id: DemoRehearsalScenario;
  label: string;
  title: string;
  summary: string;
  customerAction: string;
  expectedState: string;
  checkpoints: string[];
  responseCode: "KNOWN_AND_INTENTIONAL" | "UNABLE_TO_CONFIRM";
  demoBranchCode: "FIN_MGMT_A_NORMAL_CONTEXT" | "FIN_MGMT_B_NO_CONTEXT";
  staffDecision: string;
  requiresCitation: boolean;
};

export const REHEARSAL_FIXTURE = rehearsalData.fixture;
export const DEMO_REHEARSAL_SCENARIOS = rehearsalData.scenarios as DemoRehearsalDefinition[];

export function findRehearsalScenario(
  scenario: DemoRehearsalScenario | undefined,
): DemoRehearsalDefinition | null {
  return DEMO_REHEARSAL_SCENARIOS.find((item) => item.id === scenario) ?? null;
}

export function contextPayloadForScenario(scenario: DemoRehearsalScenario | undefined) {
  const selected = findRehearsalScenario(scenario) ?? findRehearsalScenario("caution");
  return { responseCode: selected!.responseCode, demoBranchCode: selected!.demoBranchCode };
}
