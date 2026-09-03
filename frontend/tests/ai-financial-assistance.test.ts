import assert from "node:assert/strict";
import test from "node:test";
import {
  approveFinancialIntent, generatePlainLanguage, loadChangeAnalysis, loadCurrentFinancialIntent,
  saveFinancialIntent, suggestFinancialIntent,
} from "../lib/ai-financial-assistance";

const context = { sessionId: "session-1", capability: "capability", demoRunId: "run-1", customerId: "customer-1", alertId: "alert-1" };

test("connects the three AI assistance flows through the scoped demo API", async (t) => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const url = String(input); calls.push({ url, init });
    const data = url.endsWith("/intent-suggestions") ? { suggestion: { paymentContinuity: "KEEP_ESSENTIAL_PAYMENTS", explanationMode: "SIMPLE_TEXT", helpCondition: "ON_CUSTOMER_REQUEST", shareScopes: [] } }
      : url.endsWith("/intent/approve") ? { intentId: "intent-1", status: "APPROVED", version: 2 }
      : url.endsWith("/intent") ? { intentId: "intent-1", status: "DRAFT", version: 1 }
      : url.endsWith("/change-analysis") ? { analysisWindowDays: 90, changes: [], windowComparisons: [], summary: "최근 변화가 없습니다.", confirmationQuestions: ["최근 이용 방식을 바꾸셨나요?"], reviewChecklist: ["표시된 값을 확인합니다."], guidanceMode: "EXPLAINABLE_CHANGE_GUIDANCE_V1" }
      : { featureCode: "REPEATED_CONFIRMATION_COUNT", text: "쉬운 설명", speechText: "쉬운 설명" };
    return new Response(JSON.stringify({ success: true, status: 200, code: "OK", message: "ok", data, errors: [], timestamp: "2026-08-30T00:00:00Z", traceId: "trace" }), { status: 200, headers: { "content-type": "application/json" } });
  });

  await suggestFinancialIntent(context, "공과금을 유지해 주세요");
  await saveFinancialIntent(context, { paymentContinuity: "KEEP_ESSENTIAL_PAYMENTS", explanationMode: "SIMPLE_TEXT", helpCondition: "ON_CUSTOMER_REQUEST", shareScopes: [] }, 0);
  await approveFinancialIntent(context, 1);
  assert.equal((await loadCurrentFinancialIntent(context))?.intentId, "intent-1");
  await loadChangeAnalysis(context);
  await generatePlainLanguage(context, "REPEATED_CONFIRMATION_COUNT");

  assert.equal(calls.length, 6);
  assert.ok(calls.every((call) => call.url.includes("/ai-financial-assistance")));
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("X-Demo-Capability") === "capability"));
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("X-Demo-Run-Id") === "run-1"));
});

test("treats only the documented missing-intent response as an empty current state", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response(JSON.stringify({
    success: false, status: 404, code: "DEMO_AI_INTENT_NOT_FOUND", message: "의향서가 없습니다.",
    data: null, errors: [], timestamp: "2026-08-30T00:00:00Z", traceId: "trace",
  }), { status: 404, headers: { "content-type": "application/json" } }));
  assert.equal(await loadCurrentFinancialIntent(context), null);
});
