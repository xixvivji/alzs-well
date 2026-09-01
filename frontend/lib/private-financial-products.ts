import { invokeApiOperation } from "./api-operation-client";
import { invalidatePrivateCustomerSession, withPrivateCustomerSession } from "./private-auth-session";

export type PrivateCustomerSession = {
  customerId: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  invalidated?: boolean;
};
type CurrentUser = { customerId: string; displayName: string; roles: string[] };
type PermissionList = { permissions: string[] };
export type CardSummary = { cardId: string; institutionName: string; displayName: string; maskedCardNumber: string; cardType: string; brandCode: string; status: string; paymentDay: number; currentUsageAmount: number; currency: string; dataAsOf: string };
export type CardDetail = { card: CardSummary; nextPaymentDueDate: string; currentDueAmount: number; syntheticData: boolean; externalActionExecuted: false };
export type CardTransaction = { cardTransactionId: string; occurredAt: string; merchantDisplayName: string; categoryCode: string; amount: number; status: string; installmentMonths: number; currency: string };
export type CardStatement = { statementId: string; periodFrom: string; periodTo: string; dueDate: string; totalAmount: number; paidAmount: number; remainingDueAmount: number; status: string; currency: string };
export type CardPaymentDue = { dueDate: string; amount: number; currency: string; paymentStatus: string; paymentAvailable: false };
export type CardLimit = { totalLimitAmount: number; usedAmount: number; availableLimitAmount: number; currency: string; limitChangeAvailable: false };
export type Loan = { loanId: string; institutionName: string; displayName: string; maskedReference: string; loanType: string; originalPrincipal: number; outstandingAmount: number; scheduledAmount: number; annualInterestRate: number; nextDueDate: string; maturityDate: string; repaymentMethod: string; status: string; currency: string };
export type RepaymentInstallment = { installmentId: string; installmentNumber: number; dueDate: string; principalAmount: number; interestAmount: number; totalAmount: number; status: string };
export type LoanProduct = { productId: string; institutionName: string; productName: string; productType: string; minPrincipal: number; maxPrincipal: number; minTermMonths: number; maxTermMonths: number; minAnnualInterestRate: number; maxAnnualInterestRate: number; repaymentMethod: string; summary: string; currency: string };
export type RepaymentSimulation = { productId: string; principalAmount: number; termMonths: number; annualInterestRate: number; monthlyPrincipal: number; firstPaymentAmount: number; finalPaymentAmount: number; totalInterest: number; totalRepaymentAmount: number; currency: string; calculationRule: string; personalized: false; creditAssessmentPerformed: false; applicationAvailable: false; externalActionExecuted: false };
export type InvestmentAccount = { accountId: string; institutionName: string; displayName: string; maskedAccountNumber: string; accountType: string; status: string; cashBalance: number; totalMarketValue: number; currency: string };
export type Allocation = { assetClass: string; marketValue: number; weightPercent: number };
export type Position = { positionId: string; assetClass: string; instrumentName: string; maskedInstrumentCode: string; quantity: number; averagePurchasePrice: number; currentPrice: number; marketValue: number; unrealizedProfitLoss: number; currency: string };
export type Order = { orderId: string; instrumentName: string; maskedInstrumentCode: string; side: string; quantity: number; orderPrice: number; filledQuantity: number; status: string; orderedAt: string; currency: string };
export type Watchlist = { customerId: string; items: Array<{ instrumentId: string; instrumentName: string; maskedInstrumentCode: string; displayOrder: number; currentPrice: number; changeRate: number; currency: string }>; total: number; version: number; updatedAt: string };

export type PrivateProductOverview = {
  cards: CardSummary[]; cardDetail: CardDetail | null; cardTransactions: CardTransaction[];
  statements: CardStatement[]; paymentDue: CardPaymentDue | null; cardLimit: CardLimit | null;
  loans: Loan[]; repaymentSchedule: RepaymentInstallment[]; loanProducts: LoanProduct[];
  investments: InvestmentAccount[]; allocations: Allocation[]; positions: Position[]; orders: Order[];
  loanDetail: Record<string, unknown> | null; loanProductDetail: Record<string, unknown> | null;
  watchlist: Watchlist | null; selectedQuote: Record<string, unknown> | null; selectedChart: Record<string, unknown> | null;
};

export async function loginPrivateCustomer(loginId: string, password: string): Promise<PrivateCustomerSession> {
  await memberAuthRequest("/api/member-auth/login", { loginId, password });
  try {
    return await restorePrivateCustomerSession();
  } catch (error) {
    await memberAuthRequest("/api/member-auth/logout").catch(() => undefined);
    throw error;
  }
}

export async function restorePrivateCustomerSession(): Promise<PrivateCustomerSession> {
  const [me, permissions] = await Promise.all([
    invokeApiOperation<CurrentUser>("GET /api/v1/auth/me"),
    invokeApiOperation<PermissionList>("GET /api/v1/auth/me/permissions"),
  ]);
  const user = required(me.body.data, "현재 사용자");
  return { customerId: user.customerId, displayName: user.displayName, roles: user.roles, permissions: required(permissions.body.data, "권한").permissions };
}

export async function logoutPrivateCustomer(session: PrivateCustomerSession): Promise<void> {
  try {
    await memberAuthRequest("/api/member-auth/logout");
  } finally {
    invalidatePrivateCustomerSession(session);
  }
}

async function memberAuthRequest(path: string, body?: unknown): Promise<void> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(payload?.message ?? "로그인 요청을 처리하지 못했습니다.");
  }
}

export async function loadPrivateProductOverview(session: PrivateCustomerSession): Promise<PrivateProductOverview> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const auth = { accessToken };
    const customer = { customerId: session.customerId };
    const [cardList, loanList, loanProducts, investmentList] = await Promise.all([
      invokeApiOperation<{ items: CardSummary[] }>("GET /api/v1/customers/{customerId}/cards", { path: customer, ...auth }),
      invokeApiOperation<{ items: Loan[] }>("GET /api/v1/customers/{customerId}/loan-holdings", { path: customer, ...auth }),
      invokeApiOperation<{ items: LoanProduct[] }>("GET /api/v1/loan-products", auth),
      invokeApiOperation<{ items: InvestmentAccount[] }>("GET /api/v1/customers/{customerId}/investment-accounts", { path: customer, ...auth }),
    ]);
    const cards = required(cardList.body.data, "카드 목록").items;
    const loans = required(loanList.body.data, "대출 목록").items;
    const products = required(loanProducts.body.data, "대출상품 목록").items;
    const investments = required(investmentList.body.data, "투자계좌 목록").items;
    const card = cards[0]; const loan = loans[0]; const investment = investments[0];
    const [cardDetail, cardTransactions, statements, paymentDue, cardLimit, repaymentSchedule, loanDetail, loanProductDetail, portfolio, positions, orders, watchlist] = await Promise.all([
      card ? invokeApiOperation<CardDetail>("GET /api/v1/cards/{cardId}", { path: { cardId: card.cardId }, ...auth }) : null,
      card ? invokeApiOperation<{ items: CardTransaction[] }>("GET /api/v1/cards/{cardId}/transactions", { path: { cardId: card.cardId }, query: { limit: 20 }, ...auth }) : null,
      card ? invokeApiOperation<{ items: CardStatement[] }>("GET /api/v1/cards/{cardId}/statements", { path: { cardId: card.cardId }, ...auth }) : null,
      card ? invokeApiOperation<CardPaymentDue>("GET /api/v1/cards/{cardId}/payment-due", { path: { cardId: card.cardId }, ...auth }) : null,
      card ? invokeApiOperation<CardLimit>("GET /api/v1/cards/{cardId}/limits", { path: { cardId: card.cardId }, ...auth }) : null,
      loan ? invokeApiOperation<{ items: RepaymentInstallment[] }>("GET /api/v1/loan-holdings/{loanId}/repayment-schedule", { path: { loanId: loan.loanId }, ...auth }) : null,
      loan ? invokeApiOperation<Record<string, unknown>>("GET /api/v1/loan-holdings/{loanId}", { path: { loanId: loan.loanId }, ...auth }) : null,
      products[0] ? invokeApiOperation<Record<string, unknown>>("GET /api/v1/loan-products/{productId}", { path: { productId: products[0].productId }, ...auth }) : null,
      investment ? invokeApiOperation<{ allocations: Allocation[] }>("GET /api/v1/investment-accounts/{accountId}/portfolio", { path: { accountId: investment.accountId }, ...auth }) : null,
      investment ? invokeApiOperation<{ items: Position[] }>("GET /api/v1/investment-accounts/{accountId}/positions", { path: { accountId: investment.accountId }, ...auth }) : null,
      investment ? invokeApiOperation<{ items: Order[] }>("GET /api/v1/investment-accounts/{accountId}/orders", { path: { accountId: investment.accountId }, ...auth }) : null,
      invokeApiOperation<Watchlist>("GET /api/v1/customers/{customerId}/watchlist", { path: customer, ...auth }),
    ]);
    const watchlistData = watchlist.body.data; const instrumentId = watchlistData?.items[0]?.instrumentId;
    const [selectedQuote, selectedChart] = instrumentId ? await Promise.all([
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/market-instruments/{instrumentId}/quote", { path: { instrumentId }, ...auth }),
      invokeApiOperation<Record<string, unknown>>("GET /api/v1/market-instruments/{instrumentId}/chart", { path: { instrumentId }, ...auth }),
    ]) : [null, null];
    return {
      cards, cardDetail: cardDetail?.body.data ?? null, cardTransactions: cardTransactions?.body.data?.items ?? [], statements: statements?.body.data?.items ?? [], paymentDue: paymentDue?.body.data ?? null, cardLimit: cardLimit?.body.data ?? null,
      loans, repaymentSchedule: repaymentSchedule?.body.data?.items ?? [], loanProducts: products,
      investments, allocations: portfolio?.body.data?.allocations ?? [], positions: positions?.body.data?.items ?? [], orders: orders?.body.data?.items ?? [],
      loanDetail: loanDetail?.body.data ?? null, loanProductDetail: loanProductDetail?.body.data ?? null,
      watchlist: watchlistData ?? null, selectedQuote: selectedQuote?.body.data ?? null, selectedChart: selectedChart?.body.data ?? null,
    };
  });
}

export async function simulateLoanRepayment(
  session: PrivateCustomerSession, productId: string, principalAmount: number, termMonths: number, annualInterestRate: number,
): Promise<RepaymentSimulation> {
  return withPrivateCustomerSession(session, async (accessToken) => {
    const response = await invokeApiOperation<RepaymentSimulation>("POST /api/v1/loan-products/{productId}/repayment-simulations", {
      path: { productId }, accessToken,
      body: { principalAmount, termMonths, annualInterestRate },
    });
    return required(response.body.data, "상환 모의계산");
  });
}

function required<T>(value: T | null, label: string): T {
  if (!value) throw new Error(`${label} 응답을 확인해 주세요.`);
  return value;
}
