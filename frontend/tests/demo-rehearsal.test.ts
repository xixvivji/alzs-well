import assert from "node:assert/strict";
import test from "node:test";
import {
  contextPayloadForScenario,
  DEMO_REHEARSAL_SCENARIOS,
  findRehearsalScenario,
} from "../lib/demo-rehearsal";

test("defines normal, caution and false-positive rehearsal outcomes", () => {
  assert.deepEqual(DEMO_REHEARSAL_SCENARIOS.map((scenario) => scenario.id), [
    "normal", "caution", "false-positive",
  ]);
  assert.equal(findRehearsalScenario("normal")?.expectedState, "CLOSED_NORMAL");
  assert.equal(findRehearsalScenario("caution")?.expectedState, "GUIDANCE_PLAN_APPROVED");
  assert.equal(findRehearsalScenario("false-positive")?.expectedState, "CLOSED_FALSE_POSITIVE");
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
