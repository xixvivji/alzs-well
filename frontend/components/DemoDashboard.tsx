"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, type ApiResponse } from "../lib/api";
import { clearDemoContext, readDemoContext, saveDemoContext, type DemoContext } from "../lib/demo-session";
import { DEMO_REHEARSAL_SCENARIOS, findRehearsalScenario, type DemoRehearsalScenario } from "../lib/demo-rehearsal";
import { createDemoContext, discardDemoSession } from "../lib/demo-workflow";

type Summary = { assets: { total: { amount: string } }; cashFlow: { monthlyIncome: { amount: string }; monthlyExpense: { amount: string } }; changeSummary: { openAlertCount: number; summary: string } };
type AlertList = { items?: Array<Record<string, unknown>> };
const messageOf = (error: unknown) => (error as Partial<ApiResponse<unknown>>).message ?? (error instanceof Error ? error.message : "백엔드 연결 상태를 확인해 주세요.");

export function DemoDashboard() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [alertCount, setAlertCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function load(active: DemoContext) {
    const common = { capability: active.capability, demoRunId: active.demoRunId };
    const [summaryResponse, alertsResponse] = await Promise.all([
      apiRequest<Summary>(`/api/v1/demo/sessions/${active.sessionId}/customers/${active.customerId}/financial-summary`, common),
      apiRequest<AlertList>(`/api/v1/demo/sessions/${active.sessionId}/customers/${active.customerId}/alerts`, common),
    ]);
    setSummary(summaryResponse.body.data);
    setAlertCount(alertsResponse.body.data?.items?.length ?? summaryResponse.body.data?.changeSummary.openAlertCount ?? 0);
  }

  useEffect(() => {
    const saved = readDemoContext();
    if (saved) {
      setContext(saved);
      load(saved).catch((reason) => setError(messageOf(reason)));
    }
  }, []);

  async function startDemo(rehearsalScenario?: DemoRehearsalScenario) {
    setLoading(true);
    setError("");
    try {
      const created = await createDemoContext();
      const next = { ...created, ...(rehearsalScenario ? { rehearsalScenario } : {}) };
      saveDemoContext(next);
      setContext(next);
      await load(next);
    } catch (reason) {
      setError(messageOf(reason));
    } finally {
      setLoading(false);
    }
  }

  async function reset() {
    if (context) await discardDemoSession(context.sessionId, context.capability);
    clearDemoContext();
    setContext(null);
    setSummary(null);
    setAlertCount(0);
    setError("");
  }

  const money = (value?: string) => value ? `${Number(value).toLocaleString("ko-KR")}원` : "-";

  if (!context) return <>
    <section className="assistance-start">
      <div className="assistance-welcome">
        <div className="assistance-welcome-copy">
          <p>금융생활 도움 서비스</p>
          <h2>복잡한 금융생활,<br /><em>한눈에 쉽게</em> 확인하세요.</h2>
          <span>큰 글씨와 쉬운 말로 내 금융생활을 살펴보고, 평소와 달라진 점이 있으면 먼저 확인할 수 있습니다.</span>
          <button onClick={() => void startDemo()} disabled={loading}>{loading ? "안전하게 준비하고 있어요…" : "내 금융생활 한눈에 보기"}</button>
          <small>실제 개인정보를 쓰지 않는 안전한 안내 환경입니다.</small>
        </div>
        <div className="assistance-glance" aria-label="도움 서비스에서 확인할 수 있는 내용">
          <article><span aria-hidden="true">₩</span><div><strong>내 돈 한눈에</strong><small>자산과 생활비를 보기 쉽게</small></div></article>
          <article><span aria-hidden="true">↗</span><div><strong>달라진 점 확인</strong><small>평소와 달라진 금융생활을 설명</small></div></article>
          <article><span aria-hidden="true">?</span><div><strong>잘 모르겠을 때 도움</strong><small>고객에게 먼저 묻고 행원에게 연결</small></div></article>
        </div>
      </div>
      <div className="assistance-promises">
        <article><span>가</span><div><strong>큰 글씨와 쉬운 문장</strong><p>어려운 금융 용어 대신 이해하기 쉬운 표현을 사용합니다.</p></div></article>
        <article><span>✓</span><div><strong>고객에게 먼저 확인</strong><p>질병이나 사기로 단정하지 않고 본인의 의사를 먼저 묻습니다.</p></div></article>
        <article><span>○</span><div><strong>자동 금융 실행 없음</strong><p>송금·지급정지·보호자 연락을 AI가 대신 실행하지 않습니다.</p></div></article>
      </div>
    </section>
    <details className="panel rehearsal-launch rehearsal-collapsed">
      <summary><span>서비스 확인용 메뉴</span><small>정상·주의·오탐 시나리오 선택</small></summary>
      <div className="section-heading"><div><p className="label">운영 검증</p><h2>확인할 시나리오를 선택하세요.</h2></div><span className="status-chip">정상 · 주의 · 오탐</span></div>
      <div className="rehearsal-grid">{DEMO_REHEARSAL_SCENARIOS.map((scenario) => <button key={scenario.id} onClick={() => void startDemo(scenario.id)} disabled={loading}><span>{scenario.label}</span><strong>{scenario.title}</strong><small>{scenario.summary}</small><em>예상 종결: {scenario.expectedState}</em></button>)}</div>
    </details>
    {error && <p className="api-error" role="alert">{error}</p>}
  </>;

  const rehearsal = findRehearsalScenario(context.rehearsalScenario);
  return <>
    {rehearsal && <section className="panel rehearsal-status" data-rehearsal-scenario={rehearsal.id}><div><p className="label">{rehearsal.label} 확인 흐름 진행 중</p><h2>{rehearsal.title}</h2><p>{rehearsal.summary}</p></div><ol>{rehearsal.checkpoints.map((checkpoint, index) => <li key={checkpoint}><span>{index + 1}</span>{checkpoint}</li>)}</ol></section>}
    <section className="summary-strip"><article className="panel"><p className="label">총 금융자산</p><strong>{money(summary?.assets.total.amount)}</strong></article><article className="panel"><p className="label">월 수입</p><strong>{money(summary?.cashFlow.monthlyIncome.amount)}</strong></article><article className="panel"><p className="label">월 지출</p><strong>{money(summary?.cashFlow.monthlyExpense.amount)}</strong></article></section>
    <section className="panel alert-overview"><div><p className="label">최근 변화 알림</p><h2>{alertCount}개의 변화가 발견되었습니다.</h2><p className="muted">{summary?.changeSummary.summary ?? "고객의 확인이 필요한 금융생활 변화입니다."}</p></div><Link className="primary-button" href="/demo/alerts">알림 확인하기</Link></section>
    <section className="panel alert-overview ai-entry"><div><p className="label">나에게 맞는 AI 도움</p><h2>나의 의향과 장기 변화를 함께 확인하세요.</h2><p className="muted">AI 의향서 · 30·60·90일 변화 · 쉬운말과 음성 안내</p></div><Link className="primary-button" href="/demo/ai-assistant">AI 금융생활 도우미</Link></section>
    {error && <p className="api-error" role="alert">{error}</p>}
    <button className="secondary-button" onClick={() => void reset()} disabled={loading}>다시 처음부터 보기</button>
  </>;
}
