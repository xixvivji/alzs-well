"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { ChangeAnalysis } from "../lib/ai-financial-assistance";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import { loadPrivateHelpOverview, loadPrivateLongitudinalAnalysis, type PrivateHelpOverview } from "../lib/private-help";
import type { FinancialIntent } from "../lib/private-life-services";
import { LoginRequired } from "./PrivateBankingDashboard";
import { MemberAiIntentAssistant } from "./MemberAiIntentAssistant";

const journeySteps = [
  { step: 1, title: "내 변화 먼저 확인", description: "무엇이 평소와 달라졌는지 먼저 봅니다.", href: "#help-analysis" },
  { step: 2, title: "도움 방식 정하기", description: "원하는 설명과 도움 조건을 확인합니다.", href: "#help-intent" },
  { step: 3, title: "직접 답하거나 도움 요청", description: "내 상황을 선택하고 필요하면 행원에게 문의합니다.", href: "#help-context" },
] as const;

export function PrivateHelpHub() {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [overview, setOverview] = useState<PrivateHelpOverview | null>(null);
  const [activeIntent, setActiveIntent] = useState<FinancialIntent | null>(null);
  const [analysis, setAnalysis] = useState<ChangeAnalysis | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState("");
  const [currentStep, setCurrentStep] = useState(1);

  useEffect(() => {
    let active = true;
    void restorePrivateCustomerSession()
      .then(async (restored) => {
        if (!active) return;
        setSession(restored);
        const loaded = await loadPrivateHelpOverview(restored);
        if (!active) return;
        setOverview(loaded);
        setActiveIntent(loaded.preparation.latestApproved ?? loaded.intents[0] ?? null);
      })
      .catch((reason) => { if (active) setError(message(reason)); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!overview) return;
    const stages = ["help-analysis", "help-intent", "help-context"]
      .map((id) => document.getElementById(id))
      .filter((element): element is HTMLElement => element !== null);
    const observer = new IntersectionObserver((entries) => {
      const visible = entries.filter((entry) => entry.isIntersecting).sort((left, right) => left.boundingClientRect.top - right.boundingClientRect.top)[0];
      if (visible) setCurrentStep(stages.indexOf(visible.target as HTMLElement) + 1);
    }, { rootMargin: "-20% 0px -60% 0px", threshold: 0 });
    stages.forEach((stage) => observer.observe(stage));
    return () => observer.disconnect();
  }, [overview]);

  if (!session && !error) return <section className="bank-panel banking-loading" aria-live="polite" aria-busy="true"><span className="bank-spinner loading loading-spinner loading-lg" aria-hidden="true" /><p>회원별 도움 정보를 불러오고 있습니다.</p></section>;
  if (!session) return <LoginRequired message={error} />;
  if (!overview) return <section className="bank-panel login-required"><h2>도움 정보를 불러오지 못했습니다.</h2><p>{error}</p></section>;
  const activeSession = session;

  const unread = overview.inbox.filter((item) => !item.read).length;
  const detectedSignals = overview.signals.filter((item) => item.status === "OPEN");
  const openSignals = detectedSignals.length;
  const awaitingAlerts = overview.alerts.filter((item) => ["AWAITING_CONTEXT", "DEFERRED"].includes(item.state)).length;
  const intentReady = activeIntent?.status === "APPROVED" || overview.preparation.readiness === "READY";
  const detectedChangeCount = openSignals || awaitingAlerts;
  const nextTask = detectedChangeCount > 0
    ? { title: `평소와 다른 변화 ${detectedChangeCount}건을 먼저 확인해 주세요.`, description: "도움 방식을 정하거나 답하기 전에 무엇이 달라졌는지 쉬운 말과 실제 수치로 보여드립니다.", href: "#help-analysis", label: "변화 내용 확인하기" }
    : !intentReady
      ? { title: "도움 방식을 확인해 주세요.", description: "유지할 납부, 편한 설명 방식과 도움받을 조건을 직접 정합니다.", href: "#help-intent", label: "도움 방식 정하기" }
      : { title: "지금 확인할 새로운 변화는 없습니다.", description: "현재 도움 방식을 확인하거나 필요할 때 언제든 바꿀 수 있습니다.", href: "#help-intent", label: "도움 방식 확인하기" };

  async function analyzeChanges() {
    setAnalyzing(true);
    setError("");
    try { setAnalysis(await loadPrivateLongitudinalAnalysis(activeSession)); }
    catch (reason) { setError(message(reason)); }
    finally { setAnalyzing(false); }
  }

  return <div className="private-help-hub">
    <section className="help-member-hero">
      <div><h2>{nextTask.title}</h2><span>{session.displayName}님, {nextTask.description}</span></div>
      <Link className="btn btn-primary" href={nextTask.href} onClick={() => setCurrentStep(nextTask.href === "#help-analysis" ? 1 : 2)}>{nextTask.label}</Link>
    </section>

    <nav className="help-journey" aria-label="금융생활 도움 이용 순서">
      {journeySteps.map((item) => <Link aria-current={currentStep === item.step ? "step" : undefined} className={currentStep === item.step ? "current" : currentStep > item.step ? "complete" : ""} href={item.href} key={item.step} onClick={() => setCurrentStep(item.step)}><b>{item.step}</b><div><strong>{item.title}</strong><span>{item.description}</span></div></Link>)}
    </nav>

    <section className="help-summary-grid" aria-label="회원별 도움 현황">
      <article className={openSignals > 0 ? "has-change" : ""}><small>평소와 다른 변화</small><strong>{openSignals}건</strong><span>질병 진단이 아닌 변화 설명</span></article>
      <article className={awaitingAlerts > 0 ? "has-change" : ""}><small>내 확인 필요</small><strong>{awaitingAlerts}건</strong><span>읽지 않은 알림 {unread}건</span></article>
      <article><small>금융생활 의향</small><strong>{intentStatus(activeIntent?.status ?? overview.preparation.readiness)}</strong><span>버전 {activeIntent?.version ?? 0}</span></article>
    </section>

    <section className="help-member-analysis bank-panel" id="help-analysis" aria-labelledby="help-analysis-title">
      <header><div><h3 id="help-analysis-title"><span className="help-stage-number">1단계 · </span>무엇이 달라졌는지 먼저 확인합니다</h3><span>도움 방식을 정하거나 답하기 전에 감지된 변화와 평소 수치를 먼저 보여드립니다.</span></div><button className="btn btn-primary" disabled={analyzing || !overview.baselines.length} onClick={() => void analyzeChanges()}>{analyzing ? "자세히 비교하는 중…" : analysis ? "장기 비교 다시 보기" : "30·60·90일 자세히 보기"}</button></header>
      {detectedSignals.length > 0
        ? <div className="help-detected-change-list" aria-label="감지된 금융생활 변화">{detectedSignals.slice(0, 3).map((item) => <article key={item.signalId}><div><span>확인할 변화</span><strong>{featureLabel(item.reasonCode || item.signalType)}</strong><small>{signalDescription(item.reasonCode || item.signalType)}</small></div><p><span>평소</span><b>{formatMetricValue(item.baselineValue, item.unit)}</b><i aria-hidden="true">→</i><span>최근</span><b>{formatMetricValue(item.currentValue, item.unit)}</b></p></article>)}</div>
        : <p className="help-analysis-empty">지금은 평소 범위를 벗어난 새로운 변화가 없습니다.</p>}
      {overview.baselines.length > 0 && <><p className="help-baseline-label">평소 기준과 최근 수치</p><div className="help-baseline-comparison">{overview.baselines.slice(0, 3).map((item) => <article key={item.baselineId}><span>{featureLabel(item.featureCode)}</span><strong>{formatMetricValue(item.baselineValue, item.unit)} → {formatMetricValue(item.currentValue, item.unit)}</strong><small>{item.comparisonText || "현재 기준 범위 안에서 관찰 중입니다."}</small></article>)}</div></>}
      {!overview.baselines.length && <p className="help-analysis-empty">아직 장기 비교를 위한 기록이 충분하지 않습니다. 감지된 변화는 위에서 먼저 확인할 수 있습니다.</p>}
      {error && <p className="api-error" role="alert">{error}</p>}
      {analysis && <section className="member-window-analysis" aria-live="polite">
        <header><h4><span>AI가 수치에서 확인한 내용 · </span>{analysis.summary}</h4><small>{analysis.fallbackUsed ? "AI 연결이 어려워 검증된 기본 설명을 표시했습니다." : "설명 가능한 변화 분석이 완료됐습니다."}</small></header>
        <div>{analysis.windowComparisons.map((window) => <article key={window.baselineDays}><span>과거 {window.baselineDays}일과 비교</span><strong>확인할 변화 {window.changes.filter((item) => item.changeDetected).length}건</strong><small>최근 {window.recentDays}일 기준</small></article>)}</div>
        <div className="member-guidance-grid"><section><strong>나에게 물어볼 질문</strong><ol>{analysis.confirmationQuestions.map((question) => <li key={question}>{question}</li>)}</ol></section><section><strong>도움을 요청하기 전 확인</strong><ul>{analysis.reviewChecklist.map((item) => <li key={item}>{item}</li>)}</ul></section></div>
        <details><summary>수치와 분석 근거 보기</summary>{analysis.changes.map((change) => <p key={change.featureCode}><strong>{featureLabel(change.featureCode)}</strong><span>{change.explanation}</span></p>)}</details>
        {awaitingAlerts > 0 ? <Link className="primary-button member-analysis-next" href="/banking/safety">이 변화에 내 상황 답하기</Link> : <p className="member-analysis-clear">지금 답할 변화는 없습니다. 새 변화가 생기면 이 화면에서 다음 행동을 안내합니다.</p>}
      </section>}
      <footer>변화는 질병이나 사기를 뜻하지 않습니다. AI가 거래를 실행하거나 계좌를 막지 않으며, 선택은 고객과 행원이 합니다.</footer>
    </section>

    <section className="help-work-stage" id="help-intent" aria-labelledby="help-intent-title">
      <header><div><h3 id="help-intent-title"><span className="help-stage-number">2단계 · </span>내 도움 방식을 정합니다</h3><p>변화를 확인한 뒤 원하는 설명 방식과 도움 조건을 정합니다. AI가 문장을 정리해도 저장과 승인은 내가 직접 합니다.</p></div>{intentReady && <b>승인 완료</b>}</header>
      {intentReady
        ? <details className="help-intent-details"><summary>현재 도움 방식 확인</summary><div className="help-intent-readonly"><dl><div><dt>필수 납부</dt><dd>{intentValueLabel(activeIntent?.paymentContinuity)}</dd></div><div><dt>설명 방식</dt><dd>{intentValueLabel(activeIntent?.explanationMode)}</dd></div><div><dt>도움 조건</dt><dd>{intentValueLabel(activeIntent?.helpCondition)}</dd></div></dl><p>승인된 의향은 이 화면에서 바로 덮어쓰지 않습니다. 관리 화면에서 철회한 뒤 새 초안을 만들 수 있습니다.</p><Link className="btn btn-outline" href="/banking/life">도움 방식 변경·철회</Link></div></details>
        : <MemberAiIntentAssistant session={activeSession} initialIntent={activeIntent} onIntentChange={setActiveIntent} />}
    </section>

    <section className={`help-next-step bank-panel ${awaitingAlerts > 0 ? "ready" : "empty"}`} id="help-context" aria-labelledby="help-context-title">
      <div><h3 id="help-context-title"><span className="help-stage-number">3단계 · </span>{awaitingAlerts > 0 ? "내 상황을 직접 답합니다" : "지금 답할 내용은 없습니다"}</h3><p>{awaitingAlerts > 0 ? "선택하기 전에 무엇이 달라졌고 선택 후 무엇이 일어나는지 먼저 보여드립니다." : "새로 확인할 변화가 생기면 이곳에서 다음 행동을 알려드립니다."}</p></div>
      {awaitingAlerts > 0 && <Link className="btn btn-primary" href="/banking/safety">내 상황 답하기</Link>}
    </section>

    <section className="help-support-links" aria-label="도움 설정과 고객 권리">
      <Link href="/banking/life"><strong>도움 방식·알림 자세히 관리</strong><span>의향 이력, 앱 알림과 공식 근거를 확인합니다.</span></Link>
      <Link href="/banking/settings"><strong>내 정보·접근성 설정</strong><span>큰 글씨, 동의, 신뢰 연락처와 이의신청을 관리합니다.</span></Link>
      <Link href="/demo"><strong>대표 사례 빠른 체험</strong><span>로그인 정보와 분리된 공개 시나리오를 봅니다.</span></Link>
    </section>

    <section className="help-boundary"><strong>사람이 결정합니다.</strong><span>AI는 변화를 설명하고 질문을 돕지만 진단·송금·지급정지·외부 연락을 자동 실행하지 않습니다.</span></section>
  </div>;
}

function intentStatus(value: string) {
  return ({ APPROVED: "승인 완료", READY: "승인 완료", DRAFT: "확인 필요", NOT_STARTED: "작성 전", NOT_PREPARED: "작성 전" } as Record<string, string>)[value] ?? value;
}

function intentValueLabel(value?: string) {
  return ({ KEEP_ESSENTIAL_PAYMENTS: "공과금·생활비 납부 유지", REVIEW_BEFORE_CHANGE: "변경 전에 다시 확인", SIMPLE_TEXT: "짧고 쉬운 글", VOICE_AND_TEXT: "음성과 글 함께", STAFF_EXPLANATION: "행원이 천천히 설명", ON_REPEATED_CHANGE: "반복 변화가 있을 때", ON_CUSTOMER_REQUEST: "내가 요청했을 때", NEVER_AUTOMATIC: "자동 요청하지 않기" } as Record<string, string>)[value ?? ""] ?? "설정 없음";
}

function featureLabel(value: string) {
  return ({ MISSED_PAYMENT: "정기납부 누락", MISSED_RECURRING_PAYMENT: "정기납부 누락", DUPLICATE_TRANSFER: "중복송금", REPEATED_CONFIRMATION: "거래결과 재확인", NEW_COUNTERPARTY: "새 수취인" } as Record<string, string>)[value] ?? value.replaceAll("_", " ");
}

function signalDescription(value: string) {
  return ({
    MISSED_PAYMENT: "평소 이어지던 정기납부가 최근에는 확인되지 않았습니다.",
    MISSED_RECURRING_PAYMENT: "평소 이어지던 정기납부가 최근에는 확인되지 않았습니다.",
    DUPLICATE_TRANSFER: "비슷한 송금이 짧은 기간에 반복해 확인됐습니다.",
    REPEATED_CONFIRMATION: "거래 결과를 다시 확인한 횟수가 평소보다 늘었습니다.",
    NEW_COUNTERPARTY: "평소 거래하지 않던 새 수취인이 확인됐습니다.",
  } as Record<string, string>)[value] ?? "평소 금융생활과 다른 변화가 확인됐습니다.";
}

function unitLabel(value: string) {
  return ({ COUNT: "회", KRW: "원", RATIO: "%" } as Record<string, string>)[value] ?? "";
}

function formatMetricValue(value: string, unit: string) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) return `${value}${unitLabel(unit)}`;
  const formatted = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: Number.isInteger(numericValue) ? 0 : 1 }).format(numericValue);
  return `${formatted}${unitLabel(unit)}`;
}

function message(reason: unknown) {
  return reason instanceof Error ? reason.message : "회원별 도움 정보를 불러오지 못했습니다.";
}
