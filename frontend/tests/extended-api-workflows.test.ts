import assert from "node:assert/strict";
import "./session-recovery.test";
import test from "node:test";
import { ApiClientError } from "../lib/api";
import { loadAlertAudit } from "../lib/alert-audit";
import { loadCustomerProtectionSnapshot } from "../lib/customer-protection-center";
import {
  evaluateDisclosure, grantConsent, loadPrivateCustomerAssets, simulateDepositInterest, simulateFxExchange, withdrawConsent,
} from "../lib/private-customer-assets";
import {
  createTrustedContact, ensureTrustedContactConsent, loadPrivateCustomerCare, revokeTrustedContact,
  submitAlertAppeal, updateAccessibilitySettings, updateCustomerDisplayName, updateCustomerPreferences,
} from "../lib/private-customer-care";
import {
  loadPrivateProductOverview, loginPrivateCustomer, logoutPrivateCustomer, simulateLoanRepayment,
} from "../lib/private-financial-products";
import { PrivateSessionExpiredError, withPrivateCustomerSession } from "../lib/private-auth-session";
import { loadSystemStatus } from "../lib/system-status";
import { suggestPrivateFinancialIntent } from "../lib/private-ai-intent";

const envelope = <T>(data: T, status = 200) => JSON.stringify({
  success: status < 400, status, code: status === 503 ? "SYSTEM_NOT_READY" : "OK", message: "ok", data,
  errors: [], timestamp: "2026-08-30T00:00:00Z", traceId: "trace",
});
const demoContext = { sessionId: "session-1", capability: "customer-cap", demoRunId: "run-1", customerId: "customer-1", alertId: "alert-1" };

test("로그인 회원 AI 의향 구조화는 문장만 격리 세션에 전달하고 세션을 회수한다", async (t) => {
  const calls: Array<{ path: string; method: string; body?: string }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, method: init?.method ?? "GET", body: init?.body?.toString() });
    if (path === "/api/v1/demo/sessions") return new Response(envelope({ sessionId: "session-member-ai" }), { headers: { "content-type": "application/json", "X-Demo-Customer-Capability": "temporary-cap" } });
    if (path.endsWith("/ingest")) return new Response(envelope({ demoRunId: "run-member-ai", customerId: "isolated-ai-customer", alertId: "alert-member-ai" }), { headers: { "content-type": "application/json" } });
    if (path.endsWith("/intent-suggestions")) return new Response(envelope({ suggestion: { paymentContinuity: "KEEP_ESSENTIAL_PAYMENTS", explanationMode: "STAFF_EXPLANATION", helpCondition: "ON_REPEATED_CHANGE", shareScopes: [] }, summary: "필수 납부를 유지하고 반복 변화 때 도움을 요청합니다.", evidence: [], needsClarification: false, clarifyingQuestions: [], generatedBy: "FASTAPI", modelInvoked: true, fallbackUsed: false, healthInferenceUsed: false, financialActionExecuted: false }), { headers: { "content-type": "application/json" } });
    return new Response(envelope(null), { headers: { "content-type": "application/json" } });
  });
  const result = await suggestPrivateFinancialIntent("공과금은 유지하고 반복 변화 때 알려주세요.");
  assert.equal(result.suggestion.helpCondition, "ON_REPEATED_CHANGE");
  assert.equal(calls.filter((call) => call.method === "DELETE").length, 1);
  assert.match(calls.find((call) => call.path.endsWith("/intent-suggestions"))?.body ?? "", /공과금은 유지/);
  assert.ok(calls.every((call) => !String(call.body).includes("customer-1")));
});

test("loads the customer protection center from five scoped read APIs", async (t) => {
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, init });
    const data = path.endsWith("/financial-summary")
      ? { assets: { total: { amount: "120000000" } }, cashFlow: { monthlyIncome: { amount: "3000000" }, monthlyExpense: { amount: "2100000" } }, changeSummary: { openAlertCount: 1, summary: "확인이 필요한 변화가 있습니다." } }
      : path.endsWith("/alerts")
        ? { items: [{ alertId: "alert-1", state: "PENDING_CUSTOMER_CONFIRMATION", severity: "MEDIUM" }] }
        : path.endsWith("/intent")
          ? { intentId: "intent-1", customerId: "customer-1", status: "APPROVED", version: 2, paymentContinuity: "KEEP_ESSENTIAL_PAYMENTS", explanationMode: "SIMPLE_TEXT", helpCondition: "ON_CUSTOMER_REQUEST", shareScopes: [], disclaimerAccepted: true, legallyBinding: false, healthInferenceUsed: false }
          : path.endsWith("/change-analysis")
            ? { baselineDays: 60, recentDays: 30, analysisWindowDays: 90, changes: [{ featureCode: "REPEATED_CONFIRMATION_COUNT", baselineValue: 2, recentValue: 7, delta: 5, direction: "INCREASE", ewmaScore: 1, cusumScore: 2, changeDetected: true, persistent: true, dataSufficient: true, method: "EWMA_CUSUM", explanation: "재확인이 증가했습니다." }], summary: "거래결과 확인 변화가 있습니다.", confirmationQuestions: ["여러 번 확인하셨나요?"], reviewChecklist: ["표시된 값을 확인합니다."], guidanceMode: "EXPLAINABLE_CHANGE_GUIDANCE_V1", analysisMode: "FASTAPI", fallbackUsed: false, syntheticData: true, diagnosisInferred: false, financialActionExecuted: false }
            : { items: [{ auditId: "audit-1", eventType: "ALERT_CREATED", actorType: "SYSTEM", fromState: null, toState: "PENDING_CUSTOMER_CONFIRMATION", resultCode: null, evidenceIds: [], algorithmVersion: "v1", policyVersion: "v1", traceId: "trace", occurredAt: "2026-08-30T00:00:00Z" }], nextCursor: null, hasMore: false };
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });

  const snapshot = await loadCustomerProtectionSnapshot(demoContext);
  assert.equal(snapshot.financialSummary.assets.total.amount, "120000000");
  assert.equal(snapshot.alerts[0]?.alertId, "alert-1");
  assert.equal(snapshot.intent?.status, "APPROVED");
  assert.equal(snapshot.analysis?.changes[0]?.recentValue, 7);
  assert.equal(snapshot.audit.length, 1);
  assert.deepEqual(snapshot.unavailable, []);
  assert.equal(calls.length, 5);
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("X-Demo-Capability") === "customer-cap"));
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("X-Demo-Run-Id") === "run-1"));
});

test("keeps core protection information visible when AI assistance is unavailable", async (t) => {
  t.mock.method(globalThis, "fetch", async (input) => {
    const path = String(input);
    if (path.endsWith("/financial-summary")) return new Response(envelope({ assets: { total: { amount: "0" } }, cashFlow: { monthlyIncome: { amount: "0" }, monthlyExpense: { amount: "0" } }, changeSummary: { openAlertCount: 0, summary: "정상" } }), { headers: { "content-type": "application/json" } });
    if (path.endsWith("/alerts")) return new Response(envelope({ items: [] }), { headers: { "content-type": "application/json" } });
    if (path.endsWith("/intent")) return new Response(envelope(null, 404).replace("OK", "DEMO_AI_INTENT_NOT_FOUND"), { status: 404, headers: { "content-type": "application/json" } });
    if (path.endsWith("/change-analysis")) throw new Error("AI offline");
    return new Response(envelope({ items: [], nextCursor: null, hasMore: false }), { headers: { "content-type": "application/json" } });
  });
  const snapshot = await loadCustomerProtectionSnapshot(demoContext);
  assert.equal(snapshot.intent, null);
  assert.equal(snapshot.analysis, null);
  assert.deepEqual(snapshot.unavailable, ["analysis"]);
  assert.equal(snapshot.financialSummary.changeSummary.summary, "정상");
});

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
  assert.deepEqual(paths.sort(), ["/api/v1/system/ai-readiness", "/api/v1/system/core-readiness", "/api/v1/system/health", "/api/v1/system/public-config", "/api/v1/system/readiness", "/api/v1/system/versions"]);
});

test("uses the HttpOnly BFF session for the private card, loan and investment dashboard", async (t) => {
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); calls.push({ path, init });
    let data: unknown;
    if (path.endsWith("/member-auth/login")) data = null;
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
    else if (path.endsWith("/customers/customer-1/watchlist")) data = { customerId: "customer-1", items: [{ instrumentId: "instrument-1", instrumentName: "안심전자", maskedInstrumentCode: "00****" }], total: 1, version: 1 };
    else if (path.endsWith("/market-instruments/instrument-1/quote")) data = { instrumentId: "instrument-1", currentPrice: 10000 };
    else if (path.endsWith("/market-instruments/instrument-1/chart")) data = { instrumentId: "instrument-1", items: [] };
    else data = null;
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });

  const session = await loginPrivateCustomer("demo001", "a-secure-demo-password");
  const overview = await loadPrivateProductOverview(session);
  assert.equal(overview.cards[0]?.cardId, "card-1");
  assert.equal(overview.watchlist?.total, 1);
  assert.equal(overview.selectedQuote?.instrumentId, "instrument-1");
  const loginCall = calls.find((call) => call.path.endsWith("/member-auth/login"));
  assert.equal(new Headers(loginCall?.init?.headers).get("Authorization"), null);
  const protectedCalls = calls.filter((call) => !call.path.endsWith("/member-auth/login"));
  assert.ok(protectedCalls.length >= 10);
  assert.ok(protectedCalls.every((call) => new Headers(call.init?.headers).get("Authorization") === null));
});

test("loan simulation and logout remain same-origin HttpOnly session calls", async (t) => {
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    calls.push({ path: String(input), init });
    const data = String(input).includes("repayment-simulations")
      ? { productId: "product-1", principalAmount: 30000000, termMonths: 60, annualInterestRate: 4.5, monthlyPrincipal: 500000, firstPaymentAmount: 612500, finalPaymentAmount: 501875, totalInterest: 3431250, totalRepaymentAmount: 33431250, currency: "KRW", calculationRule: "EQUAL_PRINCIPAL_ESTIMATE_V1", personalized: false, creditAssessmentPerformed: false, applicationAvailable: false, externalActionExecuted: false }
      : null;
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });
  const session = privateSession();
  const result = await simulateLoanRepayment(session, "product-1", 30000000, 60, 4.5);
  await logoutPrivateCustomer(session);
  assert.equal(result.applicationAvailable, false);
  assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), { principalAmount: 30000000, termMonths: 60, annualInterestRate: 4.5 });
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("Authorization") === null));
});

test("loads deposit, FX, pension, trust and consent contracts under one customer Bearer session", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); paths.push(path);
    assert.equal(new Headers(init?.headers).get("Authorization"), null);
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
  assert.ok(calls.every((call) => new Headers(call.init?.headers).get("Authorization") === null));
});

test("loads customer profile, accessibility, trusted contacts and appeal targets in one Bearer session", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); paths.push(path);
    assert.equal(new Headers(init?.headers).get("Authorization"), null);
    let data: unknown;
    if (path.endsWith("/customers/customer-1")) data = { customerId: "customer-1", displayName: "합성고객", organization: "보호센터", region: "KR-11", status: "ACTIVE", version: 1, createdAt: "2026-08-30T00:00:00Z", updatedAt: "2026-08-30T00:00:00Z" };
    else if (path.endsWith("/preferences")) data = { customerId: "customer-1", smsNotificationEnabled: false, pushNotificationEnabled: false, inAppNotificationEnabled: true, version: 1, updatedAt: "2026-08-30T00:00:00Z" };
    else if (path.endsWith("/accessibility-settings")) data = { customerId: "customer-1", largeFont: true, highContrast: false, speechGuidance: false, oneHandMode: true, version: 1, updatedAt: "2026-08-30T00:00:00Z" };
    else if (path.endsWith("/data-summary")) data = { customerId: "customer-1", institutions: 2, accounts: 4, transactionsSynced: 42, lastSyncAt: null, dataFreshness: { accounts: "FIXED_SNAPSHOT", transactions: "FIXED_SNAPSHOT", baseline: "CURRENT" }, updatedAt: "2026-08-30T00:00:00Z" };
    else if (path.endsWith("/trusted-contacts")) data = { items: [{ contactId: "contact-1", customerId: "customer-1", consentId: "consent-1", displayName: "가족", relationshipCode: "FAMILY", maskedContact: "010-****-1234", recipientAccepted: false, acceptanceStatus: "PENDING_ACCEPTANCE", status: "ACTIVE", scopes: ["ALERT_REASON_SUMMARY"], validFrom: "2026-08-30T00:00:00Z", expiresAt: "2027-01-01T00:00:00Z", version: 1, authorizedToAct: false, externalContactEnabled: false }] };
    else if (path.endsWith("/consents")) data = { items: [{ consentId: "consent-1", customerId: "customer-1", purposeCode: "TRUSTED_CONTACT_DISCLOSURE", status: "GRANTED", scopes: ["CONTACT_MINIMUM"], grantedAt: "2026-08-30T00:00:00Z", expiresAt: "2027-08-30T00:00:00Z", withdrawnAt: null, withdrawalReason: null, version: 1, revocable: true }] };
    else data = { items: [{ alertId: "alert-1", signalId: "signal-1", customerId: "customer-1", state: "AWAITING_CONTEXT", severity: "MEDIUM", reasonCode: "DUPLICATE_TRANSFER", version: 1, deferredUntil: null, createdAt: "2026-08-30T00:00:00Z", updatedAt: "2026-08-30T00:00:00Z" }] };
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });
  const bundle = await loadPrivateCustomerCare(privateSession());
  assert.equal(bundle.summary.displayName, "합성고객");
  assert.equal(bundle.accessibility.largeFont, true);
  assert.equal(bundle.contacts[0]?.authorizedToAct, false);
  assert.equal(bundle.alerts[0]?.alertId, "alert-1");
  assert.equal(paths.length, 7);
});

test("connects customer settings, trusted-contact consent and human appeal mutations safely", async (t) => {
  const calls: Array<{ path: string; body: Record<string, unknown>; headers: Headers }> = [];
  t.mock.method(globalThis, "fetch", async (input, init) => {
    const path = String(input); const body = init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : {};
    calls.push({ path, body, headers: new Headers(init?.headers) });
    let data: unknown = null;
    if (path.endsWith("/consents")) data = { consentId: "consent-1", customerId: "customer-1", purposeCode: "TRUSTED_CONTACT_DISCLOSURE", status: "GRANTED", scopes: ["CONTACT_MINIMUM"], grantedAt: "2026-08-30T00:00:00Z", expiresAt: "2027-08-30T00:00:00Z", withdrawnAt: null, withdrawalReason: null, version: 1, revocable: true };
    else if (path.endsWith("/trusted-contacts")) data = { contactId: "contact-1", customerId: "customer-1", consentId: "consent-1", displayName: "가족", relationshipCode: "FAMILY", maskedContact: "010-****-1234", recipientAccepted: false, acceptanceStatus: "PENDING_ACCEPTANCE", status: "ACTIVE", scopes: ["ALERT_REASON_SUMMARY"], validFrom: "2026-08-30T00:00:00Z", expiresAt: "2027-01-01T00:00:00Z", version: 1, authorizedToAct: false, externalContactEnabled: false };
    else if (path.includes("/trusted-contacts/contact-1/revoke")) data = { contactId: "contact-1", status: "REVOKED", version: 2, authorizedToAct: false, externalContactEnabled: false };
    else if (path.endsWith("/appeals")) data = { appealId: "appeal-1", alertId: "alert-1", caseId: "case-1", reasonCode: "REQUEST_HUMAN_REVIEW", status: "SUBMITTED", previousState: "AWAITING_CONTEXT", currentState: "BANK_REVIEW", alertVersion: 2, submittedAt: "2026-08-30T00:00:00Z", idempotencyReplayed: false, financialActionExecuted: false, externalNotificationSent: false };
    return new Response(envelope(data), { headers: { "content-type": "application/json" } });
  });
  const session = privateSession();
  await updateCustomerDisplayName(session, 1, "안심 고객");
  await updateCustomerPreferences(session, { customerId: "customer-1", smsNotificationEnabled: false, pushNotificationEnabled: true, inAppNotificationEnabled: true, version: 1, updatedAt: "" });
  await updateAccessibilitySettings(session, { customerId: "customer-1", largeFont: true, highContrast: true, speechGuidance: true, oneHandMode: true, version: 1, updatedAt: "" });
  const consent = await ensureTrustedContactConsent(session, []);
  const contact = await createTrustedContact(session, consent.consentId, { displayName: "가족", relationshipCode: "FAMILY", maskedContact: "010-****-1234", scopes: ["ALERT_REASON_SUMMARY"] });
  await revokeTrustedContact(session, contact, "고객 직접 철회");
  const appeal = await submitAlertAppeal(session, { alertId: "alert-1", signalId: "signal-1", customerId: "customer-1", state: "AWAITING_CONTEXT", severity: "MEDIUM", reasonCode: "DUPLICATE_TRANSFER", version: 1, deferredUntil: null, createdAt: "", updatedAt: "" }, "REQUEST_HUMAN_REVIEW", "사람의 재검토를 요청합니다.");
  assert.equal(appeal.financialActionExecuted, false);
  assert.ok(calls.every((call) => call.headers.get("Authorization") === null));
  assert.ok(calls.every((call) => call.headers.get("Idempotency-Key")));
  assert.deepEqual(calls[0]?.body, { expectedVersion: 1, displayName: "안심 고객" });
  assert.deepEqual(calls.at(-1)?.body, { reasonCode: "REQUEST_HUMAN_REVIEW", statement: "사람의 재검토를 요청합니다.", expectedVersion: 1 });
});

function privateSession() {
  return { customerId: "customer-1", displayName: "합성고객", roles: [], permissions: [] };
}

test("401이면 HttpOnly 세션을 자동 갱신하고 한 번만 재시도한다", async (t) => {
  let refreshCalls = 0;
  t.mock.method(globalThis, "fetch", async (input, init) => {
    if (String(input) === "/api/v1/auth/me") return new Response(null, { status: 401 });
    assert.equal(String(input), "/api/member-auth/refresh");
    assert.equal(init?.body, undefined);
    refreshCalls += 1;
    return new Response(envelope(null), { headers: { "content-type": "application/json" } });
  });
  const session = privateSession();
  let operationCalls = 0;
  const result = await withPrivateCustomerSession(session, async () => {
    operationCalls += 1;
    if (operationCalls === 1) throw new ApiClientError("http", "expired upstream", 401);
    return "retried";
  });
  assert.equal(result, "retried");
  assert.equal(refreshCalls, 1);
  assert.equal(operationCalls, 2);
  assert.equal(session.invalidated, false);
});

test("refresh 실패 시 token을 메모리에서 폐기하고 안전 로그아웃한다", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response(envelope(null, 401), {
    status: 401, headers: { "content-type": "application/json" },
  }));
  const session = privateSession();
  await assert.rejects(
    withPrivateCustomerSession(session, async () => { throw new ApiClientError("http", "expired", 401); }),
    (reason: unknown) => reason instanceof PrivateSessionExpiredError,
  );
  assert.equal(session.invalidated, true);
});
