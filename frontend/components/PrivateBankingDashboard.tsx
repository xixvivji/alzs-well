"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { loadBankingOverview, type BankingOverview } from "../lib/private-banking";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import { getPrivateChangeIndicator } from "../lib/private-help";
import { useBankingChangeStatus } from "./BankingShell";

export function PrivateBankingDashboard() {
  const changeStatus = useBankingChangeStatus();
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [overview, setOverview] = useState<BankingOverview | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { void restorePrivateCustomerSession().then(async (active) => { setSession(active); setOverview(await loadBankingOverview(active)); }).catch((reason) => setError(message(reason))); }, []);
  if (!session && !error) return <Loading text="회원별 금융정보를 불러오고 있습니다." />;
  if (!session) return <LoginRequired message={error} />;
  if (!overview) return <Loading text="자산과 금융 일정을 정리하고 있습니다." />;
  const changeIndicator = changeStatus ? getPrivateChangeIndicator(changeStatus) : null;
  const changeStatusLoading = changeStatus === undefined;
  const changeStatusUnavailable = changeStatus === null;
  const firstTask = changeIndicator
    ? {
        label: `${changeIndicator.ariaLabel} ${changeIndicator.count}건`,
        title: "평소와 다른 금융생활 변화가 있어요.",
        description: "달라진 내용을 한곳에서 먼저 살펴보고, 알고 한 활동인지 직접 답하거나 필요한 도움을 선택할 수 있습니다.",
        primary: "변화와 다음 절차 한 번에 보기",
        secondary: "변화 상세 바로 보기",
        secondaryHref: "/banking/safety",
      }
    : changeStatusLoading
      ? {
          label: "금융생활 변화 확인 중",
          title: "최근 금융생활 변화를 확인하고 있어요.",
          description: "결과를 불러오는 동안에도 도움 방식과 이용 순서를 먼저 살펴볼 수 있습니다.",
          primary: "금융생활 도움 열기",
          secondary: "내 도움 방식 보기",
          secondaryHref: "/banking/life",
        }
      : changeStatusUnavailable
        ? {
            label: "변화 확인 연결 필요",
            title: "금융생활 도움에서 변화 상태를 다시 확인해 주세요.",
            description: "현재 메뉴에서 변화 건수를 불러오지 못했습니다. 도움 화면을 열면 회원 정보를 다시 확인합니다.",
            primary: "금융생활 도움에서 다시 확인",
            secondary: "내 도움 방식 보기",
            secondaryHref: "/banking/life",
          }
        : {
            label: "ALZ's well 특화 기능",
            title: "내 금융생활 의향과 도움 방식을 먼저 확인해 두세요.",
            description: "유지하고 싶은 납부, 편한 설명 방식, 도움이 필요한 조건을 내가 직접 정할 수 있습니다.",
            primary: "내 도움 방식 확인",
            secondary: "최근 변화 살펴보기",
            secondaryHref: "/banking/safety",
          };
  const maxTrend = Math.max(...overview.trends.map((item) => item.netAssets), 1);
  return <div className="private-banking-dashboard">
    <section className={`banking-first-task${changeIndicator ? " has-detected-change" : ""}${changeStatusLoading ? " is-checking-change" : ""}${changeStatusUnavailable ? " is-change-unavailable" : ""}`} aria-labelledby="first-task-title" aria-live="polite"><div><span className="banking-feature-label">{firstTask.label}</span><h2 id="first-task-title">{firstTask.title}</h2><p>{firstTask.description}</p><span className="banking-first-note">변화는 질병이나 사기를 뜻하지 않으며, 송금·지급정지·가족 연락은 자동으로 실행되지 않습니다.</span></div><div className="banking-first-actions"><Link className="primary-button btn btn-primary" href="/banking/help">{firstTask.primary}</Link><Link className="secondary-button btn btn-outline" href={firstTask.secondaryHref}>{firstTask.secondary}</Link></div></section>
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
    <section className="banking-shortcuts"><Link href="/banking/accounts"><span>₩</span><strong>계좌·거래 조회</strong><small>상세 내역과 정기납부</small></Link><Link href="/banking/transfer"><span>↗</span><strong>이체 사전확인</strong><small>실행 없는 한도·조건 검증</small></Link><Link href="/banking/products"><span>◇</span><strong>금융상품·자산</strong><small>예금·대출·투자·연금</small></Link><Link className={changeIndicator ? "has-change" : ""} href="/banking/help"><span>?</span><strong>금융생활 도움받기</strong><small>{changeIndicator ? `${changeIndicator.ariaLabel} ${changeIndicator.count}건과 다음 절차 확인` : changeStatusLoading ? "최근 변화 확인 중" : changeStatusUnavailable ? "도움 화면에서 다시 확인" : "내 합성데이터로 도움 확인"}</small></Link></section>
  </div>;
}

export function LoginRequired({ message }: { message?: string }) { return <section className="bank-panel login-required"><span aria-hidden="true">◎</span><h2>금융서비스 로그인이 필요합니다.</h2><p>{message || "제공받은 합성 회원 계정으로 로그인해 주세요."}</p><Link className="primary-button btn btn-primary" href="/login">로그인하기</Link></section>; }
function Loading({ text }: { text: string }) { return <section className="bank-panel banking-loading" aria-live="polite" aria-busy="true"><span className="bank-spinner loading loading-spinner loading-lg" aria-hidden="true" /><p>{text}</p></section>; }
function won(value: number) { return `${Number(value ?? 0).toLocaleString("ko-KR")}원`; }
function category(value: string) { return ({ INCOME: "수입", HOUSING: "주거", UTILITIES: "공과금", COMMUNICATION: "통신", FOOD: "식비", TRANSPORT: "교통", HEALTH: "건강", FINANCE: "금융", SHOPPING: "쇼핑", OTHER: "기타" } as Record<string, string>)[value] ?? value; }
function message(reason: unknown) { return reason instanceof Error ? reason.message : "금융정보를 불러오지 못했습니다."; }
