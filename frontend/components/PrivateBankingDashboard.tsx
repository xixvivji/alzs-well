"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { loadBankingOverview, type BankingOverview } from "../lib/private-banking";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";

export function PrivateBankingDashboard() {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [overview, setOverview] = useState<BankingOverview | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { void restorePrivateCustomerSession().then(async (active) => { setSession(active); setOverview(await loadBankingOverview(active)); }).catch((reason) => setError(message(reason))); }, []);
  if (!session && !error) return <Loading text="회원별 금융정보를 불러오고 있습니다." />;
  if (!session) return <LoginRequired message={error} />;
  if (!overview) return <Loading text="자산과 금융 일정을 정리하고 있습니다." />;
  const maxTrend = Math.max(...overview.trends.map((item) => item.netAssets), 1);
  return <div className="private-banking-dashboard">
    <section className="banking-welcome"><div><p>{session.displayName}님, 안녕하세요.</p><h2>오늘도 편안한 금융생활 되세요.</h2><span>{overview.summary.dataAsOf} 기준 합성 금융정보입니다.</span></div><div><small>총 자산</small><strong>{won(overview.summary.totalAssets)}</strong><span>순자산 {won(overview.summary.netAssets)}</span></div></section>
    <section className="banking-summary-grid">
      <article><span>이번 기간 들어온 돈</span><strong className="positive">+{won(overview.summary.periodInflow)}</strong><small>{overview.summary.accountCount}개 계좌 연결</small></article>
      <article><span>이번 기간 나간 돈</span><strong>-{won(overview.summary.periodOutflow)}</strong><small>순현금흐름 {won(overview.summary.netCashflow)}</small></article>
      <article><span>대출·카드 부채</span><strong>{won(overview.summary.totalLiabilities)}</strong><small>{overview.summary.liabilityCount}건</small></article>
      <article><span>데이터 연결 상태</span><strong>{overview.freshness.filter((item) => item.complete).length}/{overview.freshness.length}</strong><small>{overview.freshness.every((item) => item.complete) ? "모두 정상" : "확인 필요"}</small></article>
    </section>
    <div className="banking-content-grid">
      <section className="bank-panel asset-chart"><header><div><p>자산 흐름</p><h3>내 순자산 변화</h3></div><span>합성 추세</span></header><div className="trend-chart">{overview.trends.map((item) => <div key={item.date}><i style={{ height: `${Math.max(12, item.netAssets / maxTrend * 100)}%` }} /><small>{item.date.slice(5)}</small></div>)}</div><div className="asset-breakdown">{overview.breakdown.map((item) => <article key={`${item.institutionName}-${item.assetClass}`}><span><i style={{ width: `${item.percentage}%` }} /></span><p><strong>{item.institutionName}</strong><small>{item.assetClass} · {item.percentage}%</small></p><b>{won(item.amount)}</b></article>)}</div></section>
      <section className="bank-panel account-mini-list"><header><div><p>보유계좌</p><h3>내 계좌</h3></div><Link href="/banking/accounts">전체보기 →</Link></header>{overview.accounts.slice(0, 4).map((account) => <article key={account.accountId}><span>{account.institutionName.slice(0, 1)}</span><p><strong>{account.displayName}</strong><small>{account.maskedAccountNumber} · {account.accountStatus}</small></p><b>{won(account.currentBalance)}</b></article>)}</section>
    </div>
    <div className="banking-content-grid lower">
      <section className="bank-panel"><header><div><p>이번 달 분석</p><h3>지출 구성</h3></div><span>{won(overview.expenses.totalExpense)}</span></header><div className="expense-list">{overview.expenses.items.slice(0, 6).map((item) => <article key={`${item.category}-${item.institutionName}`}><span>{category(item.category)}</span><i><b style={{ width: `${item.percentage}%` }} /></i><strong>{won(item.amount)}</strong></article>)}</div></section>
      <section className="bank-panel"><header><div><p>다가오는 일정</p><h3>금융 캘린더</h3></div><span>{overview.calendar.length}건</span></header><div className="calendar-list">{overview.calendar.slice(0, 6).map((item) => <article key={item.eventId}><time>{item.scheduledDate.slice(5)}</time><p><strong>{item.title}</strong><small>{item.eventType} · {item.certainty}</small></p><b>{item.direction === "INFLOW" ? "+" : "−"}{won(item.expectedAmount)}</b></article>)}</div></section>
    </div>
    <section className="banking-shortcuts"><Link href="/banking/accounts"><span>₩</span><strong>계좌·거래 조회</strong><small>상세 내역과 정기납부</small></Link><Link href="/banking/transfer"><span>↗</span><strong>이체 사전확인</strong><small>실행 없는 한도·조건 검증</small></Link><Link href="/banking/products"><span>◇</span><strong>금융상품·자산</strong><small>예금·대출·투자·연금</small></Link><Link href="/banking/help"><span>?</span><strong>금융생활 도움받기</strong><small>내 합성데이터로 도움 확인</small></Link></section>
  </div>;
}

export function LoginRequired({ message }: { message?: string }) { return <section className="bank-panel login-required"><span>◎</span><h2>금융서비스 로그인이 필요합니다.</h2><p>{message || "제공받은 합성 회원 계정으로 로그인해 주세요."}</p><Link className="primary-button" href="/login">로그인하기</Link></section>; }
function Loading({ text }: { text: string }) { return <section className="bank-panel banking-loading"><div className="bank-spinner" /><p>{text}</p></section>; }
function won(value: number) { return `${Number(value ?? 0).toLocaleString("ko-KR")}원`; }
function category(value: string) { return ({ INCOME: "수입", HOUSING: "주거", UTILITIES: "공과금", COMMUNICATION: "통신", FOOD: "식비", TRANSPORT: "교통", HEALTH: "건강", FINANCE: "금융", SHOPPING: "쇼핑", OTHER: "기타" } as Record<string, string>)[value] ?? value; }
function message(reason: unknown) { return reason instanceof Error ? reason.message : "금융정보를 불러오지 못했습니다."; }
