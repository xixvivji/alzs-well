"use client";

import Link from "next/link";
import { accountDisplayName } from "../lib/presentation-copy";
import { useEffect, useState } from "react";
import type { ChangeAnalysis } from "../lib/ai-financial-assistance";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import { loadPrivateHelpOverview, loadPrivateLongitudinalAnalysis, type PrivateHelpOverview } from "../lib/private-help";
import type { FinancialIntent } from "../lib/private-life-services";
import { changeLabel, intentValueLabel } from "../lib/continuity-labels";
import { LoginRequired } from "./PrivateBankingDashboard";
import { MemberAiIntentAssistant } from "./MemberAiIntentAssistant";
import { PrivateSafetyCenter } from "./PrivateSafetyCenter";

export function PrivateHelpHub() {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [overview, setOverview] = useState<PrivateHelpOverview | null>(null);
  const [activeIntent, setActiveIntent] = useState<FinancialIntent | null>(null);
  const [analysis, setAnalysis] = useState<ChangeAnalysis | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    void restorePrivateCustomerSession().then(async (restored) => {
      if (!active) return;
      if (!restored.roles.includes("CUSTOMER")) throw new Error("고객 계정으로 전환해 주세요. 행원 로그인과 고객 로그인은 구분됩니다.");
      setSession(restored);
      const loaded = await loadPrivateHelpOverview(restored);
      if (!active) return;
      setOverview(loaded); setActiveIntent(loaded.preparation.latestApproved ?? loaded.intents[0] ?? null); setError("");
    }).catch((reason) => { if (active) setError(message(reason)); });
    return () => { active = false; };
  }, [retry]);
  if (!session && !error) return <section className="bank-panel" aria-busy="true"><p role="status">회원별 도움 정보를 불러옵니다.</p></section>;
  if (!session) return <LoginRequired message={error} />;
  if (!overview) return <section className="bank-panel"><h2>도움 정보를 확인하고 있습니다.</h2>{error ? <><p role="alert">{error}</p><button className="btn btn-outline" onClick={() => setRetry((value) => value + 1)}>다시 불러오기</button></> : <p role="status">잠시 기다려 주세요.</p>}</section>;
  const activeSession = session;
  const intentReady = activeIntent?.status === "APPROVED" || overview.preparation.readiness === "READY";
  async function analyzeChanges() {
    setAnalyzing(true); setError("");
    try { setAnalysis(await loadPrivateLongitudinalAnalysis(activeSession)); }
    catch (reason) { setError(message(reason)); }
    finally { setAnalyzing(false); }
  }
  return <div className="private-help-hub">
    <p className="continuity-intro">{accountDisplayName(session.displayName)}님, 변화를 먼저 확인하고 내 상황을 알려주세요.</p>
    <nav className="help-journey" aria-label="금융생활 도움 이용 순서">{[["#help-analysis", "변화·근거 확인", "평소와 최근을 비교합니다."], ["#help-context", "내 상황 답하기", "알고 있는 활동인지 선택합니다."], ["#help-status", "연결 상태 확인", "필요한 경우 행원 검토로 이어집니다."]].map(([href, title, description], index) => <a href={href} key={href}><b>{index + 1}</b><div><strong>{title}</strong><span>{description}</span></div></a>)}</nav>
    <div className="continuity-preparation"><p><strong>내 도움 방식: {intentReady ? "승인 완료" : "확인 필요"}</strong> · 처음 이용하거나 선호가 바뀌었을 때만 설정합니다. 알림에 답할 때마다 작성하지 않습니다.</p><a href="#help-intent">{intentReady ? "현재 도움 방식 보기" : "금융생활 의향 준비하기"}</a></div>
    <PrivateSafetyCenter session={session} initialLists={overview} />
    <section className="bank-panel continuity-workspace" aria-labelledby="longitudinal-title"><header className="continuity-heading"><div><h3 id="longitudinal-title">더 자세한 장기 변화 비교</h3><p>필요할 때 30·60·90일 기준으로 비교합니다. 위의 변화 확인과 응답은 AI 분석 없이도 이용할 수 있습니다.</p></div><button className="btn btn-outline" disabled={analyzing || !overview.baselines.length} onClick={() => void analyzeChanges()}>{analyzing ? "비교하는 중…" : "30·60·90일 비교 보기"}</button></header>
      {!overview.baselines.length && <p>장기 비교를 위한 기준선이 아직 없습니다.</p>}{error && <p className="api-error" role="alert">{error}</p>}
      {analysis && <section className="member-window-analysis" aria-live="polite"><h4>AI 변화 설명 · {analysis.summary}</h4><p>{analysis.fallbackUsed ? "AI 연결이 어려워 검증된 기본 설명을 표시했습니다." : "수치에 기반한 설명이며 사람의 확인이 필요합니다."}</p><div>{analysis.windowComparisons.map((window) => <article key={window.baselineDays}><span>과거 {window.baselineDays}일과 비교</span><strong>확인할 변화 {window.changes.filter((item) => item.changeDetected).length}건</strong><small>최근 {window.recentDays}일 기준</small></article>)}</div><div className="member-guidance-grid"><section><h4>나에게 물어볼 질문</h4><ol>{analysis.confirmationQuestions.map((question) => <li key={question}>{question}</li>)}</ol></section><section><h4>추가 확인할 내용</h4><ul>{analysis.reviewChecklist.map((item) => <li key={item}>{item}</li>)}</ul></section></div><details><summary>분석 근거 자세히 보기</summary>{analysis.changes.map((change) => <p key={change.featureCode}><strong>{changeLabel(change.featureCode)}</strong> {change.explanation}</p>)}</details></section>}
    </section>
    <section className="help-work-stage" id="help-intent" aria-labelledby="help-intent-title"><header><div><h3 id="help-intent-title">내 금융생활 의향·도움 방식</h3><p>유지할 납부, 편한 설명 방식과 도움 조건을 직접 정합니다. 저장과 승인은 내가 합니다.</p></div>{intentReady && <b>승인 완료</b>}</header>
      {intentReady ? <details className="help-intent-details"><summary>현재 도움 방식 확인·관리</summary><div className="help-intent-readonly"><dl><div><dt>필수 납부</dt><dd>{intentValueLabel(activeIntent?.paymentContinuity)}</dd></div><div><dt>설명 방식</dt><dd>{intentValueLabel(activeIntent?.explanationMode)}</dd></div><div><dt>도움 조건</dt><dd>{intentValueLabel(activeIntent?.helpCondition)}</dd></div></dl><Link className="btn btn-outline" href="/banking/life">도움 방식 변경·철회</Link></div></details> : <details className="help-intent-details"><summary>처음 이용하는 경우 · 도움 방식 정하기</summary><MemberAiIntentAssistant session={session} initialIntent={activeIntent} onIntentChange={setActiveIntent} /></details>}
    </section>
    <section className="help-support-links" aria-label="도움 설정과 고객 권리"><Link href="/banking/life"><strong>의향·알림 관리</strong><span>이력과 현재 설정을 확인합니다.</span></Link><Link href="/banking/settings"><strong>내 정보·이의신청</strong><span>동의, 연락처와 사람의 재검토 요청을 관리합니다.</span></Link></section>
  </div>;
}
function message(reason: unknown) { return reason instanceof Error ? reason.message : "회원별 도움 정보를 불러오지 못했습니다."; }
