"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { restorePrivateCustomerSession, type PrivateCustomerSession } from "../lib/private-financial-products";
import { loadOperationalCaseBundle, loadOperationalCasePage, type OperationalCaseBundle, type OperationalCaseSummary } from "../lib/private-staff-cases";
import { caseStateLabel, dateTime } from "../lib/continuity-labels";
import { caseIdFromSearch } from "../lib/prototype-navigation";
import { customerLabel } from "../lib/presentation-copy";
import { ReviewLoginContext } from "./LoginNavigationContext";
import { OperationalCaseReview } from "./OperationalCaseReview";

export function PrivateStaffCaseQueue({ compact = false }: { compact?: boolean }) {
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [items, setItems] = useState<OperationalCaseSummary[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [bundle, setBundle] = useState<OperationalCaseBundle | null>(null);
  const [viewingCase, setViewingCase] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [result, setResult] = useState("");
  const [updatedAt, setUpdatedAt] = useState("");
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [filter, setFilter] = useState("ALL");
  const [needsReload, setNeedsReload] = useState(false);
  const mounted = useRef(false);
  const pending = useRef(false);
  const generation = useRef(0);

  const refresh = useCallback(async (active: PrivateCustomerSession, nextCursor?: string) => {
    const page = await loadOperationalCasePage(active, nextCursor);
    if (!mounted.current) return;
    setItems((current) => nextCursor ? [...current, ...page.items.filter((item) => !current.some((previous) => previous.caseId === item.caseId))] : page.items);
    setCursor(page.nextCursor); setUpdatedAt(new Date().toISOString());
  }, []);

  const openCase = useCallback(async (active: PrivateCustomerSession, caseId: string) => {
    const current = ++generation.current;
    pending.current = true; setBusy(true); setError(""); setResult("");
    setBundle((currentBundle) => currentBundle?.detail.caseSummary.caseId === caseId ? currentBundle : null);
    try {
      const loaded = await loadOperationalCaseBundle(active, caseId);
      if (!mounted.current || current !== generation.current) return;
      setBundle(loaded); setNeedsReload(false); setViewingCase(true);
      window.history.replaceState(null, "", `/staff/cases?caseId=${encodeURIComponent(caseId)}`);
    } catch (reason) { if (mounted.current && current === generation.current) { setNeedsReload(true); setError(`사건을 열지 못했습니다. 해당 고객의 담당 행원인지 확인해 주세요. ${message(reason)}`); } }
    finally { if (mounted.current && current === generation.current) { pending.current = false; setBusy(false); } }
  }, []);

  useEffect(() => {
    mounted.current = true;
    let active = true;
    void restorePrivateCustomerSession().then(async (restored) => {
      if (!restored.roles.includes("PROTECTION_STAFF")) throw new Error("보호업무 행원 권한이 필요합니다.");
      if (!active) return;
      setSession(restored); await refresh(restored);
      const caseId = caseIdFromSearch(window.location.search);
      if (active && caseId) await openCase(restored, caseId);
    }).catch((reason) => { if (active) setError(message(reason)); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; mounted.current = false; generation.current += 1; };
  }, [openCase, refresh]);

  useEffect(() => {
    if (!session || !autoRefresh) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState !== "visible" || pending.current) return;
      pending.current = true;
      void refresh(session).catch((reason) => { if (mounted.current) setError(`자동 새로고침 실패: ${message(reason)}`); }).finally(() => { pending.current = false; });
    }, 30000);
    return () => window.clearInterval(timer);
  }, [session, autoRefresh, refresh]);

  const selectedCaseId = bundle?.detail.caseSummary.caseId;
  useEffect(() => {
    if (!selectedCaseId || !viewingCase) return;
    document.getElementById("case-review-title")?.focus({ preventScroll: true });
    document.getElementById("staff-case-detail")?.scrollIntoView({ block: "start", behavior: "instant" });
  }, [selectedCaseId, viewingCase]);

  async function reloadList(nextCursor?: string) {
    if (!session || pending.current) return;
    pending.current = true; setBusy(true); setError("");
    try { await refresh(session, nextCursor); }
    catch (reason) { if (mounted.current) setError(message(reason)); }
    finally { pending.current = false; if (mounted.current) setBusy(false); }
  }

  async function runCommand(command: () => Promise<unknown>, success: string): Promise<boolean> {
    if (!session || !bundle || pending.current || needsReload) return false;
    pending.current = true; setBusy(true); setError(""); setResult("");
    const caseId = bundle.detail.caseSummary.caseId;
    let saved = false;
    try {
      await command(); saved = true;
      const latest = await loadOperationalCaseBundle(session, caseId);
      if (!mounted.current) return false;
      setBundle(latest); setResult(success); await refresh(session); return true;
    } catch (reason) {
      if (mounted.current) {
        setError(`${saved ? "요청은 저장됐지만 최신 상태를 불러오지 못했습니다. 다시 제출하지 말고 사건을 다시 열어 주세요. " : "입력은 유지했습니다. 사건을 다시 열어 최신 상태를 확인한 뒤 이어가세요. "}${message(reason)}`);
        setNeedsReload(true);
      }
      return false;
    } finally { pending.current = false; if (mounted.current) setBusy(false); }
  }

  const visible = items.filter((item) => filter === "ALL" || item.taskStatus === filter);
  return <div className={`private-staff-case-queue continuity-staff ${compact ? "compact" : ""}`}>
    <ReviewLoginContext caseId={bundle?.detail.caseSummary.caseId} customerId={bundle?.detail.caseSummary.customerId} alertId={bundle?.detail.caseSummary.alertId} />
    <section className="panel" id="staff-case-list" tabIndex={-1} hidden={viewingCase && Boolean(bundle)}><header className="continuity-heading"><div><h2>고객 응답이 도착한 사건</h2><p>고객이 확인을 요청한 내용을 검토해 주세요.</p></div><button className="btn btn-outline" disabled={busy} onClick={() => void reloadList()}>목록 새로고침</button></header>
      <div className="continuity-queue-controls"><label className="continuity-field"><span>처리 상태</span><select className="select" value={filter} onChange={(event) => setFilter(event.target.value)}>{["ALL", "PENDING", "IN_REVIEW", "GUIDANCE_APPROVED", "COMPLETED"].map((value) => <option key={value} value={value}>{value === "ALL" ? "전체 상태" : caseStateLabel(value)}</option>)}</select></label><label className="continuity-checkbox"><input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} />30초마다 목록 확인</label><span>마지막 확인: {updatedAt ? dateTime(updatedAt) : "확인 중"}</span></div>
      <p>불러온 {items.length}건 중 {visible.length}건 표시</p>
      {busy && !updatedAt ? <p role="status">로그인 행원의 사건을 불러옵니다.</p> : !visible.length ? <p>표시할 사건이 없습니다. 고객 응답 여부, 선택한 상태, 담당 고객 접근 권한을 확인해 주세요.</p> : <div className="continuity-case-list">{visible.map((item) => <article key={item.caseId} className={bundle?.detail.caseSummary.caseId === item.caseId ? "selected" : ""}><div><strong>{customerLabel(item.customerId)}</strong><span>{caseStateLabel(item.taskStatus)} · {item.reviewPriority === "HIGH" ? "우선 검토" : "일반 검토"}</span><small>접수 {dateTime(item.createdAt)}</small></div><button className="btn btn-outline" disabled={busy} onClick={() => session && void openCase(session, item.caseId)} aria-label={`${item.customerId} 고객의 사건 확인`}>응답·근거 확인</button></article>)}</div>}
      {cursor && <button className="btn btn-outline" disabled={busy} onClick={() => void reloadList(cursor)}>다음 사건 더 보기</button>}
    </section>
    {error && <p className="api-error" role="alert">{error}</p>}{result && <p className="workflow-result" role="status">{result}</p>}
    {session && bundle && <div className="case-detail-view" hidden={!viewingCase}><div className="case-detail-toolbar"><button className="btn btn-outline" disabled={busy} onClick={() => { setViewingCase(false); window.history.replaceState(null, "", "/staff/cases"); requestAnimationFrame(() => { document.getElementById("staff-case-list")?.focus(); }); }}>사건 목록으로</button><button className="btn btn-outline" disabled={busy} onClick={() => void openCase(session, bundle.detail.caseSummary.caseId)}>사건 새로고침</button></div><OperationalCaseReview key={bundle.detail.caseSummary.caseId} session={session} bundle={bundle} busy={busy || needsReload} runCommand={runCommand} /></div>}
  </div>;
}
function message(reason: unknown) { return reason instanceof Error ? reason.message : "사건 정보를 불러오지 못했습니다."; }
