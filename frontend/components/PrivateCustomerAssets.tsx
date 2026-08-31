"use client";

import { useCallback, useEffect, useState } from "react";
import {
  evaluateDisclosure, grantConsent, loadPrivateCustomerAssets, simulateDepositInterest, simulateFxExchange, withdrawConsent,
  type DisclosureEvaluation, type FxSimulation, type InterestSimulation, type PrivateCustomerAssets as Assets,
} from "../lib/private-customer-assets";
import { isPrivateSessionExpiredError } from "../lib/private-auth-session";
import type { PrivateCustomerSession } from "../lib/private-financial-products";

export type AssetTab = "deposit" | "fx" | "future" | "consent";
type Props = { session: PrivateCustomerSession; activeTab: AssetTab | null; onSessionExpired: (message: string) => void };
const CONSENT_SCOPES = [
  ["ACCOUNT_SUMMARY", "계좌 요약"], ["TRANSACTION_SUMMARY", "거래 요약"], ["BASELINE_SIGNAL", "변화 기준선"],
  ["PROTECTION_CASE", "보호업무 사건"], ["CONTACT_MINIMUM", "최소 연락정보"],
] as const;

export function PrivateCustomerAssets({ session, activeTab, onSessionExpired }: Props) {
  const [assets, setAssets] = useState<Assets | null>(null);
  const [busy, setBusy] = useState<"load" | "deposit" | "fx" | "grant" | "withdraw" | "evaluate" | null>("load");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [depositAmount, setDepositAmount] = useState(10_000_000);
  const [depositTerm, setDepositTerm] = useState(12);
  const [interest, setInterest] = useState<InterestSimulation | null>(null);
  const [fromCurrency, setFromCurrency] = useState("KRW");
  const [toCurrency, setToCurrency] = useState("USD");
  const [fxAmount, setFxAmount] = useState(1_000_000);
  const [fxResult, setFxResult] = useState<FxSimulation | null>(null);
  const [purpose, setPurpose] = useState("FINANCIAL_ANALYSIS");
  const [scopes, setScopes] = useState<string[]>(["ACCOUNT_SUMMARY", "TRANSACTION_SUMMARY"]);
  const [withdrawReason, setWithdrawReason] = useState("고객이 정보 제공 범위를 다시 검토하기 위해 철회합니다.");
  const [evaluation, setEvaluation] = useState<DisclosureEvaluation | null>(null);

  const handleError = useCallback((reason: unknown) => {
    const message = messageOf(reason);
    if (isPrivateSessionExpiredError(reason)) {
      setAssets(null); setInterest(null); setFxResult(null);
      onSessionExpired(message); return;
    }
    setError(message);
  }, [onSessionExpired]);

  const refresh = useCallback(async () => {
    setBusy("load"); setError("");
    try { setAssets(await loadPrivateCustomerAssets(session)); }
    catch (reason) { handleError(reason); }
    finally { setBusy(null); }
  }, [handleError, session]);

  useEffect(() => { void refresh(); }, [refresh]);

  async function simulateDeposit() {
    const product = assets?.depositProducts[0]; if (!product) return;
    setBusy("deposit"); setError("");
    try { setInterest(await simulateDepositInterest(session, product.productId, depositAmount, depositTerm)); }
    catch (reason) { handleError(reason); } finally { setBusy(null); }
  }

  async function simulateFx() {
    setBusy("fx"); setError("");
    try { setFxResult(await simulateFxExchange(session, fromCurrency, toCurrency, fxAmount)); }
    catch (reason) { handleError(reason); } finally { setBusy(null); }
  }

  async function grant() {
    if (!scopes.length) return;
    setBusy("grant"); setError(""); setMessage("");
    try {
      const expiry = new Date(); expiry.setFullYear(expiry.getFullYear() + 1);
      await grantConsent(session, purpose, scopes, expiry.toISOString());
      setMessage("목적과 범위를 확인한 동의를 등록했습니다."); await refresh();
    } catch (reason) { handleError(reason); } finally { setBusy(null); }
  }

  async function withdraw() {
    const consent = assets?.consents.find((item) => item.revocable); if (!consent || !withdrawReason.trim()) return;
    setBusy("withdraw"); setError(""); setMessage("");
    try { await withdrawConsent(session, consent, withdrawReason.trim()); setMessage("동의를 철회했습니다. 변경 이력은 감사기록에 남습니다."); await refresh(); }
    catch (reason) { handleError(reason); } finally { setBusy(null); }
  }

  async function evaluate() {
    const consent = assets?.consents[0]; if (!consent) return;
    setBusy("evaluate"); setError("");
    try { setEvaluation(await evaluateDisclosure(session, consent)); }
    catch (reason) { handleError(reason); } finally { setBusy(null); }
  }

  if (!activeTab) return null;
  if (!assets) return <section className="panel asset-loading"><div className={busy === "load" ? "bank-spinner" : "status-outage"}>{busy === "load" ? "" : "!"}</div><h2>{busy === "load" ? "남은 금융자산을 연결하고 있습니다." : "금융자산을 불러오지 못했습니다."}</h2><p>{error || "합성 금융정보를 안전하게 조회합니다."}</p>{busy !== "load" && <button className="primary-button" onClick={() => void refresh()}>다시 조회</button>}</section>;

  return <div className="customer-assets-workspace">
    {activeTab === "deposit" && <DepositScreen assets={assets} busy={busy} amount={depositAmount} term={depositTerm} interest={interest} setAmount={setDepositAmount} setTerm={setDepositTerm} simulate={() => void simulateDeposit()} />}
    {activeTab === "fx" && <FxScreen assets={assets} busy={busy} from={fromCurrency} to={toCurrency} amount={fxAmount} result={fxResult} setFrom={setFromCurrency} setTo={setToCurrency} setAmount={setFxAmount} simulate={() => void simulateFx()} />}
    {activeTab === "future" && <FutureScreen assets={assets} />}
    {activeTab === "consent" && <ConsentScreen assets={assets} busy={busy} purpose={purpose} scopes={scopes} reason={withdrawReason} evaluation={evaluation} setPurpose={setPurpose} setReason={setWithdrawReason} toggleScope={(scope) => setScopes((current) => current.includes(scope) ? current.filter((item) => item !== scope) : [...current, scope])} grant={() => void grant()} withdraw={() => void withdraw()} evaluate={() => void evaluate()} />}
    {message && <p className="workflow-result" role="status">{message}</p>}{error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

function DepositScreen({ assets, busy, amount, term, interest, setAmount, setTerm, simulate }: { assets: Assets; busy: string | null; amount: number; term: number; interest: InterestSimulation | null; setAmount: (value: number) => void; setTerm: (value: number) => void; simulate: () => void }) {
  const holding = assets.deposits[0]; const product = assets.depositProducts[0];
  return <section className="asset-screen">
    <div className="asset-hero deposit-asset-hero"><div><p>예금·적금 자산</p><h2>{money(total(assets.deposits, "currentBalance"), "KRW")}</h2><small>합성 보유상품 {assets.deposits.length}개 · 외부 금융사 조회 없음</small></div><span>예상 만기금액<strong>{money(assets.depositDetail?.expectedMaturityAmount, holding?.currency)}</strong></span></div>
    <div className="product-two-column"><article className="panel asset-detail-card"><p className="label">대표 보유상품</p><h2>{holding?.displayName ?? "보유 예금 없음"}</h2><small>{holding?.institutionName} · {holding?.maskedAccountNumber}</small><dl><div><dt>현재 잔액</dt><dd>{money(holding?.currentBalance, holding?.currency)}</dd></div><div><dt>적용 금리</dt><dd>연 {holding?.annualInterestRate ?? "-"}%</dd></div><div><dt>만기일</dt><dd>{date(holding?.maturityDate)}</dd></div><div><dt>누적 이자</dt><dd>{money(holding?.accruedInterest, holding?.currency)}</dd></div></dl></article><article className="panel simulation-card"><p className="label">실행 없는 이자 계산</p><h2>{product?.productName ?? "예금상품"}</h2><p className="muted">{assets.depositProductDetail?.cautionText ?? product?.summary}</p><div className="simulation-inputs"><label><span>예치금</span><input type="number" min={10000} max={1000000000} step={10000} value={amount} onChange={(event) => setAmount(Number(event.target.value))} /></label><label><span>기간(개월)</span><input type="number" min={1} max={120} value={term} onChange={(event) => setTerm(Number(event.target.value))} /></label></div><button className="primary-button" disabled={!product || busy !== null} onClick={simulate}>{busy === "deposit" ? "계산 중…" : "예상 이자 계산"}</button>{interest && <div className="simulation-result"><span>세전 이자<strong>{money(interest.grossInterest, interest.currency)}</strong></span><span>예상 세금<strong>{money(interest.estimatedTax, interest.currency)}</strong></span><span>만기 예상액<strong>{money(interest.estimatedMaturityAmount, interest.currency)}</strong></span><small>가입·해지·외부 호출 없음</small></div>}</article></div>
    <div className="product-two-column"><section className="panel compact-list"><div className="section-heading"><div><p className="label">승인된 합성 상품</p><h2>금리 구간</h2></div><span className="status-chip">{assets.depositRates.length}개</span></div>{assets.depositRates.map((rate) => <article key={rate.rateId}><div><strong>{rate.tierCode}</strong><small>{rate.minTermMonths}~{rate.maxTermMonths}개월</small></div><b>연 {rate.annualInterestRate}%</b></article>)}</section><section className="panel compact-list"><div className="section-heading"><div><p className="label">안내만 제공</p><h2>만기 선택지</h2></div><span className="status-chip">선택 실행 불가</span></div>{assets.maturityOptions.map((option) => <article key={option.optionId}><div><strong>{option.title}</strong><small>{option.description}</small></div><span>{option.optionCode}</span></article>)}</section></div>
  </section>;
}

function FxScreen({ assets, busy, from, to, amount, result, setFrom, setTo, setAmount, simulate }: { assets: Assets; busy: string | null; from: string; to: string; amount: number; result: FxSimulation | null; setFrom: (value: string) => void; setTo: (value: string) => void; setAmount: (value: number) => void; simulate: () => void }) {
  return <section className="asset-screen">
    <section className="panel fx-board"><div className="section-heading"><div><p className="label">고정 지연 합성 시세</p><h2>오늘의 환율</h2></div><span className="status-chip">{assets.selectedFxRate?.currency ?? "통화"} 상세 연결 · 실시간 아님</span></div><div className="fx-rate-grid">{assets.fxRates.map((rate) => <article key={rate.rateId}><span>{rate.currency}</span><div><strong>{rate.currencyName}</strong><small>{rate.unitAmount} 단위</small></div><p><b>{Number(rate.baseRate).toLocaleString("ko-KR")}</b><small>송금 보낼 때 {Number(rate.remittanceSendRate).toLocaleString("ko-KR")}</small></p></article>)}</div></section>
    <div className="product-two-column"><section className="panel"><p className="label">외화계좌</p><h2>통화별 잔액</h2><div className="currency-account-list">{assets.fxAccounts.map((account) => <article key={account.accountId}><span>{account.currency}</span><div><strong>{account.accountName}</strong><small>{account.institutionName} · {account.maskedAccountNumber}</small></div><b>{money(account.availableBalance, account.currency)}</b></article>)}</div></section><section className="panel simulation-card"><p className="label">실행 없는 환전 계산</p><h2>예상 수령금액</h2><div className="fx-simulation-inputs"><select value={from} onChange={(event) => setFrom(event.target.value)}>{["KRW", "USD", "JPY", "EUR"].map((value) => <option key={value}>{value}</option>)}</select><span>→</span><select value={to} onChange={(event) => setTo(event.target.value)}>{["USD", "JPY", "EUR", "KRW"].map((value) => <option key={value}>{value}</option>)}</select><input type="number" min={1} max={100000000} value={amount} onChange={(event) => setAmount(Number(event.target.value))} /></div><button className="primary-button" disabled={from === to || busy !== null} onClick={simulate}>{busy === "fx" ? "계산 중…" : "환전 예상액 계산"}</button>{result && <div className="fx-result"><small>적용 환율 {result.appliedRate}</small><strong>{money(result.convertedAmount, result.toCurrency)}</strong><span>환전 생성 없음 · 외부 실행 없음</span></div>}</section></div>
    <section className="panel remittance-history"><div className="section-heading"><div><p className="label">과거 합성 기록</p><h2>해외송금 이력</h2></div><span className="status-chip">신규 송금 불가</span></div>{assets.remittances.map((item) => <article key={item.remittanceId}><time>{date(item.requestedAt)}</time><div><strong>{item.beneficiaryAlias}</strong><small>{item.destinationCountryCode} · {item.status}</small></div><b>{money(item.foreignAmount, item.currency)}</b><small>수수료 {money(item.feeAmount, "KRW")}</small></article>)}</section>
  </section>;
}

function FutureScreen({ assets }: { assets: Assets }) {
  return <section className="asset-screen"><div className="future-hero"><div><p>노후·보호 자산</p><h2>{money(total(assets.pensions, "currentValue") + total(assets.trusts, "currentValue"), "KRW")}</h2><small>연금 전망은 보장값이나 투자 추천이 아닙니다.</small></div><span>외부 계약 변경 없음</span></div><div className="product-two-column"><section className="panel pension-card"><div className="section-heading"><div><p className="label">연금</p><h2>{assets.pensions[0]?.displayName ?? "연금 없음"}</h2></div><span className="status-chip">전망 {assets.pensionProjection?.scenarios.length ?? 0}개</span></div><p className="large-asset-value">{money(assets.pensions[0]?.currentValue, assets.pensions[0]?.currency)}</p><div className="scenario-bars">{assets.pensionProjection?.scenarios.map((scenario) => <article key={scenario.projectionId}><div><strong>{scenarioLabel(scenario.scenarioCode)}</strong><small>가정 수익률 {scenario.assumedAnnualReturn}%</small></div><span>월 {money(scenario.projectedMonthlyBenefit, "KRW")}</span><i style={{ width: `${Math.min(100, Math.max(15, scenario.projectedValue / Math.max(...assets.pensionProjection!.scenarios.map((item) => item.projectedValue)) * 100))}%` }} /></article>)}</div><p className="safety-banner">{assets.pensionProjection?.disclaimer}</p></section><section className="panel trust-card"><div className="section-heading"><div><p className="label">신탁</p><h2>{assets.trusts[0]?.displayName ?? "신탁 없음"}</h2></div><span className="status-chip">수익자 비공개</span></div><p className="large-asset-value">{money(assets.trusts[0]?.currentValue, assets.trusts[0]?.currency)}</p><dl><div><dt>계약 목적</dt><dd>{assets.trusts[0]?.purposeCode ?? "-"}</dd></div><div><dt>다음 검토일</dt><dd>{date(assets.trusts[0]?.nextReviewDate)}</dd></div><div><dt>수익자 수</dt><dd>{assets.trusts[0]?.beneficiaryCount ?? 0}명</dd></div><div><dt>계약 실행</dt><dd>{assets.trustDetail?.contractActionAvailable ? "가능" : "조회만"}</dd></div></dl></section></div></section>;
}

function ConsentScreen({ assets, busy, purpose, scopes, reason, evaluation, setPurpose, setReason, toggleScope, grant, withdraw, evaluate }: { assets: Assets; busy: string | null; purpose: string; scopes: string[]; reason: string; evaluation: DisclosureEvaluation | null; setPurpose: (value: string) => void; setReason: (value: string) => void; toggleScope: (value: string) => void; grant: () => void; withdraw: () => void; evaluate: () => void }) {
  const active = assets.consentDetail ?? assets.consents[0];
  const purposeExists = assets.consents.some((consent) => consent.purposeCode === purpose);
  return <section className="asset-screen consent-screen"><section className="consent-hero"><div><p>내 정보 사용 통제</p><h2>{assets.consents.length}개의 활성 동의</h2><small>목적·범위·기간을 각각 확인하고 언제든 철회할 수 있습니다.</small></div><span>외부 제공 자동 실행 없음</span></section><div className="product-two-column"><section className="panel consent-list"><div className="section-heading"><div><p className="label">현재 동의</p><h2>목적별 정보 사용</h2></div><button className="secondary-button" disabled={!active || busy !== null} onClick={evaluate}>{busy === "evaluate" ? "평가 중…" : "최소정보 평가"}</button></div>{assets.consents.map((consent) => <article key={consent.consentId}><div><strong>{purposeLabel(consent.purposeCode)}</strong><small>{date(consent.grantedAt)} ~ {date(consent.expiresAt)}</small></div><span className="status-pill safe">{consent.status}</span><p>{consent.scopes.map(scopeLabel).join(" · ")}</p></article>)}{evaluation && <div className={`disclosure-result ${evaluation.disclosureAllowed ? "allowed" : "blocked"}`}><strong>{evaluation.disclosureAllowed ? "현재 범위에서 제공 가능" : "추가 동의 필요"}</strong><span>정책 {evaluation.policyVersion} · 외부 제공 생성 없음</span>{evaluation.missingScopes.length > 0 && <small>부족 범위: {evaluation.missingScopes.map(scopeLabel).join(", ")}</small>}</div>}</section><section className="panel consent-grant"><p className="label">새 동의 등록</p><h2>필요한 범위만 선택</h2><label><span>사용 목적</span><select value={purpose} onChange={(event) => setPurpose(event.target.value)}><option value="FINANCIAL_ANALYSIS">금융생활 분석</option><option value="PROTECTION_GUIDANCE">보호수단 안내</option><option value="TRUSTED_CONTACT_DISCLOSURE">신뢰연락인 최소정보</option></select></label><fieldset><legend>제공 범위</legend>{CONSENT_SCOPES.map(([value, label]) => <label key={value}><input type="checkbox" checked={scopes.includes(value)} onChange={() => toggleScope(value)} />{label}</label>)}</fieldset>{purposeExists && <p className="consent-warning">같은 목적의 활성 동의가 있어 중복 등록할 수 없습니다.</p>}<button className="primary-button" disabled={!scopes.length || purposeExists || busy !== null} onClick={grant}>{busy === "grant" ? "등록 중…" : "1년 동의 등록"}</button></section></div><div className="product-two-column"><section className="panel consent-history"><p className="label">불변 변경 이력</p><h2>동의 감사기록</h2><ol>{assets.consentHistory.map((event) => <li key={event.eventId}><span /><div><strong>{event.eventType.replaceAll("_", " ")}</strong><small>{dateTime(event.occurredAt)} · v{event.version} · {event.actorId}</small></div></li>)}</ol></section><section className="panel consent-withdraw"><p className="label">고객 통제권</p><h2>활성 동의 철회</h2><p>철회 후 새로운 정보 제공은 허용되지 않습니다. 기존 감사기록은 삭제되지 않습니다.</p><label><span>철회 사유</span><textarea maxLength={300} value={reason} onChange={(event) => setReason(event.target.value)} /></label><button disabled={!active?.revocable || !reason.trim() || busy !== null} onClick={withdraw}>{busy === "withdraw" ? "철회 중…" : "현재 동의 철회"}</button></section></div></section>;
}

function money(value?: number, currency = "KRW") { return value === undefined ? "-" : `${Number(value).toLocaleString("ko-KR")}${currency === "KRW" ? "원" : ` ${currency}`}`; }
function date(value?: string | null) { if (!value) return "-"; const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "short", day: "numeric" }).format(parsed); }
function dateTime(value: string) { const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(parsed); }
function total<T extends object>(items: T[], key: keyof T) { return items.reduce((sum, item) => sum + Number(item[key] ?? 0), 0); }
function scenarioLabel(value: string) { return ({ CONSERVATIVE: "보수적", BASE: "기준", OPTIMISTIC: "낙관적" } as Record<string, string>)[value] ?? value; }
function purposeLabel(value: string) { return ({ FINANCIAL_ANALYSIS: "금융생활 분석", PROTECTION_GUIDANCE: "보호수단 안내", TRUSTED_CONTACT_DISCLOSURE: "신뢰연락인 최소정보" } as Record<string, string>)[value] ?? value; }
function scopeLabel(value: string) { return Object.fromEntries(CONSENT_SCOPES)[value] ?? value; }
function messageOf(reason: unknown) { return reason instanceof Error ? reason.message : "금융자산 요청을 처리하지 못했습니다."; }
