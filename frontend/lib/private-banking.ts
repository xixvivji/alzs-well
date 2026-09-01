import { invokeApiOperation } from "./api-operation-client";
import { withPrivateCustomerSession } from "./private-auth-session";
import type { PrivateCustomerSession } from "./private-financial-products";

export type Account = {
  accountId: string; institutionName: string; accountType: string; displayName: string;
  maskedAccountNumber: string; accountStatus: string; currentBalance: number;
  availableBalance: number; currency: string; balanceAsOf: string; dataAsOf: string;
};
export type Transaction = {
  transactionId: string; accountId: string; accountDisplayName: string; institutionName: string;
  counterpartyId?: string; counterpartyName?: string; occurredAt: string; direction: string;
  transactionType: string; status: string; amount: number; currency: string; balanceAfter: number;
  description: string; category: string; customerNote?: string; preferenceVersion: number;
};
export type FinancialSummary = {
  totalAssets: number; totalLiabilities: number; netAssets: number; periodInflow: number;
  periodOutflow: number; netCashflow: number; accountCount: number; liabilityCount: number;
  currency: string; dataAsOf: string; syntheticData: boolean;
};
export type AssetBreakdown = { institutionName: string; assetClass: string; amount: number; percentage: number; accountCount: number };
export type Trend = { date: string; totalAssets: number; totalLiabilities: number; netAssets: number };
export type CashflowCategory = { category: string; inflow: number; outflow: number; count: number };
export type CalendarEvent = { eventId: string; eventType: string; title: string; scheduledDate: string; direction: string; expectedAmount: number; currency: string; certainty: string };
export type Freshness = { institutionName: string; connectionStatus: string; lastSyncedAt: string; accountCount: number; transactionCount: number; freshnessStatus: string; complete: boolean };
export type RecurringPayment = {
  recurringPaymentId: string; institutionName: string; displayName: string; paymentType: string;
  categoryCode: string; cadence: string; expectedAmount: number; currency: string;
  nextExpectedDate: string; status: string; observationStatus: string; version: number;
  reminderSettings: { enabled: boolean; leadDays: number; channels: string[] };
};
export type Beneficiary = { beneficiaryId: string; institutionName: string; displayName: string; maskedAccountReference: string; beneficiaryType: string; status: string; favorite: boolean };
export type TransferLimit = { perTransferLimit: number; dailyLimit: number; dailyUsedAmount: number; dailyRemainingAmount: number; currency: string; dataAsOf: string };
export type TransferResult = { outcomeCode?: string; allowed?: boolean; decisionCode?: string; estimatedFee?: number; totalDebit?: number; projectedAvailableBalance?: number; checks: Array<{ checkCode: string; passed: boolean; message: string }>; transferCreated: false; authorizationCreated: false };
export type TransferTemplate = { templateId: string; templateName: string; sourceAccountId: string; sourceAccountDisplayName: string; maskedSourceAccountNumber: string; beneficiaryId: string; beneficiaryDisplayName: string; maskedBeneficiaryAccount: string; amount: number; currency: string; purposeCode: string; status: string };

export type BankingOverview = {
  summary: FinancialSummary; breakdown: AssetBreakdown[]; trends: Trend[];
  cashflow: { totalInflow: number; totalOutflow: number; netCashflow: number; categories: CashflowCategory[] };
  expenses: { totalExpense: number; items: Array<{ category: string; institutionName: string; amount: number; percentage: number; count: number }> };
  calendar: CalendarEvent[]; freshness: Freshness[]; accounts: Account[];
};

export type AccountWorkspace = {
  accounts: Account[]; transactions: Transaction[]; recurring: RecurringPayment[];
  missed: Array<{ payment: RecurringPayment; missedCount: number; latestMissedDate: string; totalMissedAmount: number; reasonCode: string }>;
  duplicates: Array<{ payment: RecurringPayment; duplicateOccurrenceCount: number; cycleDate: string; duplicateAmount: number; reasonCode: string }>;
  transactionSummary: { totalInflow: number; totalOutflow: number; netCashflow: number; transactionCount: number; categories: CashflowCategory[] };
  accountDetail: Record<string, unknown> | null; balance: Record<string, unknown> | null;
  balanceHistory: Array<Record<string, unknown>>; restrictions: Array<Record<string, unknown>>;
  interest: Record<string, unknown> | null; statements: Array<Record<string, unknown>>;
  recurringCounterparties: Array<Record<string, unknown>>; groups: Array<Record<string, unknown>>;
};

export type TransferWorkspace = { accounts: Account[]; beneficiaries: Beneficiary[]; limit: TransferLimit; templates: TransferTemplate[] };

const data = <T>(response: { body: { data: T | null } }, label: string): T => {
  if (response.body.data === null) throw new Error(`${label} 응답을 확인해 주세요.`);
  return response.body.data;
};

export async function loadBankingOverview(session: PrivateCustomerSession): Promise<BankingOverview> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const path = { customerId: session.customerId }; const auth = { accessToken };
    const [summary, breakdown, trends, cashflow, expenses, calendar, freshness, accounts] = await Promise.all([
      invokeApiOperation<FinancialSummary>("GET /api/v1/customers/{customerId}/financial-summary", { path, ...auth }),
      invokeApiOperation<{ items: AssetBreakdown[] }>("GET /api/v1/customers/{customerId}/asset-breakdown", { path, ...auth }),
      invokeApiOperation<{ items: Trend[] }>("GET /api/v1/customers/{customerId}/asset-trends", { path, ...auth }),
      invokeApiOperation<BankingOverview["cashflow"]>("GET /api/v1/customers/{customerId}/cashflow-summary", { path, ...auth }),
      invokeApiOperation<BankingOverview["expenses"]>("GET /api/v1/customers/{customerId}/expense-summary", { path, ...auth }),
      invokeApiOperation<{ items: CalendarEvent[] }>("GET /api/v1/customers/{customerId}/asset-calendar", { path, ...auth }),
      invokeApiOperation<{ items: Freshness[] }>("GET /api/v1/customers/{customerId}/data-freshness", { path, ...auth }),
      invokeApiOperation<{ items: Account[] }>("GET /api/v1/customers/{customerId}/accounts", { path, ...auth }),
    ]);
    return {
      summary: data(summary, "금융 요약"), breakdown: data(breakdown, "자산 구성").items,
      trends: data(trends, "자산 추세").items, cashflow: data(cashflow, "현금흐름"),
      expenses: data(expenses, "지출 요약"), calendar: data(calendar, "금융 일정").items,
      freshness: data(freshness, "데이터 최신성").items, accounts: data(accounts, "계좌").items,
    };
  });
}

export async function loadAccountWorkspace(session: PrivateCustomerSession, accountId?: string, query?: string): Promise<AccountWorkspace> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const customer = { customerId: session.customerId }; const auth = { accessToken };
    const [accountList, transactionPage, transactionSummary, recurring, missed, duplicates, groups] = await Promise.all([
      invokeApiOperation<{ items: Account[] }>("GET /api/v1/customers/{customerId}/accounts", { path: customer, ...auth }),
      invokeApiOperation<{ items: Transaction[] }>("GET /api/v1/customers/{customerId}/transactions/search", { path: customer, query: { q: query || undefined, accountId, limit: 50 }, ...auth }),
      invokeApiOperation<AccountWorkspace["transactionSummary"]>("GET /api/v1/customers/{customerId}/transactions/summary", { path: customer, ...auth }),
      invokeApiOperation<{ items: RecurringPayment[] }>("GET /api/v1/customers/{customerId}/recurring-payments", { path: customer, ...auth }),
      invokeApiOperation<{ items: AccountWorkspace["missed"] }>("GET /api/v1/customers/{customerId}/recurring-payments/missed", { path: customer, ...auth }),
      invokeApiOperation<{ items: AccountWorkspace["duplicates"] }>("GET /api/v1/customers/{customerId}/recurring-payments/duplicates", { path: customer, ...auth }),
      invokeApiOperation<{ items: Array<Record<string, unknown>> }>("GET /api/v1/customers/{customerId}/account-groups", { path: customer, ...auth }),
    ]);
    const accounts = data(accountList, "계좌 목록").items;
    const selected = accountId ?? accounts[0]?.accountId;
    const detailCalls = selected ? await Promise.all([
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/accounts/{accountId}", { path: { accountId: selected }, ...auth }),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/accounts/{accountId}/balance", { path: { accountId: selected }, ...auth }),
      invokeApiOperation<{ items: Array<Record<string, unknown>> }>("GET /api/v1/accounts/{accountId}/balance-history", { path: { accountId: selected }, ...auth }),
      invokeApiOperation<{ items: Array<Record<string, unknown>> }>("GET /api/v1/accounts/{accountId}/restrictions", { path: { accountId: selected }, ...auth }),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/accounts/{accountId}/interest-summary", { path: { accountId: selected }, ...auth }),
      invokeApiOperation<{ items: Array<Record<string, unknown>> }>("GET /api/v1/accounts/{accountId}/statements", { path: { accountId: selected }, ...auth }),
      invokeApiOperation<{ items: Array<Record<string, unknown>> }>("GET /api/v1/accounts/{accountId}/recurring-counterparties", { path: { accountId: selected }, ...auth }),
    ]) : null;
    return {
      accounts, transactions: data(transactionPage, "거래내역").items,
      transactionSummary: data(transactionSummary, "거래 요약"), recurring: data(recurring, "정기납부").items,
      missed: data(missed, "누락 납부").items, duplicates: data(duplicates, "중복 납부").items,
      groups: data(groups, "계좌 그룹").items, accountDetail: detailCalls ? data(detailCalls[0], "계좌 상세") : null,
      balance: detailCalls ? data(detailCalls[1], "잔액") : null,
      balanceHistory: detailCalls ? data(detailCalls[2], "잔액 추세").items : [],
      restrictions: detailCalls ? data(detailCalls[3], "계좌 제한").items : [],
      interest: detailCalls ? data(detailCalls[4], "이자") : null,
      statements: detailCalls ? data(detailCalls[5], "명세서").items : [],
      recurringCounterparties: detailCalls ? data(detailCalls[6], "반복 거래처").items : [],
    };
  });
}

export async function loadTransferWorkspace(session: PrivateCustomerSession): Promise<TransferWorkspace> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const path = { customerId: session.customerId }; const auth = { accessToken };
    const [accounts, beneficiaries, limit, templates] = await Promise.all([
      invokeApiOperation<{ items: Account[] }>("GET /api/v1/customers/{customerId}/accounts", { path, ...auth }),
      invokeApiOperation<{ items: Beneficiary[] }>("GET /api/v1/customers/{customerId}/beneficiaries", { path, ...auth }),
      invokeApiOperation<TransferLimit>("GET /api/v1/customers/{customerId}/transfer-limits", { path, ...auth }),
      invokeApiOperation<{ items: TransferTemplate[] }>("GET /api/v1/customers/{customerId}/transfer-templates", { path, ...auth }),
    ]);
    return { accounts: data(accounts, "계좌" ).items, beneficiaries: data(beneficiaries, "수취인").items, limit: data(limit, "이체 한도"), templates: data(templates, "이체 양식").items };
  });
}

export async function evaluateTransfer(session: PrivateCustomerSession, sourceAccountId: string, beneficiaryId: string, amount: number, purposeCode: string): Promise<{ simulation: TransferResult; validation: TransferResult }> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const common = { customerId: session.customerId, sourceAccountId, beneficiaryId, amount, currency: "KRW" };
    const [simulation, validation] = await Promise.all([
      invokeApiOperation<TransferResult>("POST /api/v1/transfer-simulations", { accessToken, body: common }),
      invokeApiOperation<TransferResult>("POST /api/v1/transfer-validations", { accessToken, body: { ...common, purposeCode, recipientConfirmed: true } }),
    ]);
    return { simulation: data(simulation, "이체 모의계산"), validation: data(validation, "이체 사전검증") };
  });
}

export async function saveTransferTemplate(session: PrivateCustomerSession, templateName: string, sourceAccountId: string, beneficiaryId: string, amount: number, purposeCode: string): Promise<void> {
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("POST /api/v1/customers/{customerId}/transfer-templates", {
    path: { customerId: session.customerId }, accessToken, idempotencyKey: crypto.randomUUID(),
    body: { templateName, sourceAccountId, beneficiaryId, amount, currency: "KRW", purposeCode },
  }));
}

export async function deleteTransferTemplate(session: PrivateCustomerSession, templateId: string): Promise<void> {
  await withPrivateCustomerSession(session, (accessToken) => invokeApiOperation("DELETE /api/v1/customers/{customerId}/transfer-templates/{templateId}", {
    path: { customerId: session.customerId, templateId }, accessToken, idempotencyKey: crypto.randomUUID(),
  }));
}
