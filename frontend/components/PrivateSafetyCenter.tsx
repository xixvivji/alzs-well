"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { PrivateCustomerSession } from "../lib/private-financial-products";
import { deferSafetyAlert, loadSafetyCenter, respondToSafetyAlert, type SafetyCenterBundle } from "../lib/private-safety-center";
import { alertStateLabel, changeLabel, dateTime, metric, responseLabel } from "../lib/continuity-labels";
import { ReviewLoginContext } from "./LoginNavigationContext";
import { evidenceDescription } from "../lib/presentation-copy";

type Props = { session: PrivateCustomerSession; initialLists: Pick<SafetyCenterBundle, "baselines" | "signals" | "alerts"> };

export function PrivateSafetyCenter({ session, initialLists }: Props) {
  const [bundle, setBundle] = useState<SafetyCenterBundle | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const generation = useRef(0);
  const request = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController(); request.current = controller;
    const current = ++generation.current;
    const requested = new URLSearchParams(window.location.search).get("alertId") ?? undefined;
    void loadSafetyCenter(session, requested, controller.signal, initialLists)
      .then((value) => { if (current === generation.current && !controller.signal.aborted) setBundle(value); })
      .catch((reason) => { if (!controller.signal.aborted) setError(message(reason)); })
      .finally(() => { if (!controller.signal.aborted) setBusy(false); });
    return () => { controller.abort(); request.current?.abort(); generation.current += 1; };
  }, [session, initialLists]);

  useEffect(() => {
    if (!notice || !bundle) return;
    const element = document.getElementById("help-status");
    element?.focus({ preventScroll: true });
    element?.scrollIntoView({ block: "start", behavior: "instant" });
  }, [notice, bundle]);

  async function refresh(alertId?: string, action?: string) {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    const current = ++generation.current;
    setBusy(true); setError(""); setNotice("");
    if (!action) setBundle(null);
    let saved = false;
    try {
      if (action && bundle?.selectedAlert) {
        if (action === "DEFER") await deferSafetyAlert(session, bundle.selectedAlert);
        else await respondToSafetyAlert(session, bundle.selectedAlert, action);
        saved = true;
        window.dispatchEvent(new Event("alzs:change-status-updated"));
        // A successful command must not leave the old actionable version on screen.
        if (current === generation.current) setBundle(null);
      }
      const value = await loadSafetyCenter(session, alertId, controller.signal);
      if (controller.signal.aborted || current !== generation.current) return;
      setBundle(value);
      if (action) setNotice(action === "DEFER" ? "하루 뒤까지 미뤘습니다. 은행 검토를 요청하지 않았습니다." : value.selectedAlert?.state === "BANK_REVIEW" ? "응답을 저장하고 은행 검토로 연결했습니다. 행원은 이 응답과 변화 근거를 함께 확인합니다." : "알고 있는 변화로 저장했습니다. 이 응답으로 은행 검토를 요청하지 않았습니다.");
      if (value.selectedAlert) window.history.replaceState(null, "", `/banking/help?alertId=${encodeURIComponent(value.selectedAlert.alertId)}#help-analysis`);
    } catch (reason) {
      if (!controller.signal.aborted && current === generation.current) {
        if (action) setBundle(null);
        setError(`${saved ? "선택은 저장됐지만 최신 상태를 불러오지 못했습니다. 다시 제출하지 말고 새로 확인해 주세요. " : action ? "선택 처리 여부를 먼저 새로 확인해 주세요. " : ""}${message(reason)}`);
      }
    } finally { if (current === generation.current && !controller.signal.aborted) setBusy(false); }
  }

  if (!bundle) return <section className="bank-panel continuity-workspace" id="help-analysis" aria-busy={busy}>
    <h3>변화와 확인 이력</h3>{busy ? <p role="status">현재 계정의 변화와 근거를 불러옵니다.</p> : <><p role="alert">{error}</p><button className="btn btn-outline" onClick={() => void refresh()}>목록 새로 확인</button></>}
  </section>;

  const alert = bundle.selectedAlert;
  const signal = alert ? bundle.signals.find((item) => item.signalId === alert.signalId) : bundle.signals[0];
  const feature = signal && bundle.baselines.find((item) => item.baselineId === signal.baselineId);
  const canRespond = alert && ["AWAITING_CONTEXT", "DEFERRED"].includes(alert.state);
  const latestResponse = [...bundle.audit].reverse().find((item) => typeof item.detail.responseCode === "string");
  const handoff = [...bundle.audit].reverse().find((item) => typeof item.detail.caseId === "string");
  const caseId = handoff ? String(handoff.detail.caseId) : null;

  return <div className="continuity-workspace" aria-busy={busy}>
    <section className="bank-panel continuity-change" id="help-analysis" aria-labelledby="change-title">
      <header className="continuity-heading"><div><h3 id="change-title">1. 무엇이 달라졌나요?</h3><p>확인할 변화를 선택해 주세요.</p></div><button className="btn btn-outline" disabled={busy} onClick={() => void refresh(alert?.alertId)}>새로 확인</button></header>
      {bundle.alerts.length > 0 && <label className="continuity-field"><span>변화·확인 이력 {bundle.alerts.length}건</span><select className="select" value={alert?.alertId ?? ""} disabled={busy} onChange={(event) => void refresh(event.target.value)}>{bundle.alerts.map((item) => <option key={item.alertId} value={item.alertId}>{changeLabel(item.reasonCode)} · {alertStateLabel(item.state)} · {dateTime(item.createdAt)}</option>)}</select></label>}
      {signal ? <>
        <div className={`continuity-fact ${canRespond ? "needs-context" : ""}`}><h4>{changeLabel(signal.reasonCode)}</h4><dl><div><dt>평소 기준</dt><dd>{metric(signal.baselineValue, signal.unit)}</dd></div><div><dt>최근 관찰</dt><dd>{metric(signal.currentValue, signal.unit)}</dd></div></dl><p>{(feature?.comparisonText ? evidenceDescription(feature.comparisonText) : "") || "개인의 평소 기준과 최근 관찰값을 비교한 결과입니다."}</p><p>변화 기록 시각: {dateTime(signal.detectedAt)}</p>{bundle.baselineFeatures.map((item) => <p key={item.featureId}>관찰 기간: {item.observedPeriod.from} ~ {item.observedPeriod.to} · 기록 {item.sampleCount}개</p>)}</div>
        <h4>확인된 근거</h4>
        {bundle.evidence.length ? <ul className="continuity-evidence">{bundle.evidence.map((item) => <li key={item.evidenceId}><p>{evidenceDescription(item.description)}</p><span>{dateTime(item.occurredAt)}{item.amount != null ? ` · ${metric(item.amount, item.currency)}` : ""}</span></li>)}</ul> : <p>연결된 상세 기록이 없습니다.</p>}
      </> : <p>현재 기록된 변화가 없습니다. 새로 확인할 내용이 생기면 이곳에 표시됩니다.</p>}
      <p className="continuity-boundary">평소와 달라진 기록이며 질병이나 사기 판정은 아닙니다.</p>
      <details><summary>전체 관찰 수치·기술 정보</summary><ul>{bundle.signals.map((item) => <li key={item.signalId}>{changeLabel(item.reasonCode)}: {metric(item.baselineValue, item.unit)} → {metric(item.currentValue, item.unit)} · {dateTime(item.detectedAt)}</li>)}</ul><ul>{bundle.baselines.map((item) => <li key={item.baselineId}>{changeLabel(item.featureCode)}: {metric(item.baselineValue, item.unit)} → {metric(item.currentValue, item.unit)} · {evidenceDescription(item.comparisonText)}</li>)}</ul><p>알고리즘: {signal?.algorithmVersion ?? "기록 없음"}</p></details>
    </section>
    <section className="bank-panel" id="help-context" aria-labelledby="context-title">
      <h3 id="context-title">2. 이 활동을 알고 계신가요?</h3>
      {canRespond ? <>
        <p>{bundle.contextQuestion}</p><p>모르거나 확인하기 어려운 변화는 은행에 확인을 요청할 수 있습니다.</p>
        <div className="continuity-options">{bundle.contextOptions.map((option) => <button className="btn btn-outline" key={option.responseCode} disabled={busy} onClick={() => void refresh(alert.alertId, option.responseCode)}><strong>{option.label}</strong><span>{option.description}</span></button>)}</div>
        <div className="continuity-secondary"><button className="btn btn-outline" disabled={busy} onClick={() => void refresh(alert.alertId, "DEFER")}>나중에 확인 · 하루 미루기</button><Link href="/banking/life">내 도움 방식 확인</Link></div>
        {alert.deferredUntil && <p>미룬 기한: {dateTime(alert.deferredUntil)}. 지금 답할 수도 있습니다.</p>}
      </> : <p>{alert ? "이 변화는 이미 확인했습니다. 저장된 응답과 연결 상태를 아래에서 확인하세요." : "현재 답할 알림이 없습니다."}</p>}
      <p className="continuity-boundary">송금·지급정지·상품 가입·가족 연락은 자동으로 실행되지 않습니다.</p>
    </section>
    <section className="bank-panel" id="help-status" tabIndex={-1} aria-labelledby="status-title" aria-live="polite">
      <h3 id="status-title">3. 확인 이후에는 어떻게 되나요?</h3>
      {notice && <p className="workflow-result" role="status">{notice}</p>}
      <dl className="continuity-status"><div><dt>현재 알림 상태</dt><dd>{alert ? alertStateLabel(alert.state) : "답할 알림 없음"}</dd></div><div><dt>내가 남긴 응답</dt><dd>{responseLabel(latestResponse?.detail.responseCode as string | undefined)}</dd></div></dl>
      {alert?.state === "BANK_REVIEW" ? <><p>은행에 확인을 요청했습니다. 남긴 응답과 변화 내용을 행원이 검토합니다.</p></> : <p>도움 방식은 필요할 때 언제든 바꿀 수 있습니다.</p>}
      {bundle.audit.length > 0 && <details><summary>선택·처리 이력 {bundle.audit.length}건</summary><ol className="continuity-timeline">{bundle.audit.map((item) => <li key={item.auditEventId}><strong>{alertStateLabel(item.resultingState)}</strong><span>{dateTime(item.createdAt)}{typeof item.detail.responseCode === "string" ? ` · ${responseLabel(item.detail.responseCode)}` : ""}</span></li>)}</ol></details>}
      {error && <p role="alert" className="api-error">{error}</p>}
    </section>
    <ReviewLoginContext caseId={caseId} customerId={session.customerId} alertId={alert?.alertId} />
  </div>;
}
function message(reason: unknown) { return reason instanceof Error ? reason.message : "정보를 불러오지 못했습니다. 새로 확인해 주세요."; }
