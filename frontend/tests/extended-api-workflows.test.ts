import assert from "node:assert/strict";
import test from "node:test";
import { loadAlertAudit } from "../lib/alert-audit";
import {
  loadPrivateProductOverview, loginPrivateCustomer, logoutPrivateCustomer, simulateLoanRepayment,
} from "../lib/private-financial-products";
import { loadSystemStatus } from "../lib/system-status";

const envelope = <T>(data: T, status = 200) => JSON.stringify({
  success: status < 400, status, code: status === 503 ? "SYSTEM_NOT_READY" : "OK", message: "ok", data,
  errors: [], timestamp: "2026-08-30T00:00:00Z", traceId: "trace",
});
const demoContext = { sessionId: "session-1", capability: "customer-cap", demoRunId: "run-1", customerId: "customer-1", alertId: "alert-1" };

test("loads alert audit history with the scoped capability", async (t) => {
  t.mock.method(globalThis, "fetch", async (input, init) => {
    assert.equal(String(input), "/api/v1/demo/sessions/session-1/alerts/alert-1/audit?limit=20&cursor=next-token");
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("X-Demo-Capability"), "customer-cap");
    assert.equal(headers.get("X-Demo-Run-Id"), "run-1");
    return new Response(envelope({ items: [{ auditId: "audit-1" }], nextCursor: null, hasMore: false }), { headers: { "content-type": "application/json" } });
  });
  assert.equal((await loadAlertAudit(demoContext, "alert-1", "next-token")).items.length, 1);
});

test("keeps readiness details visible when the readiness API correctly returns 503", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input); paths.push(path);
    const data = path.endsWith("/health")
      ? { status: "UP", service: "alzs-well", syntheticDataOnly: true, externalActionsEnabled: false }
      : path.endsWith("/readiness")
        ? { ready: false, status: "NOT_READY", checks: { database: "UP", aiRetrieval: "DOWN" } }
        : path.endsWith("/public-config")
          ? { apiVersion: "v1", dataMode: "SYNTHETIC", syntheticDataOnly: true, externalActionsEnabled: false, networkMode: "LOCAL_ONLY", externalEgressEnabled: false, remoteModelEnabled: false, syntheticProviderOnly: true, supportedScenarioIds: ["normal"], defaultLocale: "ko-KR", demoSessionTtlSeconds: 1800, featureFlags: { optionalLlmEnabled: false, templateFallbackEnabled: true, trustedContactDeliveryEnabled: false } }
          : { applicationVersion: "1", apiVersion: "v1", schemaVersion: "74", fixtureVersion: "1", algorithmVersion: "1", policyVersion: "1", sourceCatalogCheckedAt: "2026-08-30" };
    const status = path.endsWith("/readiness") ? 503 : 200;
    return new Response(envelope(data, status), { status, headers: { "content-type": "application/json" } });
  });
  const snapshot = await loadSystemStatus();
  assert.equal(snapshot.readiness.ready, false);
  assert.equal(snapshot.readiness.checks.aiRetrieval, "DOWN");
  assert.deepEqual(paths.sort(), ["/api/v1/system/health", "/api/v1/system/public-config", "/api/v1/system/readiness", "/api/v1/system/versions"]);
});

test("uses Bearer authentication for the private card, loan and investment dashboard", async (t) => {
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, init });
    let data: unknown;
    if (path.endsWith("/auth/login")) data = { accessToken: "access-secret", accessExpiresAt: "2026-08-30T01:00:00Z", refreshToken: "refresh-secret", refreshExpiresAt: "2026-08-31T00:00:00Z" };
    else if (path.endsWith("/auth/me/permissions")) data = { permissions: ["CARD_READ", "FINANCIAL_OVERVIEW_READ"] };
    else if (path.endsWith("/auth/me")) data = { customerId: "customer-1", displayName: "합성고객", roles: ["CUSTOMER"] };
    else if (path.endsWith("/customers/customer-1/cards")) data = { items: [{ cardId: "card-1", currency: "KRW" }] };
    else if (path.endsWith("/customers/customer-1/loan-holdings")) data = { items: [{ loanId: "loan-1", currency: "KRW" }] };
    else if (path.endsWith("/loan-products")) data = { items: [{ productId: "product-1" }] };
    else if (path.endsWith("/customers/customer-1/investment-accounts")) data = { items: [{ accountId: "account-1", currency: "KRW" }] };
    else if (path.endsWith("/cards/card-1")) data = { card: { cardId: "card-1" }, syntheticData: true, externalActionExecuted: false };
    else if (path.includes("/cards/card-1/transactions")) data = { items: [] };
    else if (path.endsWith("/cards/card-1/statements")) data = { items: [] };
    else if (path.endsWith("/cards/card-1/payment-due")) data = { amount: 0, currency: "KRW", paymentAvailable: false };
    else if (path.endsWith("/cards/card-1/limits")) data = { totalLimitAmount: 0, usedAmount: 0, availableLimitAmount: 0, currency: "KRW", limitChangeAvailable: false };
    else if (path.endsWith("/loan-holdings/loan-1/repayment-schedule")) data = { items: [] };
    else if (path.endsWith("/investment-accounts/account-1/portfolio")) data = { allocations: [] };
    else if (path.endsWith("/investment-accounts/account-1/positions")) data = { items: [] };
    else if (path.endsWith("/investment-accounts/account-1/orders")) data = { items: [] };
    else data = null;
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });

  const session = await loginPrivateCustomer("synthetic-customer", "a-secure-demo-password");
  const overview = await loadPrivateProductOverview(session);
  assert.equal(overview.cards[0]?.cardId, "card-1");
  const loginCall = calls.find((call) => call.path.endsWith("/auth/login"));
  assert.equal(new Headers(loginCall?.init?.headers).get("Authorization"), null);
  const protectedCalls = calls.filter((call) => !call.path.endsWith("/auth/login"));
  assert.ok(protectedCalls.length >= 10);
  assert.ok(protectedCalls.every((call) => new Headers(call.init?.headers).get("Authorization") === "Bearer access-secret"));
});

test("loan simulation and logout remain explicit non-persistent Bearer calls", async (t) => {
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calls.push({ path: String(input), init });
    const data = String(input).includes("repayment-simulations")
      ? { productId: "product-1", principalAmount: 30000000, termMonths: 60, annualInterestRate: 4.5, monthlyPrincipal: 500000, firstPaymentAmount: 612500, finalPaymentAmount: 501875, totalInterest: 3431250, totalRepaymentAmount: 33431250, currency: "KRW", calculationRule: "EQUAL_PRINCIPAL_ESTIMATE_V1", personalized: false, creditAssessmentPerformed: false, applicationAvailable: false, externalActionExecuted: false }
      : null;
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });
  const session = { accessToken: "access-secret", accessExpiresAt: "", refreshToken: "refresh-secret", refreshExpiresAt: "", customerId: "customer-1", displayName: "합성고객", roles: [], permissions: [] };
  const result = await simulateLoanRepayment(session, "product-1", 30000000, 60, 4.5);
  await logoutPrivateCustomer(session);
  assert.equal(result.applicationAvailable, false);
  assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), { principalAmount: 30000000, termMonths: 60, annualInterestRate: 4.5 });
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("Authorization") === "Bearer access-secret"));
});
