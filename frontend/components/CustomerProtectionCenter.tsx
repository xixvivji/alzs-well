"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  loadCustomerProtectionSnapshot,
  type CustomerProtectionSnapshot,
  type ProtectionAlert,
} from "../lib/customer-protection-center";
import { readDemoContext, saveDemoContext, type DemoContext } from "../lib/demo-session";
import { createDemoContext } from "../lib/demo-workflow";

const CHANGE_LABELS: Record<string, string> = {
  MISSED_RECURRING_COUNT: "정기납부 누락",
  DUPLICATE_TRANSFER_COUNT: "중복송금",
  REPEATED_CONFIRMATION_COUNT: "거래결과 재확인",
  NEW_COUNTERPARTY_COUNT: "새 수취인 거래",
  UNUSUAL_TIME_COUNT: "평소와 다른 시간대",
  UNUSUAL_AMOUNT_COUNT: "평소와 다른 금액",
};

const AUDIT_LABELS: Record<string, string> = {
  ALERT_CREATED: "금융생활 변화가 기록됐습니다.",
  CUSTOMER_CONTEXT_APPLIED: "고객의 설명이 반영됐습니다.",
  ALERT_ESCALATED: "행원 보호업무로 연결됐습니다.",
  CASE_REVIEW_STARTED: "행원이 근거 확인을 시작했습니다.",
  GUIDANCE_PLAN_APPROVED: "안내계획을 사람이 승인했습니다.",
  CASE_CLOSED_FALSE_POSITIVE: "사람 검토 후 오탐으로 종결했습니다.",
};

export function CustomerProtectionCenter() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [snapshot, setSnapshot] = useState<CustomerProtectionSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async (active: DemoContext) => {
    setLoading(true); setError("");
    try { setSnapshot(await loadCustomerProtectionSnapshot(active)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "보호센터를 불러오지 못했습니다."); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => {
    const saved = readDemoContext();
    if (!saved) return;
    setContext(saved);
    void load(saved);
  }, [load]);

  async function start() {
    setLoading(true); setError("");
    try {
      const created = await createDemoContext();
      const active: DemoContext = { ...created, rehearsalScenario: "caution" };
      saveDemoContext(active); setContext(active);
      await load(active);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "안전 체험을 시작하지 못했습니다.");
    } finally { setLoading(false); }
  }

  if (!context || (!snapshot && !loading && !error)) {
    return <ProtectionStart busy={loading} error={error} onStart={() => void start()} />;
  }
  if (!snapshot) {
    return <section className="panel protection-loading"><div className={loading ? "bank-spinner" : "protection-error-mark"}>{loading ? "" : "!"}</div><h2>{loading ? "나의 보호 상태를 확인하고 있습니다." : "보호센터 연결을 다시 확인해 주세요."}</h2><p>{error || "합성 금융생활과 AI 보조정보를 안전하게 불러옵니다."}</p><button className="primary-button" type="button" disabled={loading} onClick={() => void start()}>새 안전 체험 시작</button></section>;
  }

  const changes = snapshot.analysis?.changes.filter((item) => item.changeDetected) ?? [];
  const primaryAlert = snapshot.alerts[0];
  const intentApproved = snapshot.intent?.status === "APPROVED";
  const currentState = primaryAlert?.state ?? (snapshot.alerts.length ? "CUSTOMER_CONFIRMATION_REQUIRED" : "NORMAL");

  return <div className="customer-protection-center">
    <section className="protection-hero">
      <div className="protection-hero-copy">
        <p><span>ALZ&apos;s well 안심 보호센터</span> 합성데이터 실시간 요약</p>
        <h2>오늘의 금융생활,<br/><em>확인할 것만 간단하게.</em></h2>
        <p className="protection-hero-description">AI는 변화를 설명하고 사람은 맥락을 확인합니다. 진단하거나 거래를 자동으로 막지 않습니다.</p>
        <div className="protection-hero-actions">
          {primaryAlert ? <Link href={`/demo/alerts/${encodeURIComponent(primaryAlert.alertId)}`}>지금 확인하기 <span>→</span></Link> : <Link href="/demo/ai-assistant">나의 의향 확인 <span>→</span></Link>}
          <button type="button" disabled={loading} onClick={() => void load(context)}>{loading ? "새로고침 중…" : "상태 새로고침"}</button>
        </div>
      </div>
      <div className="protection-focus-card" aria-label="오늘의 보호 상태">
        <div><span className={snapshot.alerts.length ? "focus-dot caution" : "focus-dot safe"}/><p><small>오늘의 확인</small><strong>{snapshot.alerts.length ? `${snapshot.alerts.length}건이 기다리고 있어요` : "확인할 변화가 없어요"}</strong></p></div>
        <dl><div><dt>의향서</dt><dd>{intentApproved ? "승인 완료" : snapshot.intent ? "초안 확인 필요" : "작성 전"}</dd></div><div><dt>장기 변화</dt><dd>{changes.length}개 항목</dd></div><div><dt>현재 단계</dt><dd>{stateLabel(currentState)}</dd></div></dl>
        <small>마지막 화면 확인 · {dateTime(new Date().toISOString())}</small>
      </div>
    </section>

    <section className="protection-metrics" aria-label="보호 상태 요약">
      <article><span className="metric-icon mint">✓</span><div><small>금융생활 의향</small><strong>{intentApproved ? "본인 승인 완료" : "확인이 필요해요"}</strong></div><em>{snapshot.intent ? `v${snapshot.intent.version}` : "미작성"}</em></article>
      <article><span className="metric-icon amber">!</span><div><small>변화 알림</small><strong>{snapshot.alerts.length}건</strong></div><em>{snapshot.alerts.length ? "고객 확인 대기" : "안정"}</em></article>
      <article><span className="metric-icon blue">↗</span><div><small>30·60·90일 변화</small><strong>{changes.length}개</strong></div><em>진단 아님</em></article>
      <article><span className="metric-icon violet">◎</span><div><small>총 금융자산</small><strong>{money(snapshot.financialSummary.assets.total.amount)}</strong></div><em>합성 기준</em></article>
    </section>

    <div className="protection-main-grid">
      <section className="panel protection-today-card">
        <SectionHeading label="TODAY" title="오늘 확인할 내용" side={snapshot.alerts.length ? "확인 대기" : "정상"} />
        {primaryAlert ? <AlertSummary alert={primaryAlert} /> : <div className="protection-empty-safe"><span>✓</span><div><strong>새로 확인할 변화가 없습니다.</strong><p>금융생활의 작은 변화가 생기면 본인에게 먼저 알려드립니다.</p></div></div>}
        <div className="protection-cashflow"><div><small>월 수입</small><strong>{money(snapshot.financialSummary.cashFlow.monthlyIncome.amount)}</strong></div><span>−</span><div><small>월 지출</small><strong>{money(snapshot.financialSummary.cashFlow.monthlyExpense.amount)}</strong></div></div>
        <p className="protection-summary-copy">{snapshot.financialSummary.changeSummary.summary}</p>
      </section>

      <section className="panel protection-intent-card">
        <SectionHeading label="MY STANDARD" title="내가 정한 금융생활 기준" side={intentApproved ? "승인 완료" : "직접 확인"} />
        {snapshot.intent ? <><div className="intent-status-line"><span className={intentApproved ? "approved" : "draft"}>{intentApproved ? "본인 승인" : "수정 가능한 초안"}</span><small>법적 위임 아님 · 자동 실행 없음</small></div><dl className="protection-intent-list"><div><dt>필수 납부</dt><dd>{snapshot.intent.paymentContinuity === "KEEP_ESSENTIAL_PAYMENTS" ? "계속 유지" : "변경 전 확인"}</dd></div><div><dt>설명 방식</dt><dd>{explanationLabel(snapshot.intent.explanationMode)}</dd></div><div><dt>도움 요청</dt><dd>{helpLabel(snapshot.intent.helpCondition)}</dd></div><div><dt>공유 범위</dt><dd>{snapshot.intent.shareScopes.length ? `${snapshot.intent.shareScopes.length}개 항목` : "공유 안 함"}</dd></div></dl></> : <div className="protection-intent-empty"><p>아직 나의 금융생활 의향을 저장하지 않았습니다.</p><span>편한 말로 답하면 AI가 수정 가능한 초안만 만듭니다.</span></div>}
        <Link className="protection-text-link" href="/demo/ai-assistant">의향서 확인·수정하기 →</Link>
      </section>
    </div>

    <section className="panel protection-change-board">
      <SectionHeading label="EXPLAINABLE CHANGE" title="최근 생활과 평소 기준 비교" side="최근 30일 · 이전 60일" />
      {snapshot.analysis ? <div className="protection-change-grid">{snapshot.analysis.changes.slice(0, 4).map((item) => {
        const max = Math.max(item.baselineValue, item.recentValue, 1);
        return <article className={item.changeDetected ? "changed" : "stable"} key={item.featureCode}><div><span>{item.changeDetected ? "변화 확인" : "기준 범위"}</span><strong>{CHANGE_LABELS[item.featureCode] ?? item.featureCode}</strong></div><div className="protection-comparison"><p><small>평소</small><i><b style={{ width: `${Math.max(6, item.baselineValue / max * 100)}%` }}/></i><em>{item.baselineValue}회</em></p><p><small>최근</small><i><b style={{ width: `${Math.max(6, item.recentValue / max * 100)}%` }}/></i><em>{item.recentValue}회</em></p></div><p>{item.explanation}</p></article>;
      })}</div> : <div className="protection-partial"><span>i</span><p><strong>장기 변화 분석을 잠시 불러오지 못했습니다.</strong><small>핵심 알림과 고객 확인 기능은 계속 사용할 수 있습니다.</small></p></div>}
      <p className="protection-method-note">EWMA·CUSUM 보조 분석 · 위험점수와 치매 진단에 사용하지 않음 · 고객 맥락 확인 우선</p>
    </section>

    <div className="protection-lower-grid">
      <section className="panel protection-timeline">
        <SectionHeading label="PROTECTION JOURNEY" title="보호 진행 기록" side={`${snapshot.audit.length}건`} />
        {snapshot.audit.length ? <ol>{snapshot.audit.slice(0, 5).map((item, index) => <li key={item.auditId}><span>{String(index + 1).padStart(2, "0")}</span><div><strong>{AUDIT_LABELS[item.eventType] ?? item.eventType.replaceAll("_", " ")}</strong><small>{dateTime(item.occurredAt)} · 정책 {item.policyVersion}</small></div></li>)}</ol> : <div className="protection-partial"><span>i</span><p><strong>아직 표시할 처리 기록이 없습니다.</strong><small>고객이 알림에 답하면 상태 변화가 순서대로 기록됩니다.</small></p></div>}
      </section>
      <section className="panel protection-rights-card">
        <p className="label">CONSUMER RIGHTS</p><h2>결정은 언제나 고객과 사람에게 있습니다.</h2><ul><li><span>01</span>잘 모르겠으면 나중에 다시 확인할 수 있습니다.</li><li><span>02</span>AI 결과에 사람의 재검토를 요청할 수 있습니다.</li><li><span>03</span>신뢰 연락처에는 금융행위 대리권이 생기지 않습니다.</li></ul><Link href="/demo/settings">도움 설정과 이의신청 관리 →</Link>
      </section>
    </div>

    <section className="protection-quick-menu" aria-label="자주 찾는 보호 메뉴">
      <Link href="/demo/alerts"><span>01</span><strong>변화 알림</strong><small>쉬운 말로 확인</small><b>→</b></Link>
      <Link href="/demo/ai-assistant"><span>02</span><strong>AI 의향서</strong><small>직접 수정·승인</small><b>→</b></Link>
      <Link href="/demo/settings"><span>03</span><strong>도움 설정</strong><small>접근성·신뢰 연락처</small><b>→</b></Link>
      <Link href="/staff/cases"><span>04</span><strong>행원 연결</strong><small>사람의 최종 검토</small><b>→</b></Link>
    </section>

    {snapshot.unavailable.length > 0 && <p className="protection-warning" role="status">일부 보조정보({snapshot.unavailable.map(unavailableLabel).join(", ")})를 불러오지 못했지만 고객 알림과 금융생활 요약은 정상적으로 표시됩니다.</p>}
    {error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

function ProtectionStart({ busy, error, onStart }: { busy: boolean; error: string; onStart: () => void }) {
  return <section className="panel protection-start"><div><p className="label">고객 보호센터</p><h2>오늘 확인할 금융생활을<br/>한 화면에 모았습니다.</h2><p>기존 정상·주의·오탐 합성데이터를 사용하며 실제 금융기관이나 고객정보에는 연결하지 않습니다.</p><button className="primary-button" type="button" disabled={busy} onClick={onStart}>{busy ? "안전 환경 준비 중…" : "보호센터 안전 체험 시작"}</button>{error && <p className="api-error" role="alert">{error}</p>}</div><div className="protection-start-preview"><span>오늘의 확인</span><strong>변화 알림 · 나의 의향 · 사람 검토</strong><small>외부 금융 실행 0건</small></div></section>;
}

function SectionHeading({ label, title, side }: { label: string; title: string; side: string }) {
  return <div className="protection-section-heading"><div><p className="label">{label}</p><h2>{title}</h2></div><span>{side}</span></div>;
}

function AlertSummary({ alert }: { alert: ProtectionAlert }) {
  return <article className="protection-alert"><div><span>확인 필요</span><small>{severityLabel(alert.severity)}</small></div><h3>{alert.title ?? alert.summary ?? "평소와 다른 금융생활 변화가 있습니다."}</h3><p>{alert.explanation ?? "본인이 알고 있는 활동인지 천천히 확인해 주세요."}</p><Link href={`/demo/alerts/${encodeURIComponent(alert.alertId)}`}>내용 확인하고 답하기 →</Link></article>;
}

function money(value: string) { const amount = Number(value); return Number.isFinite(amount) ? `${amount.toLocaleString("ko-KR")}원` : "-"; }
function dateTime(value: string) { const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(parsed); }
function stateLabel(value: string) { return ({ NORMAL: "정상", CUSTOMER_CONFIRMATION_REQUIRED: "고객 확인", PENDING_CUSTOMER_CONFIRMATION: "고객 확인", PENDING_BANK_REVIEW: "행원 검토", GUIDANCE_PLAN_APPROVED: "안내 승인", CLOSED_NORMAL: "정상 종결", CLOSED_FALSE_POSITIVE: "오탐 종결" } as Record<string, string>)[value] ?? value.replaceAll("_", " "); }
function explanationLabel(value: string) { return ({ SIMPLE_TEXT: "쉬운 글", VOICE_AND_TEXT: "글과 음성", STAFF_EXPLANATION: "행원 설명" } as Record<string, string>)[value] ?? value; }
function helpLabel(value: string) { return ({ ON_REPEATED_CHANGE: "반복 변화 시", ON_CUSTOMER_REQUEST: "내가 요청할 때", NEVER_AUTOMATIC: "자동 요청 안 함" } as Record<string, string>)[value] ?? value; }
function severityLabel(value?: string) { return ({ HIGH: "주의", MEDIUM: "확인", LOW: "안내" } as Record<string, string>)[value ?? ""] ?? "확인"; }
function unavailableLabel(value: CustomerProtectionSnapshot["unavailable"][number]) { return ({ intent: "AI 의향", analysis: "장기 변화", audit: "처리 기록" } as const)[value]; }
