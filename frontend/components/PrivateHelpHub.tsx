"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { loadPrivateHelpOverview, loadPrivateLongitudinalAnalysis, type PrivateHelpOverview } from "../lib/private-help";
import type { ChangeAnalysis } from "../lib/ai-financial-assistance";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import { LoginRequired } from "./PrivateBankingDashboard";
import { MemberAiIntentAssistant } from "./MemberAiIntentAssistant";

export function PrivateHelpHub() {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null); const [overview, setOverview] = useState<PrivateHelpOverview | null>(null); const [analysis, setAnalysis] = useState<ChangeAnalysis | null>(null); const [analyzing, setAnalyzing] = useState(false); const [error, setError] = useState("");
  useEffect(() => { let active = true; void restorePrivateCustomerSession().then(async (restored) => { if (!active) return; setSession(restored); const loaded = await loadPrivateHelpOverview(restored); if (active) setOverview(loaded); }).catch((reason) => { if (active) setError(message(reason)); }); return () => { active = false; }; }, []);
  if (!session && !error) return <section className="bank-panel banking-loading"><div className="bank-spinner" /><p>회원별 도움 정보를 불러오고 있습니다.</p></section>;
  if (!session) return <LoginRequired message={error} />;
  if (!overview) return <section className="bank-panel login-required"><h2>도움 정보를 불러오지 못했습니다.</h2><p>{error}</p></section>;
  const unread = overview.inbox.filter((item) => !item.read).length; const openSignals = overview.signals.filter((item) => item.status === "OPEN").length; const awaitingAlerts = overview.alerts.filter((item) => ["AWAITING_CONTEXT", "DEFERRED"].includes(item.state)).length; const latestChange = overview.baselines.find((item) => item.comparisonText)?.comparisonText;
  return <div className="private-help-hub">
    <section className="help-member-hero"><div><p>{session.displayName}님의 금융생활</p><h2>내 데이터로 도움받기</h2><span>계좌·거래와 같은 회원 ID에 연결된 의향서, 장기 변화와 확인 알림을 사용합니다.</span></div><Link href="/demo">공개 시나리오 별도 체험</Link></section>
    <section className="help-summary-grid" aria-label="회원별 도움 현황"><article><small>금융생활 의향</small><strong>{intentStatus(overview.preparation.readiness)}</strong><span>버전 {overview.preparation.latestApproved?.version ?? overview.intents[0]?.version ?? 0}</span></article><article><small>열린 변화 신호</small><strong>{openSignals}건</strong><span>진단이 아닌 변화 설명</span></article><article><small>내 확인 필요</small><strong>{awaitingAlerts}건</strong><span>알고 있음·모름·나중에</span></article><article><small>읽지 않은 알림</small><strong>{unread}건</strong><span>앱 안에서만 제공</span></article></section>
    <MemberAiIntentAssistant session={session} initialIntent={overview.preparation.latestApproved ?? overview.intents[0] ?? null} />
    <section className="help-member-analysis bank-panel"><header><div><p>로그인 회원 장기 변화</p><h3>평소와 최근을 설명 가능한 값으로 비교</h3></div><button className="secondary-button" disabled={analyzing || !overview.baselines.length} onClick={() => { setAnalyzing(true); setError(""); void loadPrivateLongitudinalAnalysis(session).then(setAnalysis).catch((reason) => setError(message(reason))).finally(() => setAnalyzing(false)); }}>{analyzing ? "분석 중…" : analysis ? "다시 분석" : "30·60·90일 AI 분석"}</button></header><div>{overview.baselines.slice(0, 3).map((item) => <article key={item.baselineId}><span>{featureLabel(item.featureCode)}</span><strong>{item.baselineValue}{unitLabel(item.unit)} → {item.currentValue}{unitLabel(item.unit)}</strong><small>{item.comparisonText || "현재 기준 범위 안에서 관찰 중입니다."}</small></article>)}</div>{!overview.baselines.length && <p className="help-analysis-empty">아직 계산된 회원 기준선이 없습니다. 거래 관찰 기간이 쌓이면 최근 30일과 이전 구간을 비교합니다.</p>}{analysis && <section className="member-window-analysis" aria-live="polite"><div>{analysis.windowComparisons.map((window) => <article key={window.baselineDays}><span>과거 {window.baselineDays}일 기준</span><strong>확인할 변화 {window.changes.filter((item) => item.changeDetected).length}건</strong><small>최근 {window.recentDays}일과 비교</small></article>)}</div>{analysis.changes.map((change) => <p key={change.featureCode}><strong>{featureLabel(change.featureCode)}</strong><span>{change.explanation}</span></p>)}</section>}<footer>로그인한 합성 회원의 기준선만 서버에서 조회 · 입력값 위조 방지 · 진단·사기 판정·자동 금융조치 없음</footer></section>
    {latestChange && <section className="help-change-summary bank-panel"><span aria-hidden="true">!</span><div><p>최근 장기 변화 설명</p><strong>{latestChange}</strong><small>위험도나 질병 확률이 아니라 평소값과 최근값의 차이입니다.</small></div><Link href="/banking/safety">근거와 선택지 확인</Link></section>}
    <section className="help-service-grid"><Link href="/banking/life"><span>01</span><div><strong>AI 금융생활 의향서</strong><small>도움 조건과 설명 방식을 직접 정하고 승인합니다.</small></div><b>의향 관리 →</b></Link><Link href="/banking/safety"><span>02</span><div><strong>장기 변화와 본인 확인</strong><small>평소 기준선과 최근 변화를 보고 내 상황을 답합니다.</small></div><b>변화 확인 →</b></Link><Link href="/banking/life"><span>03</span><div><strong>알림·공식 근거·고객센터</strong><small>내 알림과 승인된 문서의 쉬운 설명을 확인합니다.</small></div><b>생활금융 →</b></Link><Link href="/banking/settings"><span>04</span><div><strong>큰 글씨·도움 설정</strong><small>접근성, 신뢰 연락처와 이의신청을 관리합니다.</small></div><b>설정 열기 →</b></Link></section>
    <section className="help-boundary"><strong>사람이 결정합니다.</strong><span>AI는 변화를 설명하고 질문을 돕지만 진단·송금·지급정지·외부 연락을 자동 실행하지 않습니다.</span></section>
  </div>;
}

function intentStatus(value: string) { return ({ READY: "승인 완료", DRAFT: "확인 필요", NOT_STARTED: "작성 전", NOT_PREPARED: "작성 전" } as Record<string, string>)[value] ?? value; }
function featureLabel(value: string) { return ({ MISSED_PAYMENT: "정기납부 누락", DUPLICATE_TRANSFER: "중복송금", REPEATED_CONFIRMATION: "거래결과 재확인", NEW_COUNTERPARTY: "새 수취인" } as Record<string, string>)[value] ?? value.replaceAll("_", " "); }
function unitLabel(value: string) { return ({ COUNT: "회", KRW: "원", RATIO: "%" } as Record<string, string>)[value] ?? ""; }
function message(reason: unknown) { return reason instanceof Error ? reason.message : "회원별 도움 정보를 불러오지 못했습니다."; }
