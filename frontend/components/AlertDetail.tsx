"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiRequest, type ApiResponse } from "../lib/api";
import { loadAlertAudit, type AlertAuditItem } from "../lib/alert-audit";
import { readDemoContext, type DemoContext } from "../lib/demo-session";
import { deferDemoAlert } from "../lib/demo-workflow";
import { contextPayloadForScenario, findRehearsalScenario } from "../lib/demo-rehearsal";

type Detail = Record<string, unknown>;
type EvidenceSignal = {
  reasonCode: string;
  baselineValue: number;
  currentValue: number;
  unit: string;
};
type Resolution = { message: string; currentState: string; deferredUntil?: string };
type AnswerBranch = "normal" | "review" | "later";

export function AlertDetail({ alertId }: { alertId: string }) {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [result, setResult] = useState<Resolution | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [audit, setAudit] = useState<AlertAuditItem[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const deferCommand = useRef<{
    expectedVersion: number;
    deferredUntil: string;
    idempotencyKey: string;
  } | null>(null);

  useEffect(() => {
    const active = readDemoContext();
    if (!active) { setError("먼저 서비스 체험을 시작해 주세요."); setLoading(false); return; }
    setContext(active); deferCommand.current = null;
    Promise.all([apiRequest<Detail>(`/api/v1/demo/sessions/${active.sessionId}/alerts/${alertId}`, {
      capability: active.capability, demoRunId: active.demoRunId,
    }), loadAlertAudit(active, alertId)]).then(([{ body }, history]) => {
      setDetail(body.data); setAudit(history.items);
      const state = String(body.data?.state ?? body.data?.currentState ?? "");
      if (state === "DEFERRED") {
        setResult({
          message: "나중에 다시 확인하도록 안전하게 남겨 둔 알림입니다.",
          currentState: state,
          deferredUntil: String(body.data?.deferredUntil ?? ""),
        });
      }
    })
      .catch((reason: Partial<ApiResponse<unknown>>) => setError(reason.message ?? "알림 상세를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [alertId]);

  async function answer(branch: AnswerBranch) {
    const active = readDemoContext();
    if (!active) return;
    setSubmitting(true); setError("");
    try {
      if (branch === "later") {
        const expectedVersion = Number(detail?.incidentVersion);
        if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 1) {
          throw new Error("알림 버전을 확인할 수 없어 나중 확인을 저장하지 못했습니다.");
        }
        if (!deferCommand.current || deferCommand.current.expectedVersion !== expectedVersion) {
          deferCommand.current = {
            expectedVersion,
            deferredUntil: new Date(Date.now() + 24 * 60 * 60 * 1_000).toISOString(),
            idempotencyKey: crypto.randomUUID(),
          };
        }
        const command = deferCommand.current;
        const deferred = await deferDemoAlert(
          active, alertId, command.expectedVersion, command.deferredUntil, command.idempotencyKey,
        );
        deferCommand.current = null;
        setDetail((current) => current ? {
          ...current,
          state: deferred.currentState,
          incidentVersion: deferred.incidentVersion,
          deferredUntil: deferred.deferredUntil,
        } : current);
        setResult({
          message: "내일 다시 확인할 수 있도록 알림을 안전하게 남겼습니다.",
          currentState: deferred.currentState,
          deferredUntil: deferred.deferredUntil,
        });
        setAuditLoading(true);
        loadAlertAudit(active, alertId)
          .then((history) => setAudit(history.items))
          .catch((reason) => setError(reason instanceof Error ? reason.message : "감사이력을 갱신하지 못했습니다."))
          .finally(() => setAuditLoading(false));
        return;
      }
      const payload = branch === "normal" ? contextPayloadForScenario("normal") : contextPayloadForScenario("caution");
      const response = await apiRequest<Detail>(`/api/v1/demo/sessions/${active.sessionId}/alerts/${alertId}/context`, {
        method: "POST", body: JSON.stringify(payload), capability: active.capability,
        demoRunId: active.demoRunId, idempotencyKey: crypto.randomUUID(),
      });
      setResult({ message: response.body.message, currentState: String(response.body.data?.currentState ?? "") });
      setAuditLoading(true);
      loadAlertAudit(active, alertId)
        .then((history) => setAudit(history.items))
        .catch((reason) => setError(reason instanceof Error ? reason.message : "감사이력을 갱신하지 못했습니다."))
        .finally(() => setAuditLoading(false));
    } catch (reason) {
      setError((reason as Partial<ApiResponse<unknown>>).message ?? "응답을 처리하지 못했습니다.");
    } finally { setSubmitting(false); }
  }

  if (loading) return <section className="panel"><div className="list-skeleton">변화 내용을 불러오는 중입니다.</div></section>;
  if (error && !detail) return <section className="panel empty-state"><p>{error}</p><Link className="primary-button" href="/demo">서비스 체험 시작하기</Link></section>;

  const rehearsal = findRehearsalScenario(context?.rehearsalScenario);
  const expectsNormal = context?.rehearsalScenario === "normal";
  const evidenceSignals = readEvidenceSignals(detail?.t0AlertEvidence);
  const reasonCodes = readStringList(detail?.reasonCodes);
  const primaryReason = reasonCodes[0] ?? evidenceSignals[0]?.reasonCode;
  return <>
    {rehearsal && <section className="rehearsal-cue" role="note"><strong>{rehearsal.label} 리허설</strong><span>이번 선택: {rehearsal.customerAction}</span><small>목표 상태 {rehearsal.expectedState}</small></section>}
    {result ? <section className="panel result-panel"><p className="label">고객 응답 처리 완료</p><h2>{result.message}</h2><p className="state-confirmation">확인된 상태 <strong>{result.currentState}</strong></p>{result.currentState === "DEFERRED" && result.deferredUntil && <p>다시 확인할 시각 <strong>{dateTime(result.deferredUntil)}</strong></p>}{result.currentState === "PENDING_BANK_REVIEW" ? <Link className="primary-button" href="/staff/cases">행원 사건 화면에서 계속하기</Link> : <Link className="primary-button" href="/demo">내 금융생활로 돌아가기</Link>}</section> : <>
      <p className="step-indicator">3단계 중 2단계 · 내용 확인</p>
      <section className="panel detail-panel">
        <p className="label">확인이 필요한 금융생활 변화</p>
        <h2>{primaryReason ? reasonTitle(primaryReason) : "평소와 다른 금융활동이 발견되었습니다."}</h2>
        <p className="muted">{evidenceSignals.length ? "평소 이용 모습과 최근 기록을 비교한 결과입니다. 아래 수치를 천천히 확인해 주세요." : "아래 내용을 천천히 읽고, 본인에게 해당하는 버튼을 선택해 주세요."}</p>
        {evidenceSignals.length > 0 && <div className="customer-evidence-list" aria-label="알림이 발생한 이유">
          {evidenceSignals.map((signal) => <article key={signal.reasonCode}>
            <strong>{reasonLabel(signal.reasonCode)}</strong>
            <p>{comparisonText(signal)}</p>
            <small>{unitLabel(signal.unit)} 기준 · {String(detail?.algorithmVersion ?? "검증된 규칙")}</small>
          </article>)}
        </div>}
        <p className="evidence-boundary" role="note">이 내용은 질병 진단이나 사기 판정이 아닙니다. 거래를 자동으로 실행하거나 계좌를 막지 않습니다.</p>
      </section>
      <section className="panel context-panel"><h2>아래에서 해당하는 내용을 선택해 주세요.</h2><p className="muted">확신이 없으면 지금 결정하지 않고 나중에 다시 확인할 수 있습니다.</p><div className="context-actions"><button className={`known-action ${expectsNormal ? "rehearsal-recommended" : ""}`} disabled={submitting} onClick={() => void answer("normal")}>제가 알고 있는 금융활동입니다</button><button className={`review-action ${rehearsal && !expectsNormal ? "rehearsal-recommended" : ""}`} disabled={submitting} onClick={() => void answer("review")}>잘 모르겠습니다. 도움받겠습니다</button><button className="later-action" disabled={submitting} onClick={() => void answer("later")}>지금은 잘 모르겠어요. 나중에 확인할게요</button></div></section>
    </>}
    {error && <p className="api-error" role="alert">{error}</p>}
    <section className="panel alert-audit-section">
      <div className="section-heading"><div><p className="label">검증 가능한 처리 기록</p><h2>알림 감사이력</h2></div><span className="status-chip">{auditLoading ? "갱신 중" : `${audit.length}건`}</span></div>
      {audit.length ? <ol>{audit.map((item) => <li key={item.auditId}><span className="audit-marker" /><div><strong>{auditLabel(item.eventType)}</strong><p>{stateText(item.fromState)} → {stateText(item.toState)}</p><small>{dateTime(item.occurredAt)} · {item.actorType} · {item.policyVersion}</small></div></li>)}</ol> : <div className="empty-block">아직 기록된 감사 이벤트가 없습니다.</div>}
      <p className="audit-safety-note">원문 개인정보 없이 상태 변화·정책 버전·근거 식별자만 표시합니다.</p>
    </section>
  </>;
}

function auditLabel(value: string) { return ({ ALERT_CREATED: "변화 알림 생성", CUSTOMER_CONTEXT_APPLIED: "고객 맥락 반영", CUSTOMER_CONFIRMATION_DEFERRED: "고객이 나중 확인 선택", ALERT_ESCALATED: "행원 검토 연결", CASE_REVIEW_STARTED: "행원 검토 시작" } as Record<string, string>)[value] ?? value.replaceAll("_", " "); }
function stateText(value: string | null) { return value ? value.replaceAll("_", " ") : "기록 시작"; }
function dateTime(value: string) { const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(parsed); }

function readStringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function readEvidenceSignals(value: unknown): EvidenceSignal[] {
  if (!value || typeof value !== "object") return [];
  const signals = (value as { signals?: unknown }).signals;
  if (!Array.isArray(signals)) return [];
  return signals.flatMap((item) => {
    if (!item || typeof item !== "object") return [];
    const signal = item as Record<string, unknown>;
    const baselineValue = Number(signal.baselineValue);
    const currentValue = Number(signal.currentValue);
    if (typeof signal.reasonCode !== "string" || !Number.isFinite(baselineValue) || !Number.isFinite(currentValue)) return [];
    return [{ reasonCode: signal.reasonCode, baselineValue, currentValue, unit: typeof signal.unit === "string" ? signal.unit : "COUNT" }];
  });
}

function reasonLabel(code: string) {
  return ({
    MISSED_RECURRING: "정기납부 누락",
    DUPLICATE_TRANSFER: "짧은 시간 안의 중복송금",
    REPEATED_CONFIRMATION: "거래결과 반복 확인",
  } as Record<string, string>)[code] ?? "평소와 다른 금융생활 기록";
}

function reasonTitle(code: string) {
  return `${reasonLabel(code)} 기록을 확인해 주세요.`;
}

function unitLabel(unit: string) {
  return ({ COUNT_60D: "최근 60일", COUNT_10M: "10분 이내", COUNT_1H: "완료 후 1시간", COUNT: "해당 기간" } as Record<string, string>)[unit] ?? "해당 기간";
}

function comparisonText(signal: EvidenceSignal) {
  const label = reasonLabel(signal.reasonCode);
  return `${label}: 평소 ${formatCount(signal.baselineValue)}, 최근 ${formatCount(signal.currentValue)}로 확인됐습니다.`;
}

function formatCount(value: number) {
  return `${Number.isInteger(value) ? value : value.toFixed(1)}회`;
}
