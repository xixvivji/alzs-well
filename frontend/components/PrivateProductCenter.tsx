"use client";

import { useCallback, useEffect, useState } from "react";
import {
  loadPrivateProductOverview, loginPrivateCustomer, logoutPrivateCustomer, restorePrivateCustomerSession, simulateLoanRepayment,
  type PrivateCustomerSession, type PrivateProductOverview, type RepaymentSimulation,
} from "../lib/private-financial-products";
import { isPrivateSessionExpiredError } from "../lib/private-auth-session";
import { PrivateCustomerAssets, type AssetTab } from "./PrivateCustomerAssets";

type ProductTab = "card" | "loan" | "investment" | AssetTab;

export function PrivateProductCenter() {
  const [loginId, setLoginId] = useState("demo001");
  const [password, setPassword] = useState("");
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [overview, setOverview] = useState<PrivateProductOverview | null>(null);
  const [tab, setTab] = useState<ProductTab>("card");
  const [busy, setBusy] = useState<"login" | "load" | "simulate" | "logout" | null>(null);
  const [error, setError] = useState("");
  const [principal, setPrincipal] = useState(30_000_000);
  const [term, setTerm] = useState(60);
  const [rate, setRate] = useState(4.5);
  const [simulation, setSimulation] = useState<RepaymentSimulation | null>(null);
  const expireSession = useCallback((message: string) => {
    setSession(null); setOverview(null); setSimulation(null); setError(message); setBusy(null);
  }, []);

  useEffect(() => {
    let active = true;
    void restorePrivateCustomerSession().then(async (restored) => {
      if (!active) return;
      setSession(restored); setBusy("load");
      const loaded = await loadPrivateProductOverview(restored);
      if (active) setOverview(loaded);
    }).catch(() => undefined).finally(() => { if (active) setBusy(null); });
    return () => { active = false; };
  }, []);

  async function login() {
    setBusy("login"); setError("");
    let authenticated: PrivateCustomerSession | null = null;
    try {
      authenticated = await loginPrivateCustomer(loginId.trim(), password);
      setPassword(""); setSession(authenticated); setBusy("load");
      setOverview(await loadPrivateProductOverview(authenticated));
    } catch (reason) {
      if (authenticated) await logoutPrivateCustomer(authenticated).catch(() => undefined);
      setError(loginError(reason)); setSession(null); setOverview(null);
    }
    finally { setBusy(null); }
  }

  async function logout() {
    if (!session) return;
    setBusy("logout");
    try { await logoutPrivateCustomer(session); }
    catch { /* 만료된 세션도 브라우저 메모리에서 즉시 폐기한다. */ }
    finally { setSession(null); setOverview(null); setSimulation(null); setBusy(null); }
  }

  async function simulate() {
    const product = overview?.loanProducts[0];
    if (!session || !product) return;
    setBusy("simulate"); setError("");
    try { setSimulation(await simulateLoanRepayment(session, product.productId, principal, term, rate)); }
    catch (reason) {
      if (isPrivateSessionExpiredError(reason)) expireSession(reason.message);
      else setError(messageOf(reason));
    }
    finally { setBusy(null); }
  }

  if (!session || !overview) return <section className="panel private-login-panel">
    <div className="private-login-copy"><p className="label">합성 금융서비스 인증</p><h2>내 금융정보는 로그인 후 조회합니다.</h2><p>300명의 합성 회원은 각자 분리된 계좌·거래·설정만 조회합니다. 실제 고객정보와 금융 실행은 포함하지 않습니다.</p><ul><li>인증 token은 HttpOnly 쿠키에만 저장</li><li>회원별 소유권 검사로 다른 고객 데이터 차단</li><li>실서비스 전환 시 기업 IdP·MFA로 교체</li></ul></div>
    <form onSubmit={(event) => { event.preventDefault(); void login(); }}><label><span>합성 회원 ID</span><input autoComplete="username" value={loginId} pattern="demo[0-9]{3}" maxLength={80} onChange={(event) => setLoginId(event.target.value)} /></label><label><span>비밀번호</span><input type="password" autoComplete="current-password" value={password} minLength={12} maxLength={200} onChange={(event) => setPassword(event.target.value)} /></label><button className="primary-button" disabled={busy !== null || !/^demo[0-9]{3}$/.test(loginId.trim()) || password.length < 12}>{busy === "login" || busy === "load" ? "인증·조회 중…" : "금융서비스 로그인"}</button>{error && <p className="api-error" role="alert">{error}</p>}</form>
  </section>;

  const card = overview.cards[0]; const loan = overview.loans[0]; const investment = overview.investments[0];
  return <div className="private-product-center">
    <section className="private-session-bar"><div><span>{session.displayName.slice(0, 1)}</span><p><strong>{session.displayName}</strong><small>합성 고객 · {session.customerId}</small></p></div><div><small>접근권한 {session.permissions.length}개 · HttpOnly 보안 세션</small><button onClick={() => void logout()} disabled={busy !== null}>{busy === "logout" ? "종료 중…" : "안전하게 로그아웃"}</button></div></section>
    <nav className="product-tabs" aria-label="금융상품 화면"><button className={tab === "card" ? "active" : ""} onClick={() => setTab("card")}>카드</button><button className={tab === "deposit" ? "active" : ""} onClick={() => setTab("deposit")}>예금</button><button className={tab === "loan" ? "active" : ""} onClick={() => setTab("loan")}>대출</button><button className={tab === "investment" ? "active" : ""} onClick={() => setTab("investment")}>투자</button><button className={tab === "fx" ? "active" : ""} onClick={() => setTab("fx")}>외환</button><button className={tab === "future" ? "active" : ""} onClick={() => setTab("future")}>연금·신탁</button><button className={tab === "consent" ? "active" : ""} onClick={() => setTab("consent")}>동의관리</button></nav>
    {tab === "card" && <section className="product-screen card-screen">
      <div className="product-balance-card"><p>{card?.institutionName ?? "카드"}<span>{card?.status ?? "-"}</span></p><h2>{card?.displayName ?? "보유 카드 없음"}</h2><small>{card?.maskedCardNumber}</small><div><span>이번 달 이용금액<strong>{money(card?.currentUsageAmount, card?.currency)}</strong></span><span>결제 예정<strong>{money(overview.paymentDue?.amount, overview.paymentDue?.currency)}</strong></span></div><p className="no-action">잠금·한도변경·결제 실행 없음 · 합성 상세 {overview.cardDetail?.syntheticData ? "검증" : "미확인"}</p></div>
      <div className="product-side-cards"><article className="panel"><p className="label">이용한도</p><h3>{money(overview.cardLimit?.availableLimitAmount, overview.cardLimit?.currency)}</h3><small>총 {money(overview.cardLimit?.totalLimitAmount, overview.cardLimit?.currency)} 중 이용 가능</small><div className="limit-bar"><i style={{ width: `${limitPercent(overview.cardLimit?.usedAmount, overview.cardLimit?.totalLimitAmount)}%` }} /></div></article><article className="panel"><p className="label">다음 결제일</p><h3>{date(overview.paymentDue?.dueDate)}</h3><small>{overview.paymentDue?.paymentStatus ?? "조회 결과 없음"}</small></article></div>
      <section className="panel product-list-panel"><div className="section-heading"><div><p className="label">최근 이용내역</p><h2>카드 사용내역</h2></div><span className="status-chip">합성데이터 {overview.cardTransactions.length}건</span></div><div className="product-transactions">{overview.cardTransactions.map((item) => <article key={item.cardTransactionId}><time>{date(item.occurredAt)}</time><div><strong>{item.merchantDisplayName}</strong><small>{item.categoryCode} · {item.installmentMonths > 1 ? `${item.installmentMonths}개월` : "일시불"}</small></div><b>{money(item.amount, item.currency)}</b></article>)}</div></section>
      <section className="panel statement-panel"><div className="section-heading"><div><p className="label">결제 투명성</p><h2>이용대금 명세서</h2></div><span className="status-chip">조회 전용 {overview.statements.length}건</span></div><div className="statement-list">{overview.statements.slice(0, 4).map((item) => <article key={item.statementId}><div><strong>{date(item.periodFrom)} ~ {date(item.periodTo)}</strong><small>결제일 {date(item.dueDate)}</small></div><span>{item.status}</span><p><small>청구 {money(item.totalAmount, item.currency)}</small><b>남은 금액 {money(item.remainingDueAmount, item.currency)}</b></p></article>)}</div></section>
    </section>}
    {tab === "loan" && <section className="product-screen loan-screen">
      <div className="product-two-column"><article className="panel loan-summary"><p className="label">보유 대출</p><h2>{loan?.displayName ?? "보유 대출 없음"}</h2><small>{loan?.institutionName} · {loan?.maskedReference}</small><dl><div><dt>남은 원금</dt><dd>{money(loan?.outstandingAmount, loan?.currency)}</dd></div><div><dt>다음 납부</dt><dd>{money(loan?.scheduledAmount, loan?.currency)}</dd></div><div><dt>적용 금리</dt><dd>연 {loan?.annualInterestRate ?? "-"}%</dd></div><div><dt>다음 납부일</dt><dd>{date(loan?.nextDueDate)}</dd></div></dl></article><article className="panel repayment-preview"><p className="label">실행 없는 모의계산</p><h2>{overview.loanProducts[0]?.productName ?? "대출상품"}</h2><div className="simulation-inputs"><label><span>대출 원금</span><input type="number" min={100000} max={1000000000} step={100000} value={principal} onChange={(event) => setPrincipal(Number(event.target.value))} /></label><label><span>기간(개월)</span><input type="number" min={1} max={360} value={term} onChange={(event) => setTerm(Number(event.target.value))} /></label><label><span>연 금리(%)</span><input type="number" min={0} max={30} step={0.1} value={rate} onChange={(event) => setRate(Number(event.target.value))} /></label></div><button className="primary-button" disabled={busy !== null || !overview.loanProducts.length} onClick={() => void simulate()}>{busy === "simulate" ? "계산 중…" : "예상 상환액 계산"}</button>{simulation && <div className="simulation-result"><span>첫 달 예상액<strong>{money(simulation.firstPaymentAmount, simulation.currency)}</strong></span><span>예상 총이자<strong>{money(simulation.totalInterest, simulation.currency)}</strong></span><span>예상 총상환<strong>{money(simulation.totalRepaymentAmount, simulation.currency)}</strong></span><small>신용조회·심사·신청 없음</small></div>}</article></div>
      <section className="panel repayment-schedule"><div className="section-heading"><div><p className="label">상환 일정</p><h2>예정된 원리금</h2></div><span className="status-chip">조회 전용</span></div><div>{overview.repaymentSchedule.slice(0, 6).map((item) => <article key={item.installmentId}><span>{item.installmentNumber}회차</span><time>{date(item.dueDate)}</time><small>원금 {money(item.principalAmount, loan?.currency)}</small><small>이자 {money(item.interestAmount, loan?.currency)}</small><strong>{money(item.totalAmount, loan?.currency)}</strong></article>)}</div></section>
      <section className="panel product-contract-status"><span>보유 대출 상세</span><strong>{overview.loanDetail ? "연결됨" : "보유 내역 없음"}</strong><span>상품 조건·유의사항</span><strong>{overview.loanProductDetail ? "연결됨" : "상품 없음"}</strong><small>조회와 모의계산만 제공하며 신용조회·심사·신청은 실행하지 않습니다.</small></section>
    </section>}
    {tab === "investment" && <section className="product-screen investment-screen">
      <section className="investment-hero"><div><p>{investment?.institutionName ?? "투자계좌"}</p><h2>{money(investment?.totalMarketValue, investment?.currency)}</h2><small>{investment?.displayName} · {investment?.maskedAccountNumber}</small></div><span>주문 실행 없음<br/>지연 합성 시세</span></section>
      <div className="product-two-column"><section className="panel allocation-card"><div className="section-heading"><div><p className="label">자산배분</p><h2>포트폴리오 구성</h2></div></div><div className="allocation-chart"><div>{overview.allocations.map((item, index) => <i key={item.assetClass} style={{ width: `${item.weightPercent}%`, background: COLORS[index % COLORS.length] }} />)}</div>{overview.allocations.map((item, index) => <p key={item.assetClass}><span style={{ background: COLORS[index % COLORS.length] }} />{assetLabel(item.assetClass)}<strong>{item.weightPercent}%</strong></p>)}</div></section><section className="panel position-card"><div className="section-heading"><div><p className="label">보유 종목</p><h2>평가 현황</h2></div><span className="status-chip">{overview.positions.length}종목</span></div><div>{overview.positions.map((item) => <article key={item.positionId}><div><strong>{item.instrumentName}</strong><small>{item.maskedInstrumentCode} · {item.quantity}주</small></div><p><b>{money(item.marketValue, item.currency)}</b><span className={item.unrealizedProfitLoss >= 0 ? "gain" : "loss"}>{item.unrealizedProfitLoss >= 0 ? "+" : ""}{money(item.unrealizedProfitLoss, item.currency)}</span></p></article>)}</div></section></div>
      <section className="panel order-history"><div className="section-heading"><div><p className="label">과거 기록</p><h2>주문·체결 이력</h2></div><span className="status-chip">신규 주문 불가</span></div><div>{overview.orders.map((item) => <article key={item.orderId}><time>{date(item.orderedAt)}</time><div><strong>{item.instrumentName}</strong><small>{item.side} · {item.quantity}주</small></div><b>{money(item.orderPrice, item.currency)}</b><span>{item.status}</span></article>)}</div></section>
      <section className="panel watchlist-board"><div className="section-heading"><div><p className="label">관심종목</p><h2>지연 합성 시세</h2></div><span className="status-chip">{overview.watchlist?.total ?? 0}종목</span></div><div>{overview.watchlist?.items.map((item) => <article key={item.instrumentId}><div><strong>{item.instrumentName}</strong><small>{item.maskedInstrumentCode}</small></div><b>{money(item.currentPrice, item.currency)}</b><span className={item.changeRate >= 0 ? "gain" : "loss"}>{item.changeRate >= 0 ? "+" : ""}{item.changeRate}%</span></article>)}</div><p className="data-footnote"><i /> 첫 관심종목의 시세 {overview.selectedQuote ? "연결" : "대상 없음"} · 차트 {overview.selectedChart ? "연결" : "대상 없음"} · 외부 시세 호출 없음</p></section>
    </section>}
    <PrivateCustomerAssets session={session} activeTab={isAssetTab(tab) ? tab : null} onSessionExpired={expireSession} />
    {error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

const COLORS = ["#255f4b", "#6ea78e", "#d1a34a", "#7585aa"];
function money(value?: number, currency = "KRW") { return value === undefined ? "-" : `${Number(value).toLocaleString("ko-KR")}${currency === "KRW" ? "원" : ` ${currency}`}`; }
function date(value?: string) { if (!value) return "-"; const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "short", day: "numeric" }).format(parsed); }
function limitPercent(used = 0, total = 0) { return total > 0 ? Math.min(100, Math.max(0, used / total * 100)) : 0; }
function assetLabel(value: string) { return ({ EQUITY: "주식", BOND: "채권", CASH: "현금", FUND: "펀드" } as Record<string, string>)[value] ?? value; }
function isAssetTab(value: ProductTab): value is AssetTab { return ["deposit", "fx", "future", "consent"].includes(value); }
function messageOf(reason: unknown) { return reason instanceof Error ? reason.message : "금융상품 정보를 처리하지 못했습니다."; }
function loginError(reason: unknown) { const message = messageOf(reason); return /404|찾을 수|등록되지/.test(message) ? "공개 production에서는 로컬 합성 로그인이 비활성화됩니다. 사설 staging의 기업 IdP 연결 상태를 확인해 주세요." : message; }
