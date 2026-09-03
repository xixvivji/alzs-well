"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { loadSafetyCenter, respondToSafetyAlert, type SafetyCenterBundle } from "../lib/private-safety-center";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import { LoginRequired } from "./PrivateBankingDashboard";

export function PrivateSafetyCenter() {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [bundle, setBundle] = useState<SafetyCenterBundle | null>(null);
  const [selectedAlertId, setSelectedAlertId] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const requestGeneration = useRef(0);
  const requestAbort = useRef<AbortController | null>(null);
  const applyBundle = useCallback((result: SafetyCenterBundle) => {
    setBundle(result);
    setSelectedAlertId(result.selectedAlert?.alertId ?? "");
  }, []);
  useEffect(() => {
    const generation = ++requestGeneration.current;
    const controller = new AbortController();
    requestAbort.current = controller;
    void restorePrivateCustomerSession().then(async (active) => {
      if (controller.signal.aborted || generation !== requestGeneration.current) return;
      setSession(active);
      const result = await loadSafetyCenter(active, undefined, controller.signal);
      if (controller.signal.aborted || generation !== requestGeneration.current) return;
      applyBundle(result);
    }).catch((reason) => {
      if (!controller.signal.aborted && generation === requestGeneration.current) setError(message(reason));
    }).finally(() => {
      if (requestAbort.current === controller) requestAbort.current = null;
      if (generation === requestGeneration.current) setBusy(false);
    });
    return () => {
      controller.abort();
      if (requestGeneration.current === generation) requestGeneration.current += 1;
    };
  }, [applyBundle]);
  async function chooseAlert(alertId: string) {
    if (!session) return;
    const generation = ++requestGeneration.current;
    requestAbort.current?.abort();
    const controller = new AbortController();
    requestAbort.current = controller;
    setSelectedAlertId(alertId);
    setBusy(true);
    setError("");
    try {
      const result = await loadSafetyCenter(session, alertId, controller.signal);
      if (controller.signal.aborted || generation !== requestGeneration.current) return;
      applyBundle(result);
    } catch (reason) {
      if (!controller.signal.aborted && generation === requestGeneration.current) setError(message(reason));
    } finally {
      if (requestAbort.current === controller) requestAbort.current = null;
      if (generation === requestGeneration.current) setBusy(false);
    }
  }
  async function respond(code: string) {
    if (!session || !bundle?.selectedAlert) return;
    const selectedAlert = bundle.selectedAlert;
    const generation = ++requestGeneration.current;
    requestAbort.current?.abort();
    const controller = new AbortController();
    requestAbort.current = controller;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await respondToSafetyAlert(session, selectedAlert, code);
      const result = await loadSafetyCenter(session, selectedAlert.alertId, controller.signal);
      if (controller.signal.aborted || generation !== requestGeneration.current) return;
      applyBundle(result);
      setNotice("선택한 생활맥락을 반영했습니다. 자동 금융 조치 없이 상태와 감사이력만 갱신했습니다.");
    } catch (reason) {
      if (!controller.signal.aborted && generation === requestGeneration.current) setError(message(reason));
    } finally {
      if (requestAbort.current === controller) requestAbort.current = null;
      if (generation === requestGeneration.current) setBusy(false);
    }
  }
  if (!session && busy) return <section className="bank-panel banking-loading"><div className="bank-spinner" /><p>금융생활 변화를 확인하고 있습니다.</p></section>;
  if (!session) return <LoginRequired message={error} />;
  if (!bundle) return <section className="bank-panel login-required"><h2>안심관리 정보를 불러오지 못했습니다.</h2><p>{error}</p></section>;
  return <div className="safety-center">
    <section className="life-hero protection"><div><p>진단이 아닌 금융생활 변화 확인</p><h2>평소와 달라진 점을 먼저 나에게 묻습니다.</h2><span>30·60·90일 기준선과 최근 생활 변화를 비교하고, 고객 확인과 사람 검토를 거쳐 안내합니다.</span></div><b>자동 거래·차단 0건</b></section>
    <section className="safety-flow bank-panel"><header><div><p>ALZ&apos;s well 보호 흐름</p><h3>도움받기에서 확인할 수 있는 네 단계</h3></div><Link href="/demo">안내형 화면으로 보기 →</Link></header><div>{[["1","AI 금융생활 의향서","원하는 도움과 설명 방식"],["2","장기 변화 탐지","평소값과 최근값 비교"],["3","본인 맥락 확인","알고 있음·모름·나중에"],["4","AI 행원 지원","근거 인용과 사람 승인"]].map(([step,title,copy]) => <article key={step}><span>{step}</span><strong>{title}</strong><small>{copy}</small></article>)}</div></section>
    <section className="banking-summary-grid safety-summary"><article><span>개인 기준선</span><strong>{bundle.baselines.length}개</strong><small>설명 가능한 특징값 {bundle.baselineFeatures.length}개</small></article><article><span>열린 변화신호</span><strong>{bundle.signals.filter((item) => item.status === "OPEN").length}개</strong><small>진단·사기 판정 아님</small></article><article><span>확인 알림</span><strong>{bundle.alerts.length}개</strong><small>고객 응답 전 자동 조치 없음</small></article><article><span>판단 감사이력</span><strong>{bundle.audit.length}개</strong><small>선택 알림 기준</small></article></section>
    <div className="banking-content-grid lower"><section className="bank-panel"><header><div><p>설명 가능한 기준선</p><h3>평소와 최근 비교</h3></div><span>{bundle.baselines[0]?.algorithmVersion ?? "준비 중"}</span></header><div className="safety-metric-list">{bundle.baselines.map((item) => <article key={item.baselineId}><div><strong>{feature(item.featureCode)}</strong><small>{item.comparisonText}</small></div><p><span>평소 {item.baselineValue}{unit(item.unit)}</span><b>최근 {item.currentValue}{unit(item.unit)}</b></p></article>)}</div>{!bundle.baselines.length && <Empty text="이 회원은 아직 계산된 기준선이 없습니다." />}</section><section className="bank-panel"><header><div><p>변화 근거</p><h3>관찰된 신호</h3></div><span>{bundle.evidence.length}개 근거</span></header><div className="safety-signal-list">{bundle.signals.map((item) => <article key={item.signalId}><span className={`severity ${item.severity.toLowerCase()}`}>{severity(item.severity)}</span><p><strong>{feature(item.signalType)}</strong><small>평소 {item.baselineValue} → 최근 {item.currentValue} {unit(item.unit)}</small></p><em>{status(item.status)}</em></article>)}</div>{!bundle.signals.length && <Empty text="평소 범위를 벗어난 변화신호가 없습니다. 정상 상태도 결과로 명확히 표시합니다." />}</section></div>
    <section className="bank-panel safety-alert-panel"><header><div><p>내가 직접 확인</p><h3>금융활동 맥락 확인</h3></div>{bundle.alerts.length > 0 && <select value={selectedAlertId} disabled={busy} onChange={(event) => void chooseAlert(event.target.value)}>{bundle.alerts.map((item) => <option value={item.alertId} key={item.alertId}>{feature(item.reasonCode)} · {state(item.state)}</option>)}</select>}</header>{bundle.selectedAlert ? <><div className="context-question"><span>?</span><div><strong>{bundle.contextQuestion}</strong><small>어떤 선택도 치매 진단이나 금융 불이익으로 이어지지 않습니다.</small></div></div><div className="context-options">{bundle.contextOptions.map((option) => <button disabled={busy || !["AWAITING_CONTEXT","DEFERRED"].includes(bundle.selectedAlert!.state)} onClick={() => void respond(option.responseCode)} key={option.responseCode}><strong>{option.label}</strong><small>{option.description}</small></button>)}</div><div className="audit-timeline">{bundle.audit.map((item) => <article key={item.auditEventId}><i /><time>{dateTime(item.createdAt)}</time><p><strong>{event(item.eventType)}</strong><small>{state(item.previousState)} → {state(item.resultingState)}</small></p><code>{item.integrityHash.slice(0,10)}…</code></article>)}</div></> : <Empty text="확인이 필요한 알림이 없습니다. 주의·오탐 전체 시연은 안내형 도움받기에서 체험할 수 있습니다." />}</section>
    {notice && <p className="workflow-result" role="status">{notice}</p>}{error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

function Empty({ text }: { text: string }) { return <div className="empty-block">{text}</div>; }
function feature(value: string) { return ({ MISSED_PAYMENT: "정기납부 누락 변화", DUPLICATE_TRANSFER: "중복송금 변화", REPEATED_CONFIRMATION: "거래결과 반복 확인", NEW_COUNTERPARTY: "새 수취인 변화", MISSED_RECURRING_PAYMENT: "정기납부 확인" } as Record<string,string>)[value] ?? value.replaceAll("_", " "); }
function severity(value: string) { return ({ LOW: "관찰", MEDIUM: "확인", HIGH: "우선 확인" } as Record<string,string>)[value] ?? value; }
function state(value: string | null) { if (!value) return "시작"; return ({ AWAITING_CONTEXT: "본인 확인 대기", DEFERRED: "나중에 확인", CLOSED_NORMAL: "정상 변화 확인", BANK_REVIEW: "행원 검토", CLOSED_FALSE_POSITIVE: "오탐 종결" } as Record<string,string>)[value] ?? value; }
function status(value: string) { return ({ OPEN: "확인 중", ACKNOWLEDGED: "확인함", CLOSED: "종결" } as Record<string,string>)[value] ?? value; }
function event(value: string) { return ({ ALERT_CREATED: "알림 생성", CONTEXT_RESPONSE_RECORDED: "고객 응답", ALERT_DEFERRED: "확인 연기", APPEAL_SUBMITTED: "사람 재검토 요청" } as Record<string,string>)[value] ?? value; }
function unit(value: string) { return ({ COUNT: "회", KRW: "원", RATIO: "%" } as Record<string,string>)[value] ?? ` ${value}`; }
function dateTime(value: string) { return value ? new Intl.DateTimeFormat("ko-KR",{dateStyle:"short",timeStyle:"short"}).format(new Date(value)) : "-"; }
function message(reason: unknown) { return reason instanceof Error ? reason.message : "안심관리 정보를 불러오지 못했습니다."; }
