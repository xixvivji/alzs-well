import assert from "node:assert/strict";
import test from "node:test";
import {
  contextPayloadForScenario,
  DEMO_REHEARSAL_SCENARIOS,
  findRehearsalScenario,
  REHEARSAL_FIXTURE,
} from "../lib/demo-rehearsal";

test("defines normal, caution and false-positive rehearsal outcomes", () => {
  assert.deepEqual(DEMO_REHEARSAL_SCENARIOS.map((scenario) => scenario.id), [
    "normal", "caution", "false-positive",
  ]);
  assert.equal(findRehearsalScenario("normal")?.expectedState, "CLOSED_NORMAL");
  assert.equal(findRehearsalScenario("caution")?.expectedState, "GUIDANCE_PLAN_APPROVED");
  assert.equal(findRehearsalScenario("false-positive")?.expectedState, "CLOSED_FALSE_POSITIVE");
});

test("pins one immutable 3-2-7 fixture across all three contextual outcomes", () => {
  assert.equal(REHEARSAL_FIXTURE.backendScenarioId, "FIN_MGMT_AB_001");
  assert.deepEqual(REHEARSAL_FIXTURE.signals.map((signal) => signal.observedCount), [3, 2, 7]);
  assert.equal(REHEARSAL_FIXTURE.externalActionsCreated, false);
  assert.equal(DEMO_REHEARSAL_SCENARIOS.find((item) => item.id === "caution")?.requiresCitation, true);
  assert.ok(DEMO_REHEARSAL_SCENARIOS.every((item) => item.staffDecision.length > 0));
});

test("maps only the normal rehearsal to the verified-context branch", () => {
  assert.deepEqual(contextPayloadForScenario("normal"), {
    responseCode: "KNOWN_AND_INTENTIONAL",
    demoBranchCode: "FIN_MGMT_A_NORMAL_CONTEXT",
  });
  for (const scenario of ["caution", "false-positive"] as const) {
    assert.deepEqual(contextPayloadForScenario(scenario), {
      responseCode: "UNABLE_TO_CONFIRM",
      demoBranchCode: "FIN_MGMT_B_NO_CONTEXT",
    });
  }
});
