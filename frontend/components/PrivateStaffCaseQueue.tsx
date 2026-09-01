"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import {
  addOperationalCaseNote, loadOperationalCaseBundle, loadOperationalCaseQueue,
  startOperationalCaseReview, type OperationalCaseBundle, type OperationalCaseSummary,
} from "../lib/private-staff-cases";

export function PrivateStaffCaseQueue({ compact = false }: { compact?: boolean }) {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [items, setItems] = useState<OperationalCaseSummary[]>([]);
  const [selected, setSelected] = useState<OperationalCaseSummary | null>(null);
  const [bundle, setBundle] = useState<OperationalCaseBundle | null>(null);
  const [note, setNote] = useState("고객 응답과 합성 근거를 함께 확인했습니다.");
  const [busy, setBusy] = useState("loading");
  const [error, setError] = useState("");
  const [result, setResult] = useState("");

  const refresh = useCallback(async (active: PrivateCustomerSession) => {
    const queue = await loadOperationalCaseQueue(active);
    setItems(queue); setSelected((current) => queue.find((item) => item.caseId === current?.caseId) ?? null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    void restorePrivateCustomerSession().then(async (active) => {
      if (!active.roles.includes("PROTECTION_STAFF")) throw new Error("보호업무 행원 권한이 필요합니다.");
      if (cancelled) return; setSession(active); await refresh(active);
    }).catch((reason) => { if (!cancelled) setError(message(reason)); })
      .finally(() => { if (!cancelled) setBusy(""); });
    return () => { cancelled = true; };
  }, [refresh]);

  const metrics = useMemo(() => ({
    high: items.filter((item) => item.reviewPriority === "HIGH").length,
    pending: items.filter((item) => item.taskStatus === "PENDING").length,
    reviewing: items.filter((item) => item.taskStatus === "IN_REVIEW").length,
  }), [items]);

  async function openCase(item: OperationalCaseSummary) {
    if (!session) return; setBusy("detail"); setError(""); setResult("");
    try { setSelected(item); setBundle(await loadOperationalCaseBundle(session, item.caseId)); }
    catch (reason) { setError(message(reason)); }
    finally { setBusy(""); }
  }

  async function startReview() {
    if (!session || !selected) return; setBusy("review"); setError(""); setResult("");
    try { await startOperationalCaseReview(session, selected); await refresh(session); setResult("검토를 시작했습니다."); }
    catch (reason) { setError(message(reason)); }
    finally { setBusy(""); }
  }

  async function saveNote() {
    if (!session || !selected || !note.trim()) return; setBusy("note"); setError(""); setResult("");
    try { await addOperationalCaseNote(session, selected.caseId, note.trim()); setBundle(await loadOperationalCaseBundle(session, selected.caseId)); setResult("내부 메모를 저장했습니다. 외부로 전송하지 않았습니다."); }
    catch (reason) { setError(message(reason)); }
    finally { setBusy(""); }
  }

  if (busy === "loading") return <section className="panel"><div className="list-skeleton">로그인 행원의 담당 사건을 확인하고 있습니다.</div></section>;
  if (!session || error && !items.length) return <section className="panel empty-state"><h2>담당 사건을 열 수 없습니다.</h2><p>{error}</p></section>;

  return <section className={`private-staff-case-queue ${compact ? "compact" : ""}`}>
    <div className="case-summary-grid" aria-label="로그인 행원 사건 요약">
      <article className="panel"><span>현재 담당</span><strong>{items.length}</strong><small>목적별 접근권 범위</small></article>
      <article className="panel"><span>우선 검토</span><strong>{metrics.high}</strong><small>위험 확률이 아닌 업무 순서</small></article>
      <article className="panel"><span>검토 대기</span><strong>{metrics.pending}</strong><small>고객 확인 후 승격</small></article>
      <article className="panel"><span>처리 중</span><strong>{metrics.reviewing}</strong><small>사람 검토 진행</small></article>
    </div>
    <section className="panel">
      <div className="section-heading"><div><p className="label">Bearer 보호업무 큐</p><h2>로그인 행원의 담당 사건</h2></div><button className="secondary-button" onClick={() => void refresh(session)}>새로고침</button></div>
      {!items.length ? <div className="empty-block"><strong>현재 담당 사건이 없습니다.</strong><p>배정된 고객이 안심관리에서 “확인하기 어렵습니다” 또는 “잘 모르겠어요”를 선택하면 이곳에 생성됩니다.</p></div> :
        <div className="staff-case-list">{items.map((item) => <article key={item.caseId} className="staff-case-row"><div><span>{priorityLabel(item.reviewPriority)}</span><strong>{reasonLabel(item)}</strong><small>{maskCustomer(item.customerId)} · {statusLabel(item.taskStatus)}</small></div><button className="secondary-button" onClick={() => void openCase(item)} disabled={Boolean(busy)}>근거·기록 확인</button></article>)}</div>}
    </section>
    {selected && bundle && <section className="panel operational-case-detail">
      <div className="section-heading"><div><p className="label">사건 {selected.caseId.slice(0, 8)}</p><h2>{statusLabel(selected.taskStatus)} · {reasonLabel(selected)}</h2></div>{selected.taskStatus === "PENDING" && <button className="primary-button" onClick={() => void startReview()} disabled={Boolean(busy)}>{busy === "review" ? "처리 중…" : "검토 시작"}</button>}</div>
      <dl className="system-detail-grid"><div><dt>고객 응답</dt><dd>{text(bundle.detail, "customerResponseCode", "확인 요청")}</dd></div><div><dt>근거 수</dt><dd>{count(bundle.evidence)}건</dd></div><div><dt>타임라인</dt><dd>{bundle.timeline.length}건</dd></div><div><dt>후속관리</dt><dd>{bundle.followUps.length}건</dd></div></dl>
      <label className="form-field"><span>행원 내부 메모</span><textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} /></label>
      <button className="secondary-button" onClick={() => void saveNote()} disabled={Boolean(busy) || !note.trim()}>{busy === "note" ? "저장 중…" : "내부 메모 저장"}</button>
      {bundle.notes.length > 0 && <p className="muted">저장된 내부 메모 {bundle.notes.length}건 · 고객·외부기관에는 전송되지 않습니다.</p>}
    </section>}
    {error && <p className="form-error" role="alert">{error}</p>}{result && <p className="form-success" role="status">{result}</p>}
  </section>;
}

function message(reason: unknown) { return reason instanceof Error ? reason.message : "사건 정보를 불러오지 못했습니다."; }
function maskCustomer(value: string) { return value.length > 10 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value; }
function priorityLabel(value: string) { return value === "HIGH" ? "우선 검토" : value === "MEDIUM" ? "일반 검토" : "낮은 순서"; }
function statusLabel(value: string) { return ({ PENDING: "검토 대기", IN_REVIEW: "검토 중", GUIDANCE_APPROVED: "안내계획 승인", COMPLETED: "처리 완료" } as Record<string, string>)[value] ?? value; }
function reasonLabel(item: OperationalCaseSummary) { return item.reviewPriority === "HIGH" ? "고객이 확인을 요청한 금융생활 변화" : "고객 맥락 추가 확인"; }
function text(record: Record<string, unknown>, key: string, fallback: string) { const value = record[key]; return typeof value === "string" && value ? value : fallback; }
function count(record: Record<string, unknown>) { const value = record.count; return typeof value === "number" ? value : Array.isArray(record.items) ? record.items.length : 0; }
