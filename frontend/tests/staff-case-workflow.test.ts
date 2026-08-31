import assert from "node:assert/strict";
import test from "node:test";
import {
  addStaffCaseNote,
  approveStaffGuidancePlan,
  closeStaffCaseAsFalsePositive,
  generateStaffCopilotDraft,
  issueStaffCapability,
  loadStaffCase,
  loadStaffCaseQueue,
  loadStaffCaseOperations,
  scheduleStaffFollowUp,
  startStaffCaseReview,
  updateStaffFollowUp,
  type StaffCaseQueueItem,
} from "../lib/staff-case-workflow";
import { selectStaffCaseQueueItems, staffCaseQueueMetrics } from "../lib/staff-case-queue-view";

const context = { sessionId: "98000000-0000-4000-8000-000000000001", demoRunId: "run-1" };

const envelope = <T>(data: T, message = "ok") => JSON.stringify({
  success: true, status: 200, code: "OK", message, data,
  errors: [], timestamp: "2026-08-29T00:00:00Z", traceId: "trace-staff",
});

test("loads the dedicated timeline, note and follow-up APIs", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); paths.push(path);
    assert.equal(new Headers(init?.headers).get("X-Demo-Capability"), "staff-secret");
    const data = path.endsWith("/timeline")
      ? { caseId: "CASE_001", currentState: "IN_BANK_REVIEW", caseVersion: 2, phases: [], auditTrail: [], hasMore: false, externalActionCreated: false }
      : path.endsWith("/notes")
        ? { items: [], count: 0, externalDeliveryCreated: false }
        : { items: [], count: 0, nextFollowUpAt: null };
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });

  const operations = await loadStaffCaseOperations(context, "CASE_001", "staff-secret");
  assert.equal(operations.timeline.currentState, "IN_BANK_REVIEW");
  assert.deepEqual(paths.sort(), [
    `/api/v1/demo/sessions/${context.sessionId}/cases/CASE_001/follow-ups`,
    `/api/v1/demo/sessions/${context.sessionId}/cases/CASE_001/notes`,
    `/api/v1/demo/sessions/${context.sessionId}/cases/CASE_001/timeline`,
  ]);
});

test("writes internal notes and follow-up changes with version and idempotency", async (t) => {
  const calls: Array<{ path: string; method?: string; body: Record<string, unknown>; headers: Headers }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calls.push({ path: String(input), method: init?.method, body: JSON.parse(String(init?.body)), headers: new Headers(init?.headers) });
    return new Response(envelope({ caseId: "CASE_001", currentState: "IN_BANK_REVIEW", caseVersion: 3, externalExecutionCreated: false }), { headers: { "content-type": "application/json" } });
  });

  await addStaffCaseNote(context, "CASE_001", "staff-secret", 2, "고객이 직접 확인한 사실만 기록");
  await scheduleStaffFollowUp(context, "CASE_001", "staff-secret", 2, "2026-09-01T01:00:00Z", "고객이 편한 시간에 다시 확인");
  await updateStaffFollowUp(context, "11111111-1111-4111-8111-111111111111", "staff-secret", 3, "COMPLETED", "사실관계 확인 완료");

  assert.equal(calls.length, 3);
  assert.deepEqual(calls[0]?.body, { caseVersion: 2, note: "고객이 직접 확인한 사실만 기록" });
  assert.deepEqual(calls[1]?.body, { caseVersion: 2, scheduledAt: "2026-09-01T01:00:00Z", reason: "고객이 편한 시간에 다시 확인" });
  assert.deepEqual(calls[2]?.body, { caseVersion: 3, status: "COMPLETED", resultNote: "사실관계 확인 완료", completedAt: null });
  assert.equal(calls[2]?.method, "PATCH");
  for (const call of calls) {
    assert.ok(call.headers.get("Idempotency-Key"));
    assert.equal(call.headers.get("X-Demo-Run-Id"), context.demoRunId);
  }
});

test("issues staff capability without exposing the bootstrap token to the browser", async (t) => {
  let calledUrl = "";
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calledUrl = String(input);
    assert.equal(init?.method, "POST");
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("Authorization"), null);
    assert.equal(headers.get("X-Demo-Capability"), null);
    return new Response(envelope(null), {
      headers: { "content-type": "application/json", "X-Demo-Staff-Capability": "staff-secret" },
    });
  });

  assert.equal(await issueStaffCapability(context.sessionId), "staff-secret");
  assert.equal(calledUrl, `/api/internal/staff-capability/${context.sessionId}`);
});

test("loads the staff queue with server-side state, priority and opaque cursor filters", async (t) => {
  let calledUrl = "";
  let requestSignal: AbortSignal | null | undefined;
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calledUrl = String(input);
    requestSignal = init?.signal;
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("X-Demo-Capability"), "staff-secret");
    assert.equal(headers.get("X-Demo-Run-Id"), context.demoRunId);
    return new Response(envelope({ items: [], nextCursor: "opaque-next", hasMore: true }), {
      headers: { "content-type": "application/json" },
    });
  });

  const controller = new AbortController();
  const queue = await loadStaffCaseQueue(context, "staff-secret", {
    state: "PENDING_BANK_REVIEW",
    reviewPriority: "HIGH",
    cursor: "opaque-current",
    limit: 20,
    signal: controller.signal,
  });

  assert.equal(calledUrl, `/api/v1/demo/sessions/${context.sessionId}/staff/cases?state=PENDING_BANK_REVIEW&reviewPriority=HIGH&cursor=opaque-current&limit=20`);
  assert.equal(queue.nextCursor, "opaque-next");
  assert.equal(queue.hasMore, true);
  assert.equal(requestSignal?.aborted, false);
  controller.abort();
  assert.equal(requestSignal?.aborted, true);
});

test("searches and orders only loaded queue items while reporting loaded metrics", () => {
  const items = [
    queueItem("CASE_LOW", "LOW", "2026-08-30T09:00:00Z", "정기납부 확인"),
    queueItem("CASE_HIGH_NEW", "HIGH", "2026-08-31T09:00:00Z", "반복확인 증가"),
    queueItem("CASE_HIGH_OLD", "HIGH", "2026-08-29T09:00:00Z", "중복송금 확인"),
  ];

  assert.deepEqual(selectStaffCaseQueueItems(items, "", "WORK_ORDER").map((item) => item.caseId), [
    "CASE_HIGH_OLD", "CASE_HIGH_NEW", "CASE_LOW",
  ]);
  assert.deepEqual(selectStaffCaseQueueItems(items, "중복송금", "NEWEST").map((item) => item.caseId), ["CASE_HIGH_OLD"]);
  assert.deepEqual(staffCaseQueueMetrics(items), { loaded: 3, highPriority: 2, waiting: 3, active: 0 });
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

test("connects copilot, review, guidance and false-positive commands without external action fields", async (t) => {
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
  await closeStaffCaseAsFalsePositive(context, "CASE_001", "staff-secret", 2, "정상 거래 근거 확인");

  assert.equal(calls.length, 4);
  assert.deepEqual(calls[0]?.body, { draftType: "CONSULTATION_NOTE" });
  assert.equal(calls[1]?.body.action, "START_REVIEW");
  assert.deepEqual(calls[2]?.body.selectedActionCodes, ["SAFE_BLOCK_INFO"]);
  assert.equal(calls[2]?.body.externalExecutionCreated, undefined);
  assert.deepEqual(calls[3]?.body, {
    action: "CLOSE_FALSE_POSITIVE",
    caseVersion: 2,
    note: "정상 거래 근거 확인",
    followUpAt: null,
  });
  for (const call of calls) {
    assert.equal(call.headers.get("X-Demo-Capability"), "staff-secret");
    assert.equal(call.headers.get("X-Demo-Run-Id"), context.demoRunId);
  }
  assert.equal(calls[0]?.headers.get("Idempotency-Key"), null);
  assert.ok(calls[1]?.headers.get("Idempotency-Key"));
  assert.ok(calls[2]?.headers.get("Idempotency-Key"));
  assert.ok(calls[3]?.headers.get("Idempotency-Key"));
});

function queueItem(caseId: string, reviewPriority: string, createdAt: string, summary: string): StaffCaseQueueItem {
  return {
    demoRunId: context.demoRunId,
    caseId,
    alertId: `ALERT_${caseId}`,
    customerId: `SYN_CUSTOMER_${caseId}`,
    state: "PENDING_BANK_REVIEW",
    reviewPriority,
    reasonCodes: ["REPEATED_CONFIRMATION"],
    customerResponseCode: "UNABLE_TO_CONFIRM",
    summary,
    trustedContactGate: {
      gateEvaluated: true,
      consentSnapshotId: null,
      consentStatus: "NOT_GRANTED",
      recipientAccepted: false,
      triggerMatched: true,
      fieldScopeMatched: false,
      validityMatched: false,
      deliveryEnabled: false,
      resultCode: "BLOCKED_BY_CONSENT",
      dispatchAttempted: false,
      externalDeliveryRequested: false,
      externalDeliveryCreated: false,
    },
    createdAt,
    caseVersion: 1,
    sessionResetVersion: 1,
  };
}
