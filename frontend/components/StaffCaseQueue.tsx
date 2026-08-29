"use client";

import { useEffect, useState } from "react";
import { apiRequest, ApiClientError } from "../lib/api";
import { readDemoContext } from "../lib/demo-session";

type CaseItem = { caseId: string; customerId: string; state: string; reviewPriority: string; summary: string };
type Queue = { items: CaseItem[]; nextCursor: string | null; hasMore: boolean };

export function StaffCaseQueue() {
  const [items, setItems] = useState<CaseItem[]>([]);
  const [message, setMessage] = useState("사건 큐를 불러오는 중입니다.");
  useEffect(() => {
    const context = readDemoContext();
    if (!context?.sessionId || !context.demoRunId) { setMessage("먼저 고객 데모에서 검토 필요 시나리오를 실행해 주세요."); return; }
    let cancelled = false;
    void (async () => {
      try {
        const issued = await fetch(`/api/internal/staff-capability/${context.sessionId}`, { method: "POST" });
        if (!issued.ok) throw new ApiClientError("http", "직원 접근 권한을 확인할 수 없습니다.", issued.status);
        const staffCapability = issued.headers.get("X-Demo-Staff-Capability");
        if (!staffCapability) throw new ApiClientError("parse", "직원 capability 응답이 올바르지 않습니다.");
        const response = await apiRequest<Queue>(`/api/v1/demo/sessions/${context.sessionId}/staff/cases?limit=20`, {
          staffCapability, demoRunId: context.demoRunId,
        });
        if (cancelled) return;
        const queue = response.body.data;
        setItems(queue?.items ?? []);
        setMessage(queue?.items.length ? "" : "현재 검토할 사건이 없습니다.");
      } catch (error) { if (!cancelled) setMessage(error instanceof Error ? error.message : "사건 큐를 불러오지 못했습니다."); }
    })();
    return () => { cancelled = true; };
  }, []);
  return <section className="panel" aria-live="polite"><div className="table-head"><span>고객/사건</span><span>변화 신호</span><span>상태</span><span>우선순위</span></div>
    {message ? <div className="list-skeleton">{message}</div> : items.map((item) => <article className="table-row" key={item.caseId}>
      <span><strong>{item.customerId}</strong><small>{item.caseId}</small></span><span>{item.summary}</span><span>{item.state}</span><span>{item.reviewPriority}</span>
    </article>)}</section>;
}
