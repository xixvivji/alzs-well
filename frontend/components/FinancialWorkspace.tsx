"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { type ApiResponse } from "../lib/api";
import { invokeApiOperation } from "../lib/api-operation-client";
import { readDemoContext, type DemoContext } from "../lib/demo-session";

type Money = { amount: string; currency: string };
type FinancialSummary = {
  asOf: string;
  assets: { total: Money; bankDeposits: Money; investments: Money; liabilities: Money };
  cashFlow: { monthlyIncome: Money; monthlyExpense: Money; upcomingObligations: Money };
  changeSummary: { openAlertCount: number; summary: string };
  twelveMonthTrend: Array<{ month: string; totalAssets: Money }>;
};
type Account = {
  accountId: string; institutionId: string; accountType: string; displayName: string;
  maskedAccountNumber: string; currentBalance: Money; availableBalance: Money;
  dataFreshness: string;
};
type Accounts = { items: Account[] };
type Transaction = {
  transactionId: string; occurredAt: string; direction: string; transactionType: string;
  amount: string; currency: string; balanceAfter: string; counterpartyDisplayName: string;
  category: string; status: string;
};
type Transactions = { items: Transaction[] };
type Baseline = {
  baselineId: string; featureCode: string; baselineValue: string; currentValue: string;
  unit: string; readiness: string; comparisonText: string; reasonCodes: string[];
};
type Baselines = { items: Baseline[]; baselinePeriod: { from: string; to: string }; observationPeriod: { from: string; to: string } };
type ProtectionAction = { actionCode: string; title: string; status: string; executionType: string; eligibilitySummary: string };
type ProtectionActions = { items: ProtectionAction[] };
type Consent = {
  items: Array<{ connectionId: string; institutionName: string; institutionType: string; status: string; dataFreshness: string }>;
  consentSummary: { purpose: string; granted: boolean; expiresAt: string; revocable: boolean; trustedContactGranted: boolean };
};
type WorkspaceData = {
  summary: FinancialSummary;
  accounts: Account[];
  transactions: Transaction[];
  baselines: Baseline[];
  actions: ProtectionAction[];
  consent: Consent;
};

const FEATURE_LABELS: Record<string, string> = {
  DUPLICATE_TRANSFER: "중복 송금", MISSED_RECURRING_PAYMENT: "정기납부 누락",
  REPEATED_RESULT_CHECK: "거래결과 재확인", NEW_PAYEE: "새 수취인",
  UNUSUAL_TIME: "평소와 다른 시간대", UNUSUAL_AMOUNT: "평소와 다른 금액대",
};

export function FinancialWorkspace() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [data, setData] = useState<WorkspaceData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const active = readDemoContext();
    setContext(active);
    if (!active) { setLoading(false); return; }
    loadWorkspace(active).then(setData).catch((reason) => setError(messageOf(reason))).finally(() => setLoading(false));
  }, []);

  if (loading) return <section className="panel finance-loading"><div className="bank-spinner" /><p>통합 금융생활을 안전하게 불러오는 중입니다.</p></section>;
  if (!context) return <section className="panel finance-empty"><span className="finance-empty-icon" aria-hidden="true">₩</span><p className="label">통합 금융생활</p><h2>먼저 합성데이터 체험을 시작해 주세요.</h2><p>실제 계좌나 개인정보 없이 안전한 금융 포털 화면을 확인할 수 있습니다.</p><Link className="primary-button" href="/demo">체험 시작하기</Link></section>;
  if (error || !data) return <section className="panel finance-empty"><p className="label">연결 상태 확인</p><h2>금융생활 정보를 불러오지 못했습니다.</h2><p>{error || "잠시 후 다시 시도해 주세요."}</p><Link className="primary-button" href="/demo">데모 상태 확인</Link></section>;

  const featured = data.accounts[0];
  const consent = data.consent.consentSummary;
  return <div className="finance-workspace">
    <section className="finance-hero-card">
      <div>
        <p><span>MY 자산</span> {data.summary.asOf} 기준</p>
        <h2>{money(data.summary.assets.total)}</h2>
        <div className="finance-hero-breakdown">
          <span>예금 <b>{money(data.summary.assets.bankDeposits)}</b></span>
          <span>투자 <b>{money(data.summary.assets.investments)}</b></span>
          <span>부채 <b>{money(data.summary.assets.liabilities)}</b></span>
        </div>
      </div>
      <div className="finance-health">
        <span className="finance-health-ring">{data.summary.changeSummary.openAlertCount}</span>
        <p><strong>확인할 변화</strong><small>{data.summary.changeSummary.summary}</small></p>
        <Link href="/demo/alerts">확인하기 →</Link>
      </div>
    </section>

    <section className="finance-quick-grid" aria-label="월간 금융 요약">
      <article><span className="quick-icon income" aria-hidden="true">↓</span><p>이번 달 들어온 돈<strong>{money(data.summary.cashFlow.monthlyIncome)}</strong></p></article>
      <article><span className="quick-icon expense" aria-hidden="true">↑</span><p>이번 달 나간 돈<strong>{money(data.summary.cashFlow.monthlyExpense)}</strong></p></article>
      <article><span className="quick-icon scheduled" aria-hidden="true">◇</span><p>예정된 필수 납부<strong>{money(data.summary.cashFlow.upcomingObligations)}</strong></p></article>
    </section>

    <div className="finance-two-column">
      <section className="panel account-overview">
        <div className="section-heading"><div><p className="label">연결 계좌</p><h2>내 계좌 한눈에</h2></div><span className="status-chip">{data.accounts.length}개</span></div>
        <div className="account-stack">{data.accounts.map((account, index) => <article className={index === 0 ? "featured" : ""} key={account.accountId}>
          <div><span>{institution(account.institutionId)}</span><small>{account.accountType} · {account.maskedAccountNumber}</small></div>
          <p><strong>{money(account.currentBalance)}</strong><small>출금 가능 {money(account.availableBalance)}</small></p>
        </article>)}</div>
        <p className="data-footnote"><i /> 합성데이터 · {featured?.dataFreshness ?? "데모 시점"} · 실제 조회 아님</p>
      </section>

      <section className="panel consent-card">
        <div className="section-heading"><div><p className="label">정보 연결</p><h2>동의와 연결 상태</h2></div><span className={`status-pill ${consent.granted ? "safe" : "warning"}`}>{consent.granted ? "동의 유지 중" : "확인 필요"}</span></div>
        <div className="connected-institutions">{data.consent.items.map((item) => <div key={item.connectionId}><span>{item.institutionName.slice(0, 1)}</span><p><strong>{item.institutionName}</strong><small>{item.institutionType} · {statusText(item.status)}</small></p><i /></div>)}</div>
        <dl className="consent-summary"><div><dt>이용 목적</dt><dd>{consent.purpose}</dd></div><div><dt>철회 가능</dt><dd>{consent.revocable ? "언제든 가능" : "별도 확인 필요"}</dd></div><div><dt>신뢰연락인 공유</dt><dd>{consent.trustedContactGranted ? "고객 동의함" : "동의하지 않음"}</dd></div></dl>
      </section>
    </div>

    <section className="panel transaction-panel">
      <div className="section-heading"><div><p className="label">최근 거래</p><h2>{featured?.displayName ?? "대표 계좌"} 거래내역</h2></div><span className="plain-link">조회 전용</span></div>
      <div className="finance-table"><div className="finance-table-head"><span>거래일시</span><span>내용</span><span>분류</span><span>금액</span><span>잔액</span></div>{data.transactions.slice(0, 6).map((transaction) => <div className="finance-table-row" key={transaction.transactionId}>
        <time>{dateTime(transaction.occurredAt)}</time><strong>{transaction.counterpartyDisplayName || transaction.transactionType}</strong><span>{transaction.category}</span><b className={transaction.direction === "CREDIT" ? "credit" : "debit"}>{transaction.direction === "CREDIT" ? "+" : "−"}{moneyValue(transaction.amount, transaction.currency)}</b><small>{moneyValue(transaction.balanceAfter, transaction.currency)}</small>
      </div>)}</div>
    </section>

    <div className="finance-two-column lower">
      <section className="panel baseline-panel">
        <div className="section-heading"><div><p className="label">설명 가능한 변화</p><h2>평소와 비교한 최근 모습</h2></div><Link href="/demo/ai-assistant">AI 설명 보기 →</Link></div>
        <div className="baseline-list">{data.baselines.slice(0, 5).map((item) => <article key={item.baselineId}><span className="baseline-dot" /><div><strong>{FEATURE_LABELS[item.featureCode] ?? item.featureCode}</strong><p>{item.comparisonText}</p></div><span className="baseline-values"><b>{item.currentValue}{unit(item.unit)}</b><small>평소 {item.baselineValue}{unit(item.unit)}</small></span></article>)}</div>
      </section>
      <section className="panel protection-panel">
        <div className="section-heading"><div><p className="label">보호 안내</p><h2>내가 선택할 수 있는 방법</h2></div><span className="status-chip">실행 없음</span></div>
        <div className="protection-list">{data.actions.slice(0, 4).map((action) => <article key={action.actionCode}><span aria-hidden="true">✓</span><div><strong>{action.title}</strong><p>{action.eligibilitySummary}</p><small>{statusText(action.status)} · 안내 전용</small></div></article>)}</div>
        <p className="safety-banner">ALZ&apos;s well은 송금·지급정지·연락을 대신 실행하지 않습니다.</p>
      </section>
    </div>
  </div>;
}

async function loadWorkspace(context: DemoContext): Promise<WorkspaceData> {
  const auth = { capability: context.capability, demoRunId: context.demoRunId };
  const path = { sessionId: context.sessionId, customerId: context.customerId };
  const [summary, accounts, baselines, actions, consent] = await Promise.all([
    invokeApiOperation<FinancialSummary>("GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/financial-summary", { path, ...auth }),
    invokeApiOperation<Accounts>("GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/accounts", { path, ...auth }),
    invokeApiOperation<Baselines>("GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/baselines", { path, ...auth }),
    invokeApiOperation<ProtectionActions>("GET /api/v1/demo/sessions/{sessionId}/protection-actions", { path, ...auth }),
    invokeApiOperation<Consent>("GET /api/v1/demo/sessions/{sessionId}/customers/{customerId}/connections/consent-summary", { path, ...auth }),
  ]);
  const summaryData = required(summary.body.data, "금융 요약");
  const accountData = required(accounts.body.data, "계좌 목록");
  const first = accountData.items[0];
  const transactions = first ? await invokeApiOperation<Transactions>(
    "GET /api/v1/demo/sessions/{sessionId}/accounts/{accountId}/transactions",
    { path: { sessionId: context.sessionId, accountId: first.accountId }, query: { limit: 20 }, ...auth },
  ) : null;
  return {
    summary: summaryData,
    accounts: accountData.items,
    transactions: transactions?.body.data?.items ?? [],
    baselines: required(baselines.body.data, "기준선").items,
    actions: required(actions.body.data, "보호 안내").items,
    consent: required(consent.body.data, "연결 동의"),
  };
}

function required<T>(value: T | null, label: string): T {
  if (!value) throw new Error(`${label} 응답을 확인해 주세요.`);
  return value;
}
function messageOf(error: unknown) { return (error as Partial<ApiResponse<unknown>>).message ?? (error instanceof Error ? error.message : "백엔드 연결 상태를 확인해 주세요."); }
function money(value?: Money) { return value ? moneyValue(value.amount, value.currency) : "-"; }
function moneyValue(value: string, currency: string) { return `${Number(value).toLocaleString("ko-KR")}${currency === "KRW" ? "원" : ` ${currency}`}`; }
function institution(id: string) { return id.replace(/^INST_/, "금융기관 "); }
function statusText(value: string) { return ({ ACTIVE: "정상", CONNECTED: "연결됨", ELIGIBLE: "안내 가능", AVAILABLE: "안내 가능" } as Record<string, string>)[value] ?? value; }
function unit(value: string) { return ({ COUNT: "회", KRW: "원", RATIO: "%" } as Record<string, string>)[value] ?? value; }
function dateTime(value: string) { return new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
