"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { restorePrivateCustomerSession } from "../lib/private-financial-products";
import { loadOperationalCasePage, type OperationalCaseSummary } from "../lib/private-staff-cases";
import { customerLabel } from "../lib/presentation-copy";
import { caseStateLabel, dateTime } from "../lib/continuity-labels";

export function StaffWorkOverview() {
  const [items, setItems] = useState<OperationalCaseSummary[]>([]);
  const [more, setMore] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [checkedAt, setCheckedAt] = useState("");
  const [selectedState, setSelectedState] = useState("ALL");
  const refresh = useCallback(async () => {
    setBusy(true); setError("");
    try {
      const session = await restorePrivateCustomerSession();
      const page = await loadOperationalCasePage(session);
      setItems(page.items); setMore(Boolean(page.nextCursor)); setCheckedAt(new Date().toISOString());
    } catch (reason) { setError(reason instanceof Error ? reason.message : "업무 현황을 불러오지 못했습니다. 다시 확인해 주세요."); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const visibleItems = selectedState === "ALL" ? items : items.filter((item) => item.taskStatus === selectedState);
  const listTitle = selectedState === "ALL" ? "전체 사건" : `${caseStateLabel(selectedState)} 사건`;
  return <div className="staff-work-overview">
    <header className="portal-section-heading"><div><h2>현재 내 업무</h2><p>{checkedAt ? `${dateTime(checkedAt)} 확인 · 불러온 ${items.length}건 기준` : "담당 고객의 사건을 확인합니다."}</p></div><button className="btn btn-outline" disabled={busy} onClick={() => void refresh()}>{busy ? "확인 중…" : "새로고침"}</button></header>
    {error && <p className="api-error" role="alert">{error}</p>}
    {busy && !checkedAt ? <p role="status">업무 현황을 불러오고 있습니다.</p> : checkedAt && <>
      <div className="staff-work-counts" role="group" aria-label="상태별 사건 보기">{["PENDING", "IN_REVIEW", "GUIDANCE_APPROVED", "COMPLETED"].map((state) => <button type="button" key={state} aria-pressed={selectedState === state} aria-controls="staff-work-results" onClick={() => setSelectedState(state)}><span>{caseStateLabel(state)}</span><strong>{items.filter((item) => item.taskStatus === state).length}<small>건</small></strong><span className="staff-work-count-action">{selectedState === state ? "선택됨" : "목록 보기"}</span></button>)}</div>
      <section className="panel" id="staff-work-results" aria-labelledby="staff-work-results-title" aria-busy={busy}><header className="portal-section-heading"><h2 id="staff-work-results-title" role="status">{listTitle} · {visibleItems.length}건</h2><button type="button" className="btn btn-outline" aria-pressed={selectedState === "ALL"} aria-controls="staff-work-results" onClick={() => setSelectedState("ALL")}>전체 보기</button></header>
        {visibleItems.length ? <ul className="staff-work-list">{visibleItems.map((item) => <li key={item.caseId}><div><strong>{customerLabel(item.customerId)}</strong><span>{caseStateLabel(item.taskStatus)} · 접수 {dateTime(item.createdAt)}</span></div><Link className="btn btn-outline" aria-label={`${customerLabel(item.customerId)} · ${dateTime(item.createdAt)} 접수 사건 열기`} href={`/staff/cases?caseId=${encodeURIComponent(item.caseId)}`}>사건 열기</Link></li>)}</ul> : <p>{selectedState === "ALL" ? "불러온 사건이 없습니다." : `불러온 사건 중 ${caseStateLabel(selectedState)} 상태인 사건이 없습니다.`}</p>}
        {more && <p>불러온 100건의 현황입니다. <Link href="/staff/cases">사건 검토에서 나머지 사건 확인하기</Link></p>}
      </section>
    </>}
  </div>;
}
