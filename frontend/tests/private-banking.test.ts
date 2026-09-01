import assert from "node:assert/strict";
import test from "node:test";
import { evaluateTransfer, loadBankingOverview, loadTransferWorkspace } from "../lib/private-banking";
import { loadLifeServices, searchKnowledge } from "../lib/private-life-services";
import { loadSafetyCenter, respondToSafetyAlert } from "../lib/private-safety-center";
import { loadAdminOperations, loadStaffOperations } from "../lib/operational-portal";
import type { PrivateCustomerSession } from "../lib/private-financial-products";

const session: PrivateCustomerSession = { customerId: "customer-1", displayName: "합성고객", roles: ["CUSTOMER"], permissions: [] };
const envelope = (data: unknown) => JSON.stringify({ success: true, status: 200, code: "OK", message: "ok", data, errors: [], timestamp: "2026-09-01T00:00:00Z", traceId: "trace" });
const response = (data: unknown) => new Response(envelope(data), { headers: { "content-type": "application/json" } });

test("회원 통합금융 화면은 8개 운영 조회 API를 HttpOnly BFF로 연결한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); paths.push(path);
    assert.equal(new Headers(init?.headers).get("Authorization"), null);
    if (path.endsWith("/financial-summary")) return response({ totalAssets: 100, totalLiabilities: 20, netAssets: 80, periodInflow: 10, periodOutflow: 4, netCashflow: 6, accountCount: 2, liabilityCount: 1, currency: "KRW", dataAsOf: "2026-08-31", syntheticData: true });
    if (path.endsWith("/cashflow-summary")) return response({ totalInflow: 10, totalOutflow: 4, netCashflow: 6, categories: [] });
    if (path.endsWith("/expense-summary")) return response({ totalExpense: 4, items: [] });
    return response({ items: [] });
  });
  const overview = await loadBankingOverview(session);
  assert.equal(overview.summary.netAssets, 80);
  assert.equal(paths.length, 8);
  assert.ok(paths.every((path) => path.startsWith("/api/v1/")));
});

test("이체 화면은 회원별 계좌·수취인·한도·양식과 실행 없는 사전검증만 사용한다", async (t) => {
  const calls: Array<{ path: string; body?: string }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, body: init?.body?.toString() });
    if (path.endsWith("/accounts")) return response({ items: [{ accountId: "account-1" }] });
    if (path.endsWith("/beneficiaries")) return response({ items: [{ beneficiaryId: "beneficiary-1" }] });
    if (path.endsWith("/transfer-limits")) return response({ perTransferLimit: 5000000, dailyLimit: 10000000, dailyUsedAmount: 0, dailyRemainingAmount: 10000000, currency: "KRW", dataAsOf: "2026-08-31" });
    if (path.endsWith("/transfer-templates")) return response({ items: [] });
    return response({ outcomeCode: "ALLOW", allowed: true, decisionCode: "ALLOW", checks: [], transferCreated: false, authorizationCreated: false });
  });
  const workspace = await loadTransferWorkspace(session);
  const result = await evaluateTransfer(session, "account-1", "beneficiary-1", 100000, "LIVING_EXPENSE");
  assert.equal(workspace.limit.dailyRemainingAmount, 10000000);
  assert.equal(result.simulation.transferCreated, false);
  assert.equal(result.validation.authorizationCreated, false);
  assert.ok(calls.some((call) => call.path === "/api/v1/transfer-simulations"));
  assert.ok(calls.some((call) => call.path === "/api/v1/transfer-validations"));
});

test("생활금융 화면은 의향·알림·연결·보호·근거·세션 API를 한 회원 범위로 조회한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input); paths.push(path);
    if (path.endsWith("/continuity-preparation")) return response({ readiness: "READY", latestApproved: null, legalDisclaimerRequired: true });
    if (path.endsWith("/notification-preferences")) return response({ changeAlertEnabled: true, followUpEnabled: true, serviceNoticeEnabled: true, version: 1, externalDeliveryEnabled: false });
    if (path.endsWith("/knowledge/search")) return response({ items: [{ passage: { passageId: "passage-1", heading: "확인", content: "공식 근거", citationLabel: "근거 1", sourceUrl: "https://example.invalid" }, matchedKeywordCount: 1, retrievalMode: "DETERMINISTIC" }] });
    return response({ items: [] });
  });
  const bundle = await loadLifeServices(session);
  const hits = await searchKnowledge(session, "정기납부 확인");
  assert.equal(bundle.preparation.readiness, "READY");
  assert.equal(hits[0]?.passage.heading, "확인");
  assert.equal(paths.length, 13);
  assert.ok(paths.filter((path) => path.includes("customer-1")).length >= 6);
});

test("안심관리 화면은 기준선·변화신호·고객 확인·감사이력을 회원 범위로 연결한다", async (t) => {
  const calls: Array<{ path: string; method: string; body?: string }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, method: init?.method ?? "GET", body: init?.body?.toString() });
    if (path.endsWith("/baselines")) return response({ items: [{ baselineId: "baseline-1", featureCode: "REPEATED_CONFIRMATION", baselineValue: "2", currentValue: "8", unit: "COUNT", readiness: "READY", comparisonText: "월 2회에서 8회", algorithmVersion: "v1", calculatedAt: "2026-08-31T00:00:00Z", version: 1 }] });
    if (path.endsWith("/signals")) return response({ items: [{ signalId: "signal-1", baselineId: "baseline-1", signalType: "REPEATED_CONFIRMATION", severity: "MEDIUM", baselineValue: "2", currentValue: "8", unit: "COUNT", reasonCode: "REPEATED_CONFIRMATION", status: "OPEN", algorithmVersion: "v1", detectedAt: "2026-08-31T00:00:00Z" }] });
    if (path.endsWith("/alerts")) return response({ items: [{ alertId: "alert-1", signalId: "signal-1", state: "AWAITING_CONTEXT", severity: "MEDIUM", reasonCode: "REPEATED_CONFIRMATION", version: 1, deferredUntil: null, createdAt: "2026-08-31T00:00:00Z", updatedAt: "2026-08-31T00:00:00Z" }] });
    if (path.endsWith("/features")) return response({ items: [] });
    if (path.endsWith("/evidence")) return response({ items: [] });
    if (path.endsWith("/context-options")) return response({ question: "이 활동을 알고 계신가요?", options: [{ responseCode: "NOT_SURE", label: "잘 모르겠어요", description: "사람의 확인을 요청합니다." }] });
    if (path.endsWith("/audit")) return response({ items: [] });
    if (path.endsWith("/context-responses")) return response({ alertId: "alert-1", currentState: "BANK_REVIEW", version: 2 });
    return response({ alert: { alertId: "alert-1", signalId: "signal-1", state: "AWAITING_CONTEXT", severity: "MEDIUM", reasonCode: "REPEATED_CONFIRMATION", version: 1 } });
  });
  const bundle = await loadSafetyCenter(session);
  assert.equal(bundle.contextOptions[0]?.responseCode, "NOT_SURE");
  assert.equal(calls.length, 8);
  await respondToSafetyAlert(session, bundle.selectedAlert!, "NOT_SURE");
  const mutation = calls.find((call) => call.path.endsWith("/context-responses"));
  assert.equal(mutation?.method, "POST");
  assert.match(mutation?.body ?? "", /"expectedVersion":1/);
  assert.ok(calls.every((call) => call.path.startsWith("/api/v1/")));
});

test("보호업무와 관리자는 서로 다른 Bearer 역할의 운영 조회 API만 사용한다", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => { paths.push(String(input)); return response({ items: [] }); });
  const staff = { ...session, roles: ["PROTECTION_STAFF"] };
  const admin = { ...session, roles: ["DETECTION_ADMIN"] };
  await loadStaffOperations(staff);
  await loadAdminOperations(admin);
  assert.ok(paths.includes("/api/v1/staff/cases?limit=50"));
  assert.ok(paths.includes("/api/v1/admin/rules"));
  assert.ok(paths.includes("/api/v1/audit/events?limit=25"));
  await assert.rejects(() => loadAdminOperations(staff), /역할/);
  await assert.rejects(() => loadStaffOperations(admin), /역할/);
});
