import assert from "node:assert/strict";
import test from "node:test";
import {
  approveStaffGuidancePlan,
  generateStaffCopilotDraft,
  issueStaffCapability,
  loadStaffCase,
  startStaffCaseReview,
} from "../lib/staff-case-workflow";

const context = { sessionId: "98000000-0000-4000-8000-000000000001", demoRunId: "run-1" };

const envelope = <T>(data: T, message = "ok") => JSON.stringify({
  success: true, status: 200, code: "OK", message, data,
  errors: [], timestamp: "2026-08-29T00:00:00Z", traceId: "trace-staff",
});

test("issues staff capability without exposing the bootstrap token to the browser", async (t) => {
  let calledUrl = "";
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calledUrl = String(input);
    assert.equal(init?.method, "POST");
    assert.equal(new Headers(init?.headers).get("Authorization"), null);
    return new Response(envelope(null), {
      headers: { "content-type": "application/json", "X-Demo-Staff-Capability": "staff-secret" },
    });
  });

  assert.equal(await issueStaffCapability(context.sessionId), "staff-secret");
  assert.equal(calledUrl, `/api/internal/staff-capability/${context.sessionId}`);
});

test("loads case detail and immutable evidence with the staff capability", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input);
    paths.push(path);
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("X-Demo-Capability"), "staff-secret");
    assert.equal(headers.get("X-Demo-Run-Id"), context.demoRunId);
    const data = path.endsWith("/evidence")
      ? { immutableT0: true, signals: [], provenance: { syntheticData: true, sourceProvider: "SYNTHETIC_PROVIDER", externalFetchPerformed: false } }
      : { caseId: "CASE_001", caseVersion: 1, state: "PENDING_BANK_REVIEW" };
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });

  const bundle = await loadStaffCase(context, "CASE_001", "staff-secret");
  assert.equal(bundle.detail.caseId, "CASE_001");
  assert.equal(bundle.evidence.immutableT0, true);
  assert.deepEqual(paths.sort(), [
    `/api/v1/demo/sessions/${context.sessionId}/cases/CASE_001`,
    `/api/v1/demo/sessions/${context.sessionId}/cases/CASE_001/evidence`,
  ]);
});

test("connects copilot, review and guidance commands without external action fields", async (t) => {
  const calls: Array<{ path: string; body: Record<string, unknown>; headers: Headers }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input);
    calls.push({ path, body: JSON.parse(String(init?.body)) as Record<string, unknown>, headers: new Headers(init?.headers) });
    const data = path.endsWith("/copilot-drafts")
      ? { caseId: "CASE_001", draftType: "CONSULTATION_NOTE", draft: { summary: "초안" }, safety: { humanReviewRequired: true } }
      : { caseId: "CASE_001", previousState: "PENDING_BANK_REVIEW", currentState: "IN_BANK_REVIEW", caseVersion: 2, externalExecutionCreated: false };
    return new Response(envelope(data, "처리했습니다."), { headers: { "content-type": "application/json" } });
  });

  const draft = await generateStaffCopilotDraft(context, "CASE_001", "staff-secret");
  assert.equal(draft.safety.humanReviewRequired, true);
  await startStaffCaseReview(context, "CASE_001", "staff-secret", 1);
  await approveStaffGuidancePlan(context, "CASE_001", "staff-secret", 2, ["SAFE_BLOCK_INFO"], "공식 조건 확인");

  assert.equal(calls.length, 3);
  assert.deepEqual(calls[0]?.body, { draftType: "CONSULTATION_NOTE" });
  assert.equal(calls[1]?.body.action, "START_REVIEW");
  assert.deepEqual(calls[2]?.body.selectedActionCodes, ["SAFE_BLOCK_INFO"]);
  assert.equal(calls[2]?.body.externalExecutionCreated, undefined);
  for (const call of calls) {
    assert.equal(call.headers.get("X-Demo-Capability"), "staff-secret");
    assert.equal(call.headers.get("X-Demo-Run-Id"), context.demoRunId);
  }
  assert.equal(calls[0]?.headers.get("Idempotency-Key"), null);
  assert.ok(calls[1]?.headers.get("Idempotency-Key"));
  assert.ok(calls[2]?.headers.get("Idempotency-Key"));
});
