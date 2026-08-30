"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiRequest } from "../lib/api";
import { readDemoContext } from "../lib/demo-session";
import { issueStaffCapability } from "../lib/staff-case-workflow";

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
        const staffCapability = await issueStaffCapability(context.sessionId);
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
    {message ? <div className="list-skeleton">{message}</div> : items.map((item) => <Link className="table-row table-row-link" href={`/staff/cases/${encodeURIComponent(item.caseId)}`} aria-label={`${item.customerId} 사건 상세 보기`} key={item.caseId}>
      <span><strong>{item.customerId}</strong><small>{item.caseId}</small></span><span>{item.summary}</span><span>{item.state}</span><span>{item.reviewPriority}</span>
    </Link>)}</section>;
}
