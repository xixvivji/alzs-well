import type { DemoRehearsalScenario } from "./demo-rehearsal";

export type DemoContext = {
  sessionId: string;
  capability: string;
  demoRunId: string;
  customerId: string;
  alertId: string;
  rehearsalScenario?: DemoRehearsalScenario;
};
const STORAGE_KEY = "alzs-well-demo-context";
export function readDemoContext(): DemoContext | null { if (typeof window === "undefined") return null; try { return JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? "null") as DemoContext | null; } catch { return null; } }
export function saveDemoContext(context: DemoContext) { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(context)); }
export function clearDemoContext() { sessionStorage.removeItem(STORAGE_KEY); }
