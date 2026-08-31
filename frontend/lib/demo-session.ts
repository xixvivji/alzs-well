import type { DemoRehearsalScenario } from "./demo-rehearsal";

export type DemoContext = {
  sessionId: string;
  capability: string;
  demoRunId: string;
  customerId: string;
  alertId: string;
  rehearsalScenario?: DemoRehearsalScenario;
};

type StoredDemoContext = Omit<DemoContext, "capability">;

const STORAGE_KEY = "alzs-well-demo-context";
let memoryCapability = "";

export function readDemoContext(): DemoContext | null {
  if (typeof window === "undefined") return null;
  try {
    const stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? "null") as StoredDemoContext | null;
    return stored ? { ...stored, capability: memoryCapability } : null;
  } catch { return null; }
}

export function saveDemoContext(context: DemoContext) {
  memoryCapability = context.capability;
  const stored: StoredDemoContext = {
    sessionId: context.sessionId,
    demoRunId: context.demoRunId,
    customerId: context.customerId,
    alertId: context.alertId,
    ...(context.rehearsalScenario ? { rehearsalScenario: context.rehearsalScenario } : {}),
  };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
}

export function clearDemoContext() {
  memoryCapability = "";
  sessionStorage.removeItem(STORAGE_KEY);
}
