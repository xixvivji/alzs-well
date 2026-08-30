import assert from "node:assert/strict";
import test from "node:test";
import { loadAlertAudit } from "../lib/alert-audit";
import {
  evaluateDisclosure, grantConsent, loadPrivateCustomerAssets, simulateDepositInterest, simulateFxExchange, withdrawConsent,
} from "../lib/private-customer-assets";
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

test("loads deposit, FX, pension, trust and consent contracts under one customer Bearer session", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); paths.push(path);
    assert.equal(new Headers(init?.headers).get("Authorization"), "Bearer access-secret");
    let data: unknown;
    if (path.endsWith("/customers/customer-1/deposit-holdings")) data = { items: [{ holdingId: "holding-1", currentBalance: 10000000, currency: "KRW" }] };
    else if (path.endsWith("/deposit-products")) data = { items: [{ productId: "deposit-product-1" }] };
    else if (path.endsWith("/fx/rates")) data = { items: [{ rateId: "rate-1", currency: "USD" }] };
    else if (path.endsWith("/customers/customer-1/foreign-currency-accounts")) data = { items: [{ accountId: "fx-account-1", currency: "USD" }] };
    else if (path.endsWith("/customers/customer-1/overseas-remittance-history")) data = { items: [] };
    else if (path.endsWith("/customers/customer-1/pension-holdings")) data = { items: [{ holdingId: "pension-1", currentValue: 20000000 }] };
    else if (path.endsWith("/customers/customer-1/trust-holdings")) data = { items: [{ trustId: "trust-1", currentValue: 30000000 }] };
    else if (path.endsWith("/customers/customer-1/consents")) data = { items: [{ consentId: "consent-1", customerId: "customer-1", purposeCode: "FINANCIAL_ANALYSIS", status: "ACTIVE", scopes: ["ACCOUNT_SUMMARY"], version: 1, revocable: true }] };
    else if (path.endsWith("/deposit-holdings/holding-1/maturity-options")) data = { items: [] };
    else if (path.endsWith("/deposit-holdings/holding-1")) data = { deposit: { holdingId: "holding-1" }, expectedMaturityAmount: 10100000, maturityActionAvailable: false, syntheticData: true, externalProviderCalled: false, externalActionExecuted: false };
    else if (path.endsWith("/deposit-products/deposit-product-1/rates")) data = { items: [] };
    else if (path.endsWith("/deposit-products/deposit-product-1")) data = { product: { productId: "deposit-product-1" }, cautionText: "합성", applicationAvailable: false, syntheticData: true, externalProviderCalled: false, externalActionExecuted: false };
    else if (path.endsWith("/fx/rates/USD")) data = { rateId: "rate-1", currency: "USD" };
    else if (path.endsWith("/pension-holdings/pension-1/projection")) data = { holdingId: "pension-1", scenarios: [], guaranteed: false, recommendationProvided: false, actionAvailable: false, syntheticData: true, externalActionExecuted: false };
    else if (path.endsWith("/trust-holdings/trust-1")) data = { trust: { trustId: "trust-1" }, beneficiaryIdentityProvided: false, contractActionAvailable: false, syntheticData: true, externalProviderCalled: false, externalActionExecuted: false };
    else if (path.endsWith("/customers/customer-1/consents/consent-1/history")) data = { items: [] };
    else if (path.endsWith("/customers/customer-1/consents/consent-1")) data = { consentId: "consent-1", customerId: "customer-1", purposeCode: "FINANCIAL_ANALYSIS", scopes: ["ACCOUNT_SUMMARY"], version: 1, revocable: true };
    else data = null;
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });
  const session = privateSession();
  const assets = await loadPrivateCustomerAssets(session);
  assert.equal(assets.deposits[0]?.holdingId, "holding-1");
  assert.equal(assets.selectedFxRate?.currency, "USD");
  assert.equal(assets.pensions[0]?.holdingId, "pension-1");
  assert.equal(assets.trusts[0]?.trustId, "trust-1");
  assert.equal(assets.consents[0]?.consentId, "consent-1");
  assert.equal(paths.length, 17);
});

test("connects safe simulations and consent lifecycle commands without external action fields", async (t) => {
  const calls: Array<{ path: string; init?: RequestInit; body: Record<string, unknown> }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
    calls.push({ path, init, body });
    const data = path.includes("interest-simulations")
      ? { productId: "deposit-product-1", grossInterest: 100000, estimatedTax: 15400, netInterest: 84600, estimatedMaturityAmount: 10084600, currency: "KRW", applicationAvailable: false, externalActionExecuted: false }
      : path.includes("exchange-simulations")
        ? { fromCurrency: "KRW", toCurrency: "USD", inputAmount: 1000000, convertedAmount: 700, exchangeCreated: false, externalActionExecuted: false }
        : path.includes("disclosure-evaluations")
          ? { evaluationId: "evaluation-1", consentId: "consent-1", customerId: "customer-1", purposeCode: "FINANCIAL_ANALYSIS", requestedScopes: ["ACCOUNT_SUMMARY"], missingScopes: [], consentStatus: "ACTIVE", decision: "ALLOW", policyVersion: "P1", disclosureAllowed: true, externalDisclosureRequested: false, externalDisclosureCreated: false }
          : { consentId: "consent-1", customerId: "customer-1", purposeCode: "FINANCIAL_ANALYSIS", status: "ACTIVE", scopes: ["ACCOUNT_SUMMARY"], version: 1, revocable: true };
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });
  const session = privateSession();
  const consent = { consentId: "consent-1", customerId: "customer-1", purposeCode: "FINANCIAL_ANALYSIS", status: "ACTIVE", scopes: ["ACCOUNT_SUMMARY"], grantedAt: "", expiresAt: "", withdrawnAt: null, withdrawalReason: null, version: 1, revocable: true };
  assert.equal((await simulateDepositInterest(session, "deposit-product-1", 10000000, 12)).externalActionExecuted, false);
  assert.equal((await simulateFxExchange(session, "KRW", "USD", 1000000)).exchangeCreated, false);
  await grantConsent(session, "FINANCIAL_ANALYSIS", ["ACCOUNT_SUMMARY"], "2027-08-30T00:00:00Z");
  await withdrawConsent(session, consent, "범위를 재검토합니다.");
  assert.equal((await evaluateDisclosure(session, consent)).externalDisclosureCreated, false);
  assert.deepEqual(calls[0]?.body, { principalAmount: 10000000, termMonths: 12 });
  assert.deepEqual(calls[1]?.body, { fromCurrency: "KRW", toCurrency: "USD", amount: 1000000 });
  assert.ok(new Headers(calls[2]?.init?.headers).get("Idempotency-Key"));
  assert.deepEqual(calls[3]?.body, { expectedVersion: 1, reason: "범위를 재검토합니다." });
  assert.ok(new Headers(calls[3]?.init?.headers).get("Idempotency-Key"));
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("Authorization") === "Bearer access-secret"));
});

function privateSession() {
  return { accessToken: "access-secret", accessExpiresAt: "", refreshToken: "refresh-secret", refreshExpiresAt: "", customerId: "customer-1", displayName: "합성고객", roles: [], permissions: [] };
}
