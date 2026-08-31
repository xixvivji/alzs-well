import assert from "node:assert/strict";
import test from "node:test";
import { clearDemoContext, readDemoContext, saveDemoContext } from "../lib/demo-session";

test("keeps the customer capability out of sessionStorage", () => {
  const values = new Map<string, string>();
  Object.defineProperty(globalThis, "window", { configurable: true, value: {} });
  Object.defineProperty(globalThis, "sessionStorage", { configurable: true, value: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  }});
  saveDemoContext({
    sessionId: "session-1", capability: "customer-capability-secret", demoRunId: "run-1",
    customerId: "customer-1", alertId: "alert-1", rehearsalScenario: "caution",
  });
  const serialized = values.get("alzs-well-demo-context") ?? "";
  assert.doesNotMatch(serialized, /customer-capability-secret|capability/);
  assert.equal(readDemoContext()?.capability, "customer-capability-secret");
  clearDemoContext();
  assert.equal(readDemoContext(), null);
});
