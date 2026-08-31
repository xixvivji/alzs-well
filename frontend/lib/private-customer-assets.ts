import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type DepositHolding = { holdingId: string; accountId: string; institutionName: string; displayName: string; maskedAccountNumber: string; productType: string; principalAmount: number; currentBalance: number; accruedInterest: number; annualInterestRate: number; openedOn: string; maturityDate: string; status: string; currency: string; dataAsOf: string };
export type DepositDetail = { deposit: DepositHolding; expectedMaturityAmount: number; maturityActionAvailable: false; syntheticData: boolean; externalProviderCalled: false; externalActionExecuted: false };
export type DepositProduct = { productId: string; institutionName: string; productName: string; productType: string; minPrincipal: number; maxPrincipal: number; minTermMonths: number; maxTermMonths: number; interestPaymentType: string; summary: string; status: string; currency: string; dataAsOf: string };
export type DepositProductDetail = { product: DepositProduct; cautionText: string; applicationAvailable: false; syntheticData: boolean; externalProviderCalled: false; externalActionExecuted: false };
export type DepositRate = { rateId: string; tierCode: string; minTermMonths: number; maxTermMonths: number; annualInterestRate: number; rateType: string; dataAsOf: string };
export type MaturityOption = { optionId: string; optionCode: string; title: string; description: string; displayOrder: number };
export type InterestSimulation = { productId: string; inputMode: string; inputAmount: number; termMonths: number; annualInterestRate: number; totalPrincipal: number; grossInterest: number; estimatedTax: number; netInterest: number; estimatedMaturityAmount: number; currency: string; calculationRule: string; personalized: false; applicationAvailable: false; externalActionExecuted: false };

export type FxRate = { rateId: string; currency: string; currencyName: string; unitAmount: number; baseRate: number; remittanceSendRate: number; remittanceReceiveRate: number; cashBuyRate: number; cashSellRate: number; quotedAt: string; dataAsOf: string };
export type FxAccount = { accountId: string; institutionName: string; maskedAccountNumber: string; accountName: string; currency: string; balance: number; availableBalance: number; status: string; dataAsOf: string };
export type FxRemittance = { remittanceId: string; destinationCountryCode: string; beneficiaryAlias: string; currency: string; foreignAmount: number; appliedRate: number; krwAmount: number; feeAmount: number; status: string; requestedAt: string; completedAt: string | null };
export type FxSimulation = { fromCurrency: string; toCurrency: string; inputAmount: number; appliedRate: number; unitAmount: number; convertedAmount: number; calculationRule: string; rateQuotedAt: string; personalized: false; exchangeCreated: false; syntheticData: boolean; externalProviderCalled: false; externalActionExecuted: false };

export type PensionHolding = { holdingId: string; institutionName: string; displayName: string; maskedContractReference: string; pensionType: string; status: string; contributedAmount: number; currentValue: number; expectedBenefitStartDate: string; currency: string; dataAsOf: string };
export type PensionScenario = { projectionId: string; scenarioCode: string; assumedAnnualReturn: number; projectedValue: number; projectedMonthlyBenefit: number; benefitStartDate: string; calculatedOn: string };
export type PensionProjection = { holdingId: string; scenarios: PensionScenario[]; disclaimer: string; guaranteed: false; recommendationProvided: false; actionAvailable: false; syntheticData: boolean; externalActionExecuted: false };
export type TrustHolding = { trustId: string; institutionName: string; displayName: string; maskedContractReference: string; trustType: string; purposeCode: string; status: string; entrustedPrincipal: number; currentValue: number; beneficiaryCount: number; startedOn: string; maturityDate: string; nextReviewDate: string; currency: string; dataAsOf: string };
export type TrustDetail = { trust: TrustHolding; beneficiaryIdentityProvided: false; contractActionAvailable: false; syntheticData: boolean; externalProviderCalled: false; externalActionExecuted: false };

export type Consent = { consentId: string; customerId: string; purposeCode: string; status: string; scopes: string[]; grantedAt: string; expiresAt: string; withdrawnAt: string | null; withdrawalReason: string | null; version: number; revocable: boolean };
export type ConsentEvent = { eventId: string; eventType: string; statusSnapshot: string; scopeSnapshot: string[]; reason: string | null; actorId: string; occurredAt: string; version: number };
export type DisclosureEvaluation = { evaluationId: string; consentId: string; customerId: string; purposeCode: string; requestedScopes: string[]; missingScopes: string[]; consentStatus: string; decision: string; policyVersion: string; disclosureAllowed: boolean; externalDisclosureRequested: false; externalDisclosureCreated: false };

export type PrivateCustomerAssets = {
  deposits: DepositHolding[]; depositDetail: DepositDetail | null; depositProducts: DepositProduct[];
  depositProductDetail: DepositProductDetail | null; depositRates: DepositRate[]; maturityOptions: MaturityOption[];
  fxRates: FxRate[]; selectedFxRate: FxRate | null; fxAccounts: FxAccount[]; remittances: FxRemittance[];
  pensions: PensionHolding[]; pensionProjection: PensionProjection | null;
  trusts: TrustHolding[]; trustDetail: TrustDetail | null;
  consents: Consent[]; consentDetail: Consent | null; consentHistory: ConsentEvent[];
};

export async function loadPrivateCustomerAssets(session: PrivateCustomerSession): Promise<PrivateCustomerAssets> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const auth = { accessToken };
    const customer = { customerId: session.customerId };
    const [depositList, productList, rateList, fxAccounts, remittances, pensionList, trustList, consentList] = await Promise.all([
    invokeApiOperation<{ items: DepositHolding[] }>("GET /api/v1/customers/{customerId}/deposit-holdings", { path: customer, ...auth }),
    invokeApiOperation<{ items: DepositProduct[] }>("GET /api/v1/deposit-products", auth),
    invokeApiOperation<{ items: FxRate[] }>("GET /api/v1/fx/rates", auth),
    invokeApiOperation<{ items: FxAccount[] }>("GET /api/v1/customers/{customerId}/foreign-currency-accounts", { path: customer, ...auth }),
    invokeApiOperation<{ items: FxRemittance[] }>("GET /api/v1/customers/{customerId}/overseas-remittance-history", { path: customer, ...auth }),
    invokeApiOperation<{ items: PensionHolding[] }>("GET /api/v1/customers/{customerId}/pension-holdings", { path: customer, ...auth }),
    invokeApiOperation<{ items: TrustHolding[] }>("GET /api/v1/customers/{customerId}/trust-holdings", { path: customer, ...auth }),
    invokeApiOperation<{ items: Consent[] }>("GET /api/v1/customers/{customerId}/consents", { path: customer, ...auth }),
    ]);
    const deposits = items(depositList.body.data); const products = items(productList.body.data);
    const fxRates = items(rateList.body.data); const pensions = items(pensionList.body.data);
    const trusts = items(trustList.body.data); const consents = items(consentList.body.data);
    const deposit = deposits[0]; const product = products[0]; const pension = pensions[0]; const trust = trusts[0]; const consent = consents[0]; const currency = fxRates[0]?.currency;
    const [depositDetail, productDetail, depositRates, maturityOptions, selectedFxRate, pensionProjection, trustDetail, consentDetail, consentHistory] = await Promise.all([
    deposit ? invokeApiOperation<DepositDetail>("GET /api/v1/deposit-holdings/{holdingId}", { path: { holdingId: deposit.holdingId }, ...auth }) : null,
    product ? invokeApiOperation<DepositProductDetail>("GET /api/v1/deposit-products/{productId}", { path: { productId: product.productId }, ...auth }) : null,
    product ? invokeApiOperation<{ items: DepositRate[] }>("GET /api/v1/deposit-products/{productId}/rates", { path: { productId: product.productId }, ...auth }) : null,
    deposit ? invokeApiOperation<{ items: MaturityOption[] }>("GET /api/v1/deposit-holdings/{holdingId}/maturity-options", { path: { holdingId: deposit.holdingId }, ...auth }) : null,
    currency ? invokeApiOperation<FxRate>("GET /api/v1/fx/rates/{currency}", { path: { currency }, ...auth }) : null,
    pension ? invokeApiOperation<PensionProjection>("GET /api/v1/pension-holdings/{holdingId}/projection", { path: { holdingId: pension.holdingId }, ...auth }) : null,
    trust ? invokeApiOperation<TrustDetail>("GET /api/v1/trust-holdings/{trustId}", { path: { trustId: trust.trustId }, ...auth }) : null,
    consent ? invokeApiOperation<Consent>("GET /api/v1/customers/{customerId}/consents/{consentId}", { path: { ...customer, consentId: consent.consentId }, ...auth }) : null,
    consent ? invokeApiOperation<{ items: ConsentEvent[] }>("GET /api/v1/customers/{customerId}/consents/{consentId}/history", { path: { ...customer, consentId: consent.consentId }, ...auth }) : null,
    ]);
    return {
      deposits, depositDetail: depositDetail?.body.data ?? null, depositProducts: products,
      depositProductDetail: productDetail?.body.data ?? null, depositRates: items(depositRates?.body.data), maturityOptions: items(maturityOptions?.body.data),
      fxRates, selectedFxRate: selectedFxRate?.body.data ?? null, fxAccounts: items(fxAccounts.body.data), remittances: items(remittances.body.data),
      pensions, pensionProjection: pensionProjection?.body.data ?? null, trusts, trustDetail: trustDetail?.body.data ?? null,
      consents, consentDetail: consentDetail?.body.data ?? null, consentHistory: items(consentHistory?.body.data),
    };
  });
}

export async function simulateDepositInterest(session: PrivateCustomerSession, productId: string, principalAmount: number, termMonths: number): Promise<InterestSimulation> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<InterestSimulation>("POST /api/v1/deposit-products/{productId}/interest-simulations", {
      path: { productId }, accessToken, body: { principalAmount, termMonths },
    });
    return required(response.body.data, "예금 이자 모의계산");
  });
}

export async function simulateFxExchange(session: PrivateCustomerSession, fromCurrency: string, toCurrency: string, amount: number): Promise<FxSimulation> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<FxSimulation>("POST /api/v1/fx/exchange-simulations", {
      accessToken, body: { fromCurrency, toCurrency, amount },
    });
    return required(response.body.data, "환전 모의계산");
  });
}

export async function grantConsent(session: PrivateCustomerSession, purposeCode: string, scopes: string[], expiresAt: string): Promise<Consent> {
  const idempotencyKey = crypto.randomUUID();
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<Consent>("POST /api/v1/customers/{customerId}/consents", {
      path: { customerId: session.customerId }, accessToken, idempotencyKey,
      body: { purposeCode, scopes, expiresAt },
    });
    return required(response.body.data, "동의 등록");
  });
}

export async function withdrawConsent(session: PrivateCustomerSession, consent: Consent, reason: string): Promise<Consent> {
  const idempotencyKey = crypto.randomUUID();
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<Consent>("POST /api/v1/customers/{customerId}/consents/{consentId}/withdraw", {
      path: { customerId: session.customerId, consentId: consent.consentId }, accessToken,
      idempotencyKey, body: { expectedVersion: consent.version, reason },
    });
    return required(response.body.data, "동의 철회");
  });
}

export async function evaluateDisclosure(session: PrivateCustomerSession, consent: Consent): Promise<DisclosureEvaluation> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<DisclosureEvaluation>("POST /api/v1/customers/{customerId}/disclosure-evaluations", {
      path: { customerId: session.customerId }, accessToken,
      body: { consentId: consent.consentId, purposeCode: consent.purposeCode, requestedScopes: consent.scopes },
    });
    return required(response.body.data, "최소정보 제공 평가");
  });
}

function items<T>(value: { items: T[] } | null | undefined): T[] { return value?.items ?? []; }
function required<T>(value: T | null, label: string): T { if (!value) throw new Error(`${label} 응답을 확인해 주세요.`); return value; }
